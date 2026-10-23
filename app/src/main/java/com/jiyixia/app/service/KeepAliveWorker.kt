package com.jiyixia.app.service

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/**
 * WorkManager 周期性兜底 Worker
 *
 * 每 15 分钟做两件事：
 * 1. 检查保活服务是否存活，被系统杀死后自动重新拉起
 * 2. 端到端自检：发一条自测通知，若监听服务收到则证明
 *    "系统 → 通知 → 监听服务"全链路通；收不到 = 链路断，
 *    立即提醒用户去系统设置重新授权（节流：24 小时最多提醒一次）
 *
 * WorkManager 比 START_STICKY 更可靠：
 * - 系统会保证 Worker 最终被执行（即使 App 被杀）
 * - 利用 JobScheduler 调度，符合 Android Doze 模式
 */
class KeepAliveWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "KeepAliveWorker"
        private const val WORK_NAME = "keep_alive_worker"
        private const val SELF_TEST_CHANNEL = "self_test"
        private const val ALERT_CHANNEL = "link_alert"
        private const val ALERT_ID = 20260602
        private const val PREFS = "auto_bookkeeping"
        private const val WARN_INTERVAL_MS = 24 * 60 * 60 * 1000L

        /** 调度周期性 Worker（15 分钟是 WorkManager 最短周期） */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(false)
                .build()

            val request = PeriodicWorkRequestBuilder<KeepAliveWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
            Log.d(TAG, "保活 Worker 已调度（每 15 分钟检查）")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "保活检查开始")

        val context = applicationContext

        // 检查 KeepAliveService 是否在运行
        if (!isServiceRunning(context, KeepAliveService::class.java.name)) {
            Log.w(TAG, "KeepAliveService 未运行，尝试重启...")
            try {
                KeepAliveService.start(context)
                Log.d(TAG, "KeepAliveService 已重启")
            } catch (e: Exception) {
                Log.e(TAG, "KeepAliveService 重启失败", e)
            }
        } else {
            Log.d(TAG, "KeepAliveService 运行中，无需操作")
        }

        // 端到端自检（链路断时最多 30 分钟内被用户感知，而不是等支付失败后）
        runEndToEndSelfTest(context)

        return Result.success()
    }

    /**
     * 端到端自检：发自测通知 → 等 10 秒 → 检查监听服务是否收到。
     * isServiceConnected 只能证明"绑定活着"，自测通知才能证明"通知真的能送达"。
     */
    private suspend fun runEndToEndSelfTest(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        if (Build.VERSION.SDK_INT >= 33 && !nm.areNotificationsEnabled()) {
            // 自己的通知权限都没给，无法自检也无法提醒
            Log.w(TAG, "App 通知权限未授予，跳过自检")
            return
        }

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val sentAt = System.currentTimeMillis()
        prefs.edit().putLong("self_test_sent_at", sentAt).apply()

        try {
            createSelfTestChannel(context)
            val notification = NotificationCompat.Builder(context, SELF_TEST_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("自动记账链路自检")
                .setContentText("系统检测中，稍后自动消失")
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setSilent(true)
                .build()
            nm.notify(PaymentNotificationListener.SELF_TEST_ID, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "发自测通知被拒绝", e)
            return
        }

        delay(10_000)

        val receivedAt = PaymentNotificationListener.lastSelfTestReceivedAt
        if (receivedAt >= sentAt) {
            Log.d(TAG, "端到端自检通过")
            prefs.edit().putLong("self_test_last_ok_at", receivedAt).apply()
            return
        }

        // 链路断：提醒用户去重新授权（节流 24 小时，避免轰炸）
        val lastWarnAt = prefs.getLong("self_test_last_warn_at", 0)
        if (System.currentTimeMillis() - lastWarnAt < WARN_INTERVAL_MS) {
            Log.w(TAG, "链路断，但 24 小时内已提醒过，跳过")
            return
        }
        showBrokenLinkAlert(context)
        prefs.edit().putLong("self_test_last_warn_at", System.currentTimeMillis()).apply()
        Log.w(TAG, "端到端自检失败：自测通知未被监听服务收到，已发提醒")
    }

    /** 链路断提醒：通知栏直接引导去系统设置重新授权 */
    private fun showBrokenLinkAlert(context: Context) {
        try {
            createAlertChannel(context)
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            val pendingIntent = PendingIntent.getActivity(
                context, ALERT_ID, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, ALERT_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("自动记账可能已失效")
                .setContentText("检测到通知监听异常，点击重新授权（关闭再打开通知使用权）")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
            NotificationManagerCompat.from(context).notify(ALERT_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "发链路断提醒失败", e)
        }
    }

    private fun createSelfTestChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SELF_TEST_CHANNEL, "自检",
                NotificationManager.IMPORTANCE_MIN
            ).apply { description = "自动记账链路自检通知" }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun createAlertChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ALERT_CHANNEL, "自动记账异常提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "自动记账失效时提醒用户修复" }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    /** 检查某个服务是否正在运行 */
    private fun isServiceRunning(context: Context, serviceName: String): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return try {
            am.getRunningServices(Int.MAX_VALUE)
                .any { it.service.className == serviceName }
        } catch (e: Exception) {
            // Android 8+ getRunningServices 只返回自己的服务，可能抛异常
            false
        }
    }
}
