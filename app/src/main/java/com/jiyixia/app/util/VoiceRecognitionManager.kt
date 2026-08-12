package com.jiyixia.app.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.RecognizerIntent
import androidx.core.content.ContextCompat

/**
 * 语音识别管理器 —— 基于系统 Intent
 *
 * 使用 RecognizerIntent.ACTION_RECOGNIZE_SPEECH 调用系统语音输入界面
 * 国产手机（小米/OPPO/vivo等）通常都有内置语音输入支持
 *
 * 关键修复：
 * 1. 提供引擎可用性检测（避免无引擎设备崩溃或反复弹授权）
 * 2. 提供麦克风权限检查工具（Android 6+ 必须运行时授权）
 * 3. 区分"无引擎"和"无权限"两种失败场景，给出针对性提示
 */
class VoiceRecognitionManager(private val context: Context) {

    companion object {
        /**
         * 创建语音识别 Intent
         */
        fun createRecognizerIntent(): Intent {
            return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "说出金额和用途，如「午餐 38」")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                // 离线优先（减少联网授权弹窗，但部分设备仍需联网）
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }

        /**
         * 从识别结果中提取最佳文本
         */
        fun extractBestText(data: Intent?): String? {
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            return results?.firstOrNull()
        }

        /**
         * 检测设备是否有可用的语音识别引擎
         *
         * 国产手机无 Google 服务时可能返回 false，
         * 此时调用 launch() 会抛 ActivityNotFoundException，
         * 应提前拦截并提示用户。
         */
        fun isSpeechAvailable(context: Context): Boolean {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            val list = context.packageManager.queryIntentActivities(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
            )
            return list.isNotEmpty()
        }

        /**
         * 检查是否已授予麦克风权限（Android 6+ 运行时权限）
         */
        fun hasRecordAudioPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        }

        /**
         * 判断是否需要运行时申请麦克风权限
         */
        fun needRuntimePermission(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
    }
}
