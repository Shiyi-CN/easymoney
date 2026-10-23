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
import com.jiyixia.app.util.LearningQueue
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
 * - AccessibilityService → verifyScreen()（核对模式，不入库）
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

    /** 核对模式的时间窗口：屏幕支付金额与已入账记录匹配的回溯范围（±15 分钟） */
    private const val VERIFY_WINDOW_MS = 15 * 60 * 1000L

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
        detectedTime: Long = System.currentTimeMillis(),
        allowBackfill: Boolean = false
    ) {
        addLog("[诊断] 收到通知: pkg=$packageName")

        if (!isPaymentApp(packageName)) {
            addLog("[诊断] 非支付app，跳过: pkg=$packageName")
            return
        }

        // 1. 结构化解析通知
        val content = NotificationParser.parse(notification, packageName)
        addLog("[诊断] 解析通知: pkg=$packageName, title=${content.title}, text=${content.text.take(30)}")

        // 微信个人转账请求特殊处理：通知只显示"昵称 [转账] 请收款"，不含金额（金额只在收款页面），
        // 通知通道无法完成记账。记录待收款状态（30 分钟有效），用户点开收款页面时
        // 由屏幕核对通道补金额入库（详见 verifyScreen 的微信转账分支）。
        if (packageName == "com.tencent.mm" &&
            content.allText.contains("转账") && content.allText.contains("请收款")
        ) {
            val prefs = context.getSharedPreferences("auto_bookkeeping", Context.MODE_PRIVATE)
            prefs.edit()
                .putLong("wechat_transfer_pending_at", System.currentTimeMillis())
                .putString("wechat_transfer_sender", content.title)
                .apply()
            addLog("[诊断] 💸 微信转账请求（通知不含金额）：${content.title}，收款时打开微信页面自动记账")
            Log.d(TAG, "微信转账请求，等待收款页面补金额: sender=${content.title}")
            LearningQueue.archive(
                context = context,
                packageName = packageName,
                title = content.title,
                text = content.allText,
                reason = "转账通知不含金额，收款时打开微信页面自动记账"
            )
            return
        }

        val parsed = NotificationParser.extractTransaction(content) ?: run {
            addLog("[诊断] ❌ 解析失败/被过滤: pkg=$packageName, title=${content.title}, allText=${content.allText.take(60)}")
            Log.d(TAG, "通知解析失败或被过滤: pkg=$packageName, title=${content.title}")
            // 学习队列（支柱4）：疑似支付的通知原文存档，不再静默丢弃。
            // 只存档带支付特征的文本（含动作词/¥金额），避免聊天类通知刷爆队列。
            val paymentHint = listOf("支付", "付款", "收款", "转账", "红包", "退款", "扣款", "¥", "￥")
                .any { content.allText.contains(it) }
            if (paymentHint) {
                LearningQueue.archive(
                    context = context,
                    packageName = packageName,
                    title = content.title,
                    text = content.allText,
                    reason = "被过滤/未匹配模板"
                )
            }
            return
        }

        addLog("[诊断] ✅ 解析成功: pkg=$packageName, amount=${parsed.amount}, merchant=${parsed.merchantName}")
        Log.d(TAG, "通知解析成功: pkg=$packageName, amount=${parsed.amount}, " +
                "merchant=${parsed.merchantName}, scene=${parsed.scene}")

        // 2. 委托给统一处理
        processParsedTransaction(
            parsed = parsed,
            sourceType = "notification",
            appSignature = packageName,
            context = context,
            detectedTime = detectedTime,
            allowBackfill = allowBackfill
        )
    }

    /**
     * 屏幕核对（Layer 2: AccessibilityService 调用，核对模式）
     *
     * 双通道关系重定义（支柱4）：通知 = 发现 + 解析（主通道）；
     * 无障碍 = 只在用户主动打开支付 App 时核对（低频辅助），不再从屏幕直接入库。
     * 原因：屏幕文本杂乱是"乱记录"重灾区，且主动解析支付页面有风控风险。
     *
     * 核对逻辑：解析出屏幕支付金额后，查同金额 ±15 分钟内是否有已入账记录——
     * 有 → 日志确认"已核对"；没有 → 提示手动补记（通知可能未送达）。
     */
    fun verifyScreen(
        rootNode: AccessibilityNodeInfo,
        packageName: String,
        context: Context,
        detectedTime: Long = System.currentTimeMillis()
    ) {
        if (!isPaymentApp(packageName)) return

        // 1. 结构化解析屏幕
        val content = ScreenParser.parse(rootNode, packageName)
        val parsed = ScreenParser.extractTransaction(content) ?: run {
            // 区分两种失败：节点树里根本没有文本（App 自绘/屏蔽了无障碍内容）
            // vs 有文本但不是支付成功页。前者是"屏蔽"，只能靠通知/短信通道兜底。
            if (content.textBlocks.isEmpty()) {
                addLog("[诊断] ⚠️ 屏幕无可读文本，可能被App屏蔽或自绘UI: pkg=$packageName")
                Log.w(TAG, "屏幕无可读文本（可能被屏蔽/自绘UI）: pkg=$packageName")
            } else {
                Log.d(TAG, "屏幕解析失败或被过滤: pkg=$packageName")
            }
            return
        }

        Log.d(TAG, "屏幕解析成功，进入核对: pkg=$packageName, amount=${parsed.amount}")

        // 1.5 微信转账收款分支：此前收到"请收款"通知（不含金额，30 分钟内有效），
        //     用户点开收款页面 → 屏幕提供金额 → 通知+屏幕双信号，转待确认入库。
        //     这是屏幕通道唯一允许直接入库的场景（通知通道拿不到金额，别无他法）。
        val prefs = context.getSharedPreferences("auto_bookkeeping", Context.MODE_PRIVATE)
        val pendingAt = prefs.getLong("wechat_transfer_pending_at", 0)
        val pageText = parsed.content.allText
        val isTransferAcceptedPage = pageText.contains("已存入零钱") ||
                pageText.contains("已收款") || pageText.contains("已收钱") || pageText.contains("已确认收款")
        if (pendingAt > 0 &&
            System.currentTimeMillis() - pendingAt < VERIFY_WINDOW_MS &&
            isTransferAcceptedPage && parsed.amount > 0
        ) {
            val sender = prefs.getString("wechat_transfer_sender", "") ?: ""
            prefs.edit().putLong("wechat_transfer_pending_at", 0).apply()
            addLog("[诊断] 💰 微信转账收款页面 ¥${parsed.amount}（来自：$sender），通知+屏幕双信号入库")
            Log.d(TAG, "微信转账收款入库: amount=${parsed.amount}, sender=$sender")
            processParsedTransaction(
                parsed = parsed.copy(scene = "微信转账", signal = NotificationParser.Signal.WEAK),
                sourceType = "screen",
                appSignature = packageName,
                context = context,
                detectedTime = detectedTime
            )
            return
        }

        // 2. 核对：同金额 ±15 分钟内是否有已入账记录（不写库，只核对）
        val windowStart = detectedTime - VERIFY_WINDOW_MS
        val app = context.applicationContext as JiYiXiaApp
        coroutineScope.launch {
            try {
                val matched = app.database.recordDao()
                    .findRecentByAmount(parsed.amount.toCents(), windowStart, detectedTime)
                if (matched != null) {
                    addLog("[核对] ✓ 屏幕支付¥${parsed.amount} 与已记账记录一致: pkg=$packageName")
                    Log.d(TAG, "屏幕核对通过: amount=${parsed.amount}, pkg=$packageName")
                } else {
                    addLog("[核对] ⚠️ 屏幕发现支付¥${parsed.amount}，无对应记账（通知可能未送达），建议手动补记: pkg=$packageName")
                    Log.w(TAG, "屏幕发现支付但无对应记录: amount=${parsed.amount}, pkg=$packageName")
                }
            } catch (e: Exception) {
                Log.e(TAG, "屏幕核对失败", e)
            }
        }
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
        detectedTime: Long,
        allowBackfill: Boolean = false
    ) {
        val amount = parsed.amount
        if (amount <= 0) {
            addLog("[诊断] ❌ 金额无效: amount=$amount, source=$sourceType")
            return
        }

        // 0. 只记当天：检测时间不是今天的默认跳过
        //    场景：系统重新投递通知栏残留旧通知，若不拦截会生成过去日期的记录。
        //    例外：补捞模式（allowBackfill，重连后从通知栏捞回的支付通知）不再静默丢弃，
        //    而是强制转"待确认"入库——把"记到过去日期"的选择权交给用户，漏单不再永久丢失。
        var isBackfill = false
        if (!isToday(detectedTime)) {
            if (!allowBackfill) {
                addLog("[诊断] ⏭️ 非当天检测，跳过（只记当天）: source=$sourceType, app=$appSignature")
                Log.d(TAG, "非当天检测，跳过: source=$sourceType, app=$appSignature")
                return
            }
            isBackfill = true
            addLog("[诊断] 🔁 补捞非当天通知，转待确认: source=$sourceType, app=$appSignature")
            Log.d(TAG, "补捞非当天通知，转待确认: source=$sourceType, app=$appSignature")
        }

        // 1. 智能去重
        // 1.1 同来源同金额 5 分钟内去重
        if (DedupManager.isDuplicate(amount, sourceType, appSignature)) {
            addLog("[诊断] ⏭️ 同来源去重跳过: amount=$amount, source=$sourceType")
            return
        }

        // 1.2 跨来源去重：如果其他来源已经记录过此金额，跳过
        // （例如：通知已经记录了 ¥38，屏幕检测到 ¥38 应该跳过）
        if (DedupManager.isDuplicateAcrossSources(amount, excludeSourceType = sourceType)) {
            addLog("[诊断] ⏭️ 跨来源去重跳过: amount=$amount, source=$sourceType")
            Log.d(TAG, "跨来源去重跳过: amount=$amount, source=$sourceType")
            return
        }

        addLog("[诊断] ✅ 通过去重: amount=$amount, source=$sourceType, app=$appSignature")
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

                // 补捞的漏单一律待确认（记到过去日期需用户点头）；
                // 信号分级（支柱4）：强信号（官方模板+动作锚点齐备）自动入库，
                // 弱信号（通用/宽松解析）一律转待确认——乱记录收敛到待确认队列，不丢不乱
                val isStrongSignal = parsed.signal == NotificationParser.Signal.STRONG
                val isPending = isBackfill || !isStrongSignal || confidence < 80
                val notePrefix = when {
                    !isPending -> "$typeLabel·$categoryName$merchantLabel"
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

                // 6.1 标记此条检测已成功入库，使跨来源去重能识别
                //    （避免通知和屏幕同时记录同一笔支付）
                DedupManager.markRecorded(amount, sourceType, appSignature)

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

    /**
     * 判断时间戳是否为今天（本地时区）
     *
     * "只记当天"规则的核心判断：自动记账不允许产生过去日期的记录。
     * 公开以便单元测试。
     */
    fun isToday(timestamp: Long): Boolean {
        val target = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
        val today = java.util.Calendar.getInstance()
        return target.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) &&
                target.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR)
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

    /**
     * 供外部调用（如 PaymentAccessibilityService）添加诊断日志
     * 用于排查辅助功能服务是否正常工作
     */
    fun addDiagnosticLog(msg: String) {
        addLog(msg)
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
        "com.jd.jrapp",                       // 京东金融
        // 电商 / 外卖
        "com.taobao.taobao",                  // 淘宝
        "com.jingdong.app.mall",              // 京东
        "com.sankuai.meituan",                // 美团
        "com.sankuai.meituan.takeoutnew",     // 美团外卖
        "me.ele",                             // 饿了么
        "com.dianping.v1",                    // 大众点评
        "com.xunmeng.pinduoduo",              // 拼多多
        "com.ss.android.ugc.aweme",           // 抖音
        "com.smile.gifmaker",                 // 快手
        "com.xingin.xhs",                     // 小红书
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
        "com.xiaojukeji.hitch",
        // 旅行 / 票务
        "ctrip.android.view",                 // 携程旅行
        "com.MobileTicket",                   // 12306
        "com.qunar",                          // 去哪儿
        "com.feeyo",                          // 飞常准
        // 餐饮
        "com.mcdonalds.gma.cn",               // 麦当劳
        "com.kfc.mobile",                     // 肯德基
        "com.starbucks.cn",                   // 星巴克
        "com.lucky.luckyapp"                  // 瑞幸咖啡
    )

    /** 银行 app 包名前缀 */
    val BANK_APP_PREFIXES = listOf(
        "com.icbc", "com.chinamworld", "com.android.bankabc",
        "com.boc.bocsoft", "com.bankcomm", "com.cmbchina",
        "com.spdbccc", "com.pingan", "com.cgbchina", "com.cmbc.mbank",
        "com.cib", "com.cebbank", "com.hxb", "com.bankofbeijing",
        "com.yitong.mbank.psbc", "com.psbc",
        // 新增银行/信用卡 APP
        "com.cmbchina.ccdesk",                // 招商银行掌上生活
        "com.citiccard",                      // 中信银行信用卡（动卡空间）
        "com.cgb.xingye",                     // 兴业银行
        "com.pa.cccd",                        // 平安信用卡
        "com.spdb",                           // 浦发银行
        "com.cebbank",                        // 光大银行
        "com.bankcomm.bankcomm"               // 交通银行
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
