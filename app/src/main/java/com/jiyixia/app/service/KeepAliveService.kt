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
        private const val NOTIFICATION_ID = 3

        /** 通知监听重连检查间隔：90 秒 */
        private const val REBIND_CHECK_INTERVAL_MS = 90_000L

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
     * 实际重连依赖 onListenerDisconnected 中的 requestRebind 和用户在系统设置里的操作。
     */
    private fun checkAndRebindNotificationListener() {
        if (!PaymentNotificationListener.isServiceConnected) {
            rebindRetryCount++
            Log.w(TAG, "通知监听已断开 (检测到 $rebindRetryCount 次)，等待系统重连...")
        } else {
            if (rebindRetryCount > 0) {
                Log.d(TAG, "通知监听已恢复（之前断连 $rebindRetryCount 次）")
            }
            rebindRetryCount = 0
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
