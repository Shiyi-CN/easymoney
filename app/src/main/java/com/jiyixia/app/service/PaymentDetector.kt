package com.jiyixia.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.jiyixia.app.JiYiXiaApp
import com.jiyixia.app.data.entity.Record
import com.jiyixia.app.ui.MainActivity
import com.jiyixia.app.util.RuleManager
import com.jiyixia.app.util.UserLearningManager
import com.jiyixia.app.util.toCents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 支付检测统一入口（重构版）
 *
 * 新架构流程：
 *   原始数据 → 结构化解析 → 场景识别 → 智能去重 → 分类推断 → 写库+通知
 *
 * 三个检测层共享此入口：
 * - NotificationListener → processNotification()
 * - AccessibilityService → processScreen()
 * - SmsReceiver → processSms()
 *
 * 解决的问题：
 * 1. 同一条多次记录 → DedupManager 跨来源去重 + 5分钟时间窗口
 * 2. 乱识别 → NotificationParser/ScreenParser 结构化解析 + 营销过滤
 * 3. 识别不准确 → SceneDetector 场景识别 + 商户名提取
 */
object PaymentDetector {

    private const val TAG = "PaymentDetector"
    private const val CHANNEL_ID = "payment_monitor"

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 最近识别日志（用于调试展示）
    private val recentLogs = mutableListOf<String>()
    val detectionLogs: List<String> get() = synchronized(recentLogs) { recentLogs.toList() }

    // ═══════════════════════════════════════════════════════════
    //  三个检测层的入口
    // ═══════════════════════════════════════════════════════════

    /**
     * 处理通知（Layer 1: NotificationListener 调用）
     */
    fun processNotification(
        notification: Notification,
        packageName: String,
        context: Context,
        detectedTime: Long = System.currentTimeMillis()
    ) {
        if (!isPaymentApp(packageName)) return

        // 1. 结构化解析通知
        val content = NotificationParser.parse(notification, packageName)
        val parsed = NotificationParser.extractTransaction(content) ?: run {
            Log.d(TAG, "通知解析失败或被过滤: pkg=$packageName, title=${content.title}")
            return
        }

        Log.d(TAG, "通知解析成功: pkg=$packageName, amount=${parsed.amount}, " +
                "merchant=${parsed.merchantName}, scene=${parsed.scene}")

        // 2. 委托给统一处理
        processParsedTransaction(
            parsed = parsed,
            sourceType = "notification",
            appSignature = packageName,
            context = context,
            detectedTime = detectedTime
        )
    }

    /**
     * 处理屏幕检测（Layer 2: AccessibilityService 调用）
     */
    fun processScreen(
        rootNode: AccessibilityNodeInfo,
        packageName: String,
        context: Context,
        detectedTime: Long = System.currentTimeMillis()
    ) {
        if (!isPaymentApp(packageName)) return

        // 1. 结构化解析屏幕
        val content = ScreenParser.parse(rootNode, packageName)
        val parsed = ScreenParser.extractTransaction(content) ?: run {
            Log.d(TAG, "屏幕解析失败或被过滤: pkg=$packageName")
            return
        }

        Log.d(TAG, "屏幕解析成功: pkg=$packageName, amount=${parsed.amount}, " +
                "merchant=${parsed.merchantName}, scene=${parsed.scene}")

        // 2. 委托给统一处理
        processParsedTransaction(
            parsed = parsed,
            sourceType = "screen",
            appSignature = packageName,
            context = context,
            detectedTime = detectedTime
        )
    }

    /**
     * 处理短信（Layer 3: SmsReceiver 调用）
     */
    fun processSms(
        sender: String,
        body: String,
        context: Context,
        detectedTime: Long = System.currentTimeMillis()
    ) {
        // 1. 结构化解析短信
        val content = NotificationParser.parseSms(sender, body)
        val parsed = NotificationParser.extractTransaction(content) ?: run {
            Log.d(TAG, "短信解析失败或被过滤: sender=$sender")
            return
        }

        Log.d(TAG, "短信解析成功: sender=$sender, amount=${parsed.amount}, " +
                "merchant=${parsed.merchantName}, scene=${parsed.scene}")

        // 2. 委托给统一处理
        processParsedTransaction(
            parsed = parsed,
            sourceType = "sms",
            appSignature = "sms:$sender",
            context = context,
            detectedTime = detectedTime
        )
    }

    // ═══════════════════════════════════════════════════════════
    //  统一处理逻辑
    // ═══════════════════════════════════════════════════════════

    /**
     * 统一处理解析后的交易
     *
     * 流程：
     * 1. 智能去重（跨来源）
     * 2. 场景识别（判断收支类型和分类）
     * 3. 用户学习修正
     * 4. 写入数据库
     * 5. 显示通知
     */
    private fun processParsedTransaction(
        parsed: NotificationParser.ParsedNotification,
        sourceType: String,
        appSignature: String,
        context: Context,
        detectedTime: Long
    ) {
        val amount = parsed.amount
        if (amount <= 0) return

        // 1. 智能去重
        // 1.1 同来源同金额 5 分钟内去重
        if (DedupManager.isDuplicate(amount, sourceType, appSignature)) {
            return
        }

        // 1.2 跨来源去重：如果其他来源已经记录过此金额，跳过
        // （例如：通知已经记录了 ¥38，屏幕检测到 ¥38 应该跳过）
        if (DedupManager.isDuplicateAcrossSources(amount, excludeSourceType = sourceType)) {
            Log.d(TAG, "跨来源去重跳过: amount=$amount, source=$sourceType")
            return
        }

        Log.d(TAG, "通过去重: amount=$amount, source=$sourceType, app=$appSignature")

        val app = context.applicationContext as JiYiXiaApp
        coroutineScope.launch {
            try {
                val db = app.database
                val categories = db.categoryDao().getAll().first()
                val nameToId = categories.associate { it.name to it.id }

                // 2. 场景识别
                val sceneResult = SceneDetector.detect(parsed)
                Log.d(TAG, "场景识别: type=${sceneResult.sceneType}, " +
                        "category=${sceneResult.categoryName}, " +
                        "isExpense=${sceneResult.isExpense}, " +
                        "confidence=${sceneResult.confidence}, " +
                        "reason=${sceneResult.reason}")

                // 营销场景直接拒绝
                if (sceneResult.sceneType == SceneDetector.SceneType.MARKETING) {
                    Log.d(TAG, "营销场景拒绝记录")
                    return@launch
                }

                var categoryName = sceneResult.categoryName
                var type = if (sceneResult.isExpense) 0 else 1
                var confidence = sceneResult.confidence

                // 3. 用户学习修正（最高优先级）
                val merchantName = parsed.merchantName
                if (merchantName.isNotBlank()) {
                    val learnedCategory = UserLearningManager.getLearnedCategory(context, merchantName)
                    if (learnedCategory != null && learnedCategory != categoryName) {
                        Log.d(TAG, "用户学习修正: $categoryName → $learnedCategory (商户: $merchantName)")
                        categoryName = learnedCategory
                        confidence = 95
                    }
                }

                // 4. 查找分类
                val category = categories.find { it.name == categoryName && it.type == type }
                    ?: categories.find { it.name == categoryName }
                    ?: categories.find { it.name == "其他" && it.type == type }
                    ?: categories.firstOrNull()

                if (category == null) {
                    Log.e(TAG, "无可用分类，跳过记录")
                    return@launch
                }

                // 5. 构建备注
                val typeLabel = if (type == 1) "收入" else "支出"
                val merchantLabel = if (merchantName.isNotBlank()) "·$merchantName" else ""
                val sceneLabel = parsed.scene

                val isPending = confidence < 80
                val notePrefix = when {
                    confidence >= 80 -> "$typeLabel·$categoryName$merchantLabel"
                    confidence >= 60 -> "$typeLabel·待确认·$categoryName$merchantLabel"
                    else -> "$typeLabel·待分类·$sceneLabel"
                }

                // 6. 写入数据库
                db.recordDao().insert(
                    Record(
                        type = type,
                        amount = amount.toCents(),
                        categoryId = category.id,
                        note = notePrefix,
                        date = detectedTime,
                        isPendingConfirm = isPending,
                        confidence = confidence,
                        isReimbursable = false,
                        reimbursementTarget = ""
                    )
                )

                // 7. 显示通知
                showDetectNotification(context, amount, notePrefix, isPending)
                addLog("$sourceType $notePrefix ¥$amount ($confidence%)")

                Log.d(TAG, "记录成功: $notePrefix ¥$amount")

            } catch (e: Exception) {
                Log.e(TAG, "处理检测失败: source=$sourceType", e)
                addLog("错误: ${e.message}")
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  通知显示
    // ═══════════════════════════════════════════════════════════

    private fun showDetectNotification(
        context: Context,
        amount: Double,
        category: String,
        isPending: Boolean
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                Log.w(TAG, "通知权限未授予，跳过通知显示")
                return
            }
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel(nm)

        val text = if (isPending) {
            "识别到 ¥${String.format("%.2f", amount)} → $category（待确认）"
        } else {
            "识别到 ¥${String.format("%.2f", amount)} → $category"
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("记一下 - 自动记账")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        nm.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }

    private fun createChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "支付监听",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "监听支付通知并自动记账" }
            nm.createNotificationChannel(channel)
        }
    }

    private fun addLog(msg: String) {
        synchronized(recentLogs) {
            val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
            recentLogs.add(0, "$time $msg")
            if (recentLogs.size > 50) recentLogs.removeLast()
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  支付 app 识别（供各检测层共用）
    // ═══════════════════════════════════════════════════════════

    /**
     * 支付相关 app 包名集合
     */
    val PAYMENT_APP_PACKAGES = setOf(
        // 第三方支付
        "com.eg.android.AlipayGphone",      // 支付宝
        "com.tencent.mm",                     // 微信
        "com.tencent.mobileqq",               // QQ
        "com.unionpay",                       // 云闪付
        // 电商 / 外卖
        "com.taobao.taobao",                  // 淘宝
        "com.jingdong.app.mall",              // 京东
        "com.sankuai.meituan",                // 美团
        "com.sankuai.meituan.takeoutnew",     // 美团外卖
        "me.ele",                             // 饿了么
        "com.dianping.v1",                    // 大众点评
        "com.xunmeng.pinduoduo",              // 拼多多
        "com.ss.android.ugc.aweme",           // 抖音
        // 出行 / 打车
        "com.autonavi.minimap",               // 高德地图
        "com.baidu.BaiduMap",                 // 百度地图
        "com.didiglobal.passenger",           // 滴滴出行
        "com.didi.global",
        "com.sdu.didi.psnger",
        "com.yongche",
        "com.jingyao.driver",                 // 曹操出行
        "com.taxiservice",                    // T3出行
        "com.hellobike",
        "com.meituan.taxi",
        "com.xiaojukeji.hitch"
    )

    /** 银行 app 包名前缀 */
    val BANK_APP_PREFIXES = listOf(
        "com.icbc", "com.chinamworld", "com.android.bankabc",
        "com.boc.bocsoft", "com.bankcomm", "com.cmbchina",
        "com.spdbccc", "com.pingan", "com.cgbchina", "com.cmbc.mbank",
        "com.cib", "com.cebbank", "com.hxb", "com.bankofbeijing",
        "com.yitong.mbank.psbc", "com.psbc"
    )

    /** 判断是否为支付相关 app */
    fun isPaymentApp(packageName: String): Boolean {
        // 优先从 RuleManager 加载规则
        val rulePackages = RuleManager.getPaymentAppPackages()
        val rulePrefixes = RuleManager.getBankAppPrefixes()

        if (rulePackages.isNotEmpty()) {
            if (packageName in rulePackages) return true
            return rulePrefixes.any { packageName.startsWith(it) }
        }

        if (packageName in PAYMENT_APP_PACKAGES) return true
        return BANK_APP_PREFIXES.any { packageName.startsWith(it) }
    }
}
