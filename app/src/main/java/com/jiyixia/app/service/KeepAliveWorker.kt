package com.jiyixia.app.service

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * WorkManager 周期性兜底 Worker
 *
 * 每 15 分钟检查一次保活服务是否存活，
 * 被系统杀死后自动重新拉起。
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

        return Result.success()
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
