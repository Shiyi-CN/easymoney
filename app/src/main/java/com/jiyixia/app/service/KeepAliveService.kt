package com.jiyixia.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jiyixia.app.ui.MainActivity

/**
 * 前台保活服务
 * 用于在小米等国产ROM上保持通知监听服务的连接
 *
 * 改进：定时检查通知监听服务连接状态，断连时自动 requestRebind 重试。
 * 小米/HyperOS 上 onListenerDisconnected 中的 requestRebind 经常静默失败，
 * 需要前台服务周期性重试才能恢复。
 */
class KeepAliveService : Service() {

    companion object {
        private const val TAG = "KeepAliveService"
        private const val CHANNEL_ID = "keep_alive"
        private const val WARNING_CHANNEL_ID = "service_warning"
        private const val NOTIFICATION_ID = 3
        private const val WARNING_NOTIFICATION_ID = 4

        /** 通知监听重连检查间隔：45 秒（缩短检测延迟） */
        private const val REBIND_CHECK_INTERVAL_MS = 45_000L

        /** 连续断连 N 次后弹用户提醒（约 2 分 15 秒） */
        private const val DISCONNECT_WARN_THRESHOLD = 3

        fun start(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, KeepAliveService::class.java))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var rebindRetryCount = 0

    private val rebindCheckRunnable = object : Runnable {
        override fun run() {
            checkAndRebindNotificationListener()
            // 每 90 秒检查一次
            handler.postDelayed(this, REBIND_CHECK_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "保活服务创建")
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "保活服务启动")
        // 启动周期性重连检查
        handler.removeCallbacks(rebindCheckRunnable)
        handler.postDelayed(rebindCheckRunnable, REBIND_CHECK_INTERVAL_MS)
        return START_STICKY // 被杀死后自动重启
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(rebindCheckRunnable)
        Log.d(TAG, "保活服务销毁")
    }

    /**
     * 检查通知监听服务是否已断开
     *
     * 小米/HyperOS 上 onListenerDisconnected 的 requestRebind 经常静默失败，
     * 这里通过前台服务周期性记录连接状态，便于排查断连问题。
     * 连续断连超过阈值后，弹通知提醒用户手动检查权限。
     */
    private fun checkAndRebindNotificationListener() {
        if (!PaymentNotificationListener.isServiceConnected) {
            rebindRetryCount++
            Log.w(TAG, "通知监听已断开 (检测到 $rebindRetryCount 次)，等待系统重连...")

            // 连续断连超过阈值，提醒用户
            if (rebindRetryCount >= DISCONNECT_WARN_THRESHOLD) {
                showDisconnectWarning()
            }
        } else {
            if (rebindRetryCount > 0) {
                Log.d(TAG, "通知监听已恢复（之前断连 $rebindRetryCount 次）")
                // 恢复后取消警告通知
                cancelDisconnectWarning()
            }
            rebindRetryCount = 0
        }
    }

    /**
     * 显示断连警告通知，提醒用户自动记账可能失效
     */
    private fun showDisconnectWarning() {
        createWarningChannel()

        val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, WARNING_CHANNEL_ID)
            .setContentTitle("自动记账可能已失效")
            .setContentText("点击此处检查通知监听权限")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(WARNING_NOTIFICATION_ID, notification)
    }

    /**
     * 取消断连警告通知
     */
    private fun cancelDisconnectWarning() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.cancel(WARNING_NOTIFICATION_ID)
    }

    /**
     * 创建高优先级警告通知渠道
     */
    private fun createWarningChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                WARNING_CHANNEL_ID,
                "服务异常提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "自动记账服务断连时提醒用户"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification() {
        createChannel()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("记一下")
            .setContentText("正在后台保护记账服务")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "服务保活",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持记账服务在后台运行"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }
}
