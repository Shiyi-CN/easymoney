package com.jiyixia.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.jiyixia.app.BuildConfig
import androidx.core.app.NotificationCompat
import com.jiyixia.app.JiYiXiaApp
import com.jiyixia.app.data.entity.Record
import com.jiyixia.app.ui.MainActivity
import com.jiyixia.app.domain.usecase.SmartParseUseCase
import com.jiyixia.app.util.toCents
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PaymentNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "PaymentListener"
        private const val CHANNEL_ID = "payment_monitor"
        private const val FOREGROUND_ID = 1
        private const val DETECT_ID = 2

        // 最近识别记录（用于调试）
        private val recentDetections = mutableListOf<String>()
        val detections: List<String> get() = recentDetections.toList()

        // 服务连接状态
        @Volatile
        var isServiceConnected = false
            private set

        // 通知去重：key = "金额_分钟时间戳"，value = 首次出现时间
        private val recentNotifications = HashMap<String, Long>()
        private const val DEDUP_WINDOW_MS = 60_000L // 1 分钟去重窗口

        // 多种金额正则，覆盖微信/支付宝/银行各种格式
        private val AMOUNT_PATTERNS = listOf(
            // "支出/消费/付款/扣款/支付/还款/缴费 + 金额元"
            Regex("""(?:支出|消费|付款|扣款|支付|转账|付款成功|支付成功|还款|缴费|退款|汇入|汇出)[^\d]*([\d,.]+)\s*元"""),
            // ¥ 或 ￥ 符号
            Regex("""[¥￥]\s*([\d,.]+)"""),
            // "金额元"（微信转账常见）
            Regex("""([\d,.]+)\s*元"""),
            // "向xx转账/收xx转账/转给xx + 金额"
            Regex("""(?:向|收|转给).{0,15}转(?:账|款)[^\d]*([\d,.]+)"""),
            // "收款到账" 格式
            Regex("""到账[^\d]*([\d,.]+)"""),
            // "交易金额: 元" / "金额: 元"
            Regex("""(?:交易)?金额[：:]\s*[¥￥]?\s*([\d,.]+)"""),
            // "扣款金额" 银行常见
            Regex("""扣款[^\d]*([\d,.]+)"""),
            // "入账" 收入
            Regex("""入账[^\d]*([\d,.]+)"""),
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName ?: return

        // 只处理支付类通知
        if (!isPaymentApp(packageName)) return

        val notification = sbn.notification ?: return
        val allText = extractAllText(notification)

        if (BuildConfig.DEBUG) Log.d(TAG, "收到通知: pkg=$packageName, text=$allText")

        if (allText.isBlank()) return

        // 检查是否包含支付相关关键词（避免误识别普通聊天消息）
        if (!containsPaymentKeyword(allText)) {
            if (BuildConfig.DEBUG) Log.d(TAG, "无支付关键词，跳过")
            return
        }

        // 移入协程处理，避免阻塞主线程
        val app = applicationContext as JiYiXiaApp
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = app.database
                val categories = db.categoryDao().getAll().first()
                val nameToId = categories.associate { it.name to it.id }
                val defaultCategoryId = categories.firstOrNull()?.id ?: 0L

                val parsed = SmartParseUseCase.parse(
                    text = allText,
                    categoryNameToId = nameToId,
                    defaultCategoryId = defaultCategoryId
                )

                // 使用 SmartParseUseCase 返回的金额
                val amount = parsed?.amount
                if (amount == null || amount <= 0) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "无法解析金额，跳过")
                    return@launch
                }

                // 通知去重：同一分钟内相同金额的通知只处理一次
                val minuteTimestamp = System.currentTimeMillis() / 60_000 * 60_000
                val dedupKey = "${amount}_$minuteTimestamp"
                synchronized(recentNotifications) {
                    val lastTime = recentNotifications[dedupKey]
                    if (lastTime != null && System.currentTimeMillis() - lastTime < DEDUP_WINDOW_MS) {
                        if (BuildConfig.DEBUG) Log.d(TAG, "重复通知，跳过: key=$dedupKey")
                        return@launch
                    }
                    recentNotifications[dedupKey] = System.currentTimeMillis()
                    // 清理过期记录
                    val now = System.currentTimeMillis()
                    recentNotifications.entries.removeIf { now - it.value > DEDUP_WINDOW_MS * 2 }
                }

                if (BuildConfig.DEBUG) Log.d(TAG, "识别到支付: amount=$amount, text=$allText")

                // 保存记录
                val categoryName = parsed.categoryName ?: "其他"
                val type = if (parsed.isExpense == false) 1 else 0
                val confidence = parsed.confidence ?: 50
                val isReimbursable = parsed.isReimbursable ?: false
                val reimbursementTarget = parsed.reimbursementTarget ?: ""

                val category = categories.find { it.name == categoryName && it.type == type }
                    ?: categories.find { it.name == categoryName }
                    ?: categories.find { it.name == "其他" && it.type == type }
                    ?: categories.firstOrNull()

                if (category != null) {
                    val isPending = confidence < 80
                    val typeLabel = if (type == 1) "收入" else "支出"
                    db.recordDao().insert(
                        Record(
                            type = type,
                            amount = amount.toCents(),
                            categoryId = category.id,
                            note = "$typeLabel·$categoryName",
                            date = System.currentTimeMillis(),
                            isPendingConfirm = isPending,
                            confidence = confidence,
                            isReimbursable = isReimbursable,
                            reimbursementTarget = reimbursementTarget
                        )
                    )
                    showDetectNotification(amount, "$typeLabel·$categoryName", isPending)
                    addSanitizedDetection(typeLabel, amount, categoryName, confidence)
                }
            } catch (e: Exception) {
                Log.e(TAG, "处理通知失败", e)
                addDetection("错误: ${e.message}")
            }
        }
    }

    private fun isPaymentApp(packageName: String): Boolean {
        // 精确匹配的第三方支付
        val exactMatch = setOf(
            "com.eg.android.AlipayGphone",      // 支付宝
            "com.tencent.mm",                     // 微信
            "com.tencent.mobileqq",               // QQ
            "com.unionpay",                       // 云闪付
            "com.tencent.wetype",                 // 微信输入法（可能推送支付提示）
        )
        if (packageName in exactMatch) return true

        // 前缀匹配的银行 app（包名在不同设备/版本可能有后缀差异）
        val bankPrefixes = listOf(
            "com.icbc",                           // 工商银行
            "com.chinamworld",                    // 建设银行
            "com.android.bankabc",                // 农业银行
            "com.boc.bocsoft",                    // 中国银行
            "com.bankcomm",                       // 交通银行
            "com.cmbchina",                       // 招商银行
            "com.spdbccc",                        // 浦发银行
            "com.pingan",                         // 平安银行
            "com.cgbchina",                       // 广发银行
            "com.cmbc.mbank",                     // 民生银行
            "com.cib",                            // 兴业银行
            "com.cebbank",                        // 光大银行
            "com.hxb",                            // 华夏银行
            "com.bankofbeijing",                  // 北京银行
            "com.yitong.mbank.psbc",              // 邮储银行
            "com.psbc",                           // 邮储银行（另一种包名）
        )
        return bankPrefixes.any { packageName.startsWith(it) }
    }

    /** 检查是否包含支付关键词，过滤普通聊天消息 */
    private fun containsPaymentKeyword(text: String): Boolean {
        val keywords = listOf(
            "支付", "付款", "转账", "扣款", "消费", "支出", "收款", "到账",
            "买单", "结算", "充值", "缴费", "还款", "汇款", "入账", "退款",
            "微信支付", "支付宝", "Alipay",
            "信用卡", "借记卡", "银行卡",
            "订单", "商户", "门店", "交易",
            "工资", "报销", "提现"
        )
        return keywords.any { text.contains(it) }
    }

    /** 判断是否为收入（收款/入账/退款/工资等） */
    private fun isIncome(text: String): Boolean {
        val incomeKeywords = listOf(
            "收款", "入账", "到账", "转入", "退款", "返回", "报销",
            "工资", "奖金", "红包", "退费", "返现", "提现"
        )
        return incomeKeywords.any { text.contains(it) } &&
               !text.contains("付款") && !text.contains("消费") && !text.contains("支出")
    }

    /** 全面提取通知文本（兼容微信/支付宝各种格式） */
    private fun extractAllText(notification: Notification): String {
        val extras = notification.extras ?: return ""
        val sb = StringBuilder()

        // 标题
        extras.getCharSequence(Notification.EXTRA_TITLE)?.let { sb.append(it).append(" ") }
        // 副标题
        extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.let { sb.append(it).append(" ") }
        // 主文本
        extras.getCharSequence(Notification.EXTRA_TEXT)?.let { sb.append(it).append(" ") }
        // 大文本（微信支付详情常在这里）
        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.let { sb.append(it).append(" ") }
        // 摘要
        extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.let { sb.append(it).append(" ") }
        // 子文本
        extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.let { sb.append(it).append(" ") }
        // 文本行（多行通知）
        @Suppress("DEPRECATION")
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.let { lines ->
            lines.forEach { sb.append(it).append(" ") }
        }

        return sb.toString().trim()
    }

    private fun addDetection(msg: String) {
        synchronized(recentDetections) {
            val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
            recentDetections.add(0, "$time $msg")
            if (recentDetections.size > 20) recentDetections.removeLast()
        }
    }

    /**
     * 添加脱敏的识别日志
     * 只显示时间+分类+金额，不暴露原始通知全文
     */
    private fun addSanitizedDetection(type: String, amount: Double, categoryName: String, confidence: Int) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        addDetection("$type ¥$amount → $categoryName (置信度$confidence%)")
    }

    // ===== 前台服务保活 =====

    override fun onListenerConnected() {
        super.onListenerConnected()
        isServiceConnected = true
        Log.d(TAG, "通知监听服务已连接")
        startForeground()
        addDetection("服务已启动")

        // 启动保活服务（小米等国产ROM需要）
        KeepAliveService.start(this)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isServiceConnected = false
        Log.d(TAG, "通知监听服务已断开，尝试重新连接...")
        addDetection("服务已断开，正在重连...")

        // 尝试重新绑定（小米等国产ROM需要）
        requestRebind(ComponentName(this, PaymentNotificationListener::class.java))
    }

    private fun startForeground() {
        createChannel()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("记一下 - 监听中")
            .setContentText("正在监听支付通知")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()

        try {
            startForeground(FOREGROUND_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "启动前台服务失败", e)
        }
    }

    /** 显示识别到支付的通知 */
    private fun showDetectNotification(amount: Double, category: String, isPending: Boolean) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel()

        val text = if (isPending) {
            "识别到 ¥${String.format("%.2f", amount)} → $category（待确认）"
        } else {
            "识别到 ¥${String.format("%.2f", amount)} → $category"
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("记一下 - 自动记账")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        // 用时间戳做 ID，避免覆盖
        nm.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID, "支付监听",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "监听支付通知并自动记账" }
            nm.createNotificationChannel(channel)
        }
    }
}
