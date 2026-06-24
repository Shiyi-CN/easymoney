package com.jiyixia.app.service

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
import androidx.core.app.NotificationCompat
import com.jiyixia.app.BuildConfig
import com.jiyixia.app.ui.MainActivity

/**
 * 通知监听服务（Layer 1 - 重构版）
 *
 * 改进点：
 * 1. 使用 PaymentDetector.processNotification 统一入口
 * 2. 移除本地关键词过滤和金额提取（由 NotificationParser 处理）
 * 3. 只负责：接收通知 → 过滤支付 app → 委托给 PaymentDetector
 *
 * 与 PaymentAccessibilityService（Layer 2）和 SmsReceiver（Layer 3）共享
 * PaymentDetector 的去重和记录逻辑。
 */
class PaymentNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "PaymentListener"
        private const val CHANNEL_ID = "payment_monitor"
        private const val FOREGROUND_ID = 1

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

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "收到通知: pkg=$packageName")
        }

        // 提取通知时间（优先使用通知时间，作为支付时间的参考）
        val notificationTime = notification.`when`?.takeIf { it > 0 }
            ?: System.currentTimeMillis()

        // 委托给 PaymentDetector 统一处理
        // （内部会做：结构化解析 → 营销过滤 → 去重 → 场景识别 → 记录）
        PaymentDetector.processNotification(
            notification = notification,
            packageName = packageName,
            context = applicationContext,
            detectedTime = notificationTime
        )
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
        Log.w(TAG, "通知监听服务已断开，尝试重新连接...")

        // 尝试 requestRebind 重新绑定（Android 原生 API）
        // 注意：在国产 ROM（小米/华为/OPPO/vivo）和 Android 12+ 上经常不生效，
        // 需要依赖用户在系统设置里关闭再打开权限来恢复（首页会有警告横幅引导）。
        // 不要在这里启动 KeepAliveService：onListenerDisconnected 被调用时 App 大概率
        // 在后台，Android 12+ 会拒绝后台启动前台服务，KeepAliveService.startForeground()
        // 抛出 ForegroundServiceStartNotAllowedException 会导致服务崩溃，进而触发系统
        // 更积极地杀掉 App 进程，形成"断连→崩溃→进程被杀→再次断连"的恶性循环。
        try {
            requestRebind(ComponentName(this, PaymentNotificationListener::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "requestRebind 失败", e)
        }
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
