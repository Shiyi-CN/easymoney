package com.jiyixia.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * 开机/应用更新后自动启动保活服务
 *
 * 小米等国产 ROM 重启后不会自动恢复后台服务，
 * 需要监听 BOOT_COMPLETED 主动拉起。
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "收到广播: $action")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                // 启动保活服务
                try {
                    KeepAliveService.start(context)
                    Log.d(TAG, "保活服务已自启动")
                } catch (e: Exception) {
                    Log.e(TAG, "保活服务自启动失败", e)
                }

                // 调度 WorkManager 周期性兜底
                KeepAliveWorker.schedule(context)
            }
        }
    }
}
