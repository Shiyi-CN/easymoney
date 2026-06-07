package com.jiyixia.app.util

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent

/**
 * 语音识别管理器 —— 基于系统 Intent
 *
 * 使用 RecognizerIntent.ACTION_RECOGNIZE_SPEECH 调用系统语音输入界面
 * 国产手机（小米/OPPO/vivo等）通常都有内置语音输入支持
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
    }
}