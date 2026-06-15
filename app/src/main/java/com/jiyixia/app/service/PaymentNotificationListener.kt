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
import com.jiyixia.app.ui.MainActivity

/**
 * 通知监听服务（Layer 1）
 *
 * 监听支付 app 的通知，提取交易信息后委托给 PaymentDetector 统一处理。
 * 与 PaymentAccessibilityService（Layer 2）共享 PaymentDetector 的去重和记录逻辑。
 */
class PaymentNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "PaymentListener"
        private const val CHANNEL_ID = "payment_monitor"
        private const val FOREGROUND_ID = 1

        // 服务连接状态
        @Volatile
        var isServiceConnected = false
            private set

        // 兼容旧代码：代理到 PaymentDetector 的日志
        val detections: List<String> get() = PaymentDetector.detectionLogs
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName ?: return

        // 只处理支付类通知
        if (!PaymentDetector.isPaymentApp(packageName)) return

        val notification = sbn.notification ?: return
        val allText = extractAllText(notification)

        if (BuildConfig.DEBUG) Log.d(TAG, "收到通知: pkg=$packageName, text=$allText")

        if (allText.isBlank()) return

        // 检查是否包含支付相关关键词（避免误识别普通聊天消息）
        if (!PaymentDetector.containsPaymentKeyword(allText)) {
            if (BuildConfig.DEBUG) Log.d(TAG, "无支付关键词，跳过")
            return
        }

        // 提取金额
        val amount = PaymentDetector.extractAmount(allText)
        if (amount == null || amount <= 0) {
            if (BuildConfig.DEBUG) Log.d(TAG, "无法解析金额，跳过")
            return
        }

        // 委托给统一检测入口
        PaymentDetector.processDetection(
            source = "通知",
            amount = amount,
            text = allText,
            packageName = packageName,
            context = applicationContext
        )
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

    // ===== 前台服务保活 =====

    override fun onListenerConnected() {
        super.onListenerConnected()
        isServiceConnected = true
        Log.d(TAG, "通知监听服务已连接")
        startForeground()

        // 启动保活服务（小米等国产ROM需要）
        KeepAliveService.start(this)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isServiceConnected = false
        Log.d(TAG, "通知监听服务已断开，尝试重新连接...")

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
