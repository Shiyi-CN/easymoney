package com.jiyixia.app.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * 应用内语音识别器 —— 基于 SpeechRecognizer API
 *
 * 与 RecognizerIntent（启动系统Activity）不同，
 * SpeechRecognizer 在应用内直接进行语音识别，
 * 不会弹出系统语音引擎的授权确认框。
 *
 * 这是解决国产ROM（小米/华为/OPPO/vivo）每次调用
 * 系统语音都弹授权问题的根本方案。
 *
 * 使用流程：
 * 1. 检查 isAvailable()
 * 2. startListening(onResult, onError)
 * 3. 识别完成或出错后自动停止
 * 4. 不再使用时调用 destroy()
 *
 * 注意：必须在主线程创建和调用
 */
class InAppVoiceRecognizer(private val context: Context) {

    companion object {
        private const val TAG = "InAppVoiceRecognizer"

        /**
         * 检测设备是否支持 SpeechRecognizer
         *
         * 注意：此方法在国产手机（小米/华为/OPPO）上可能返回 false，
         * 但实际设备有语音引擎（如小爱同学）。因此不应作为前置拦截条件，
         * 应直接尝试创建识别器，通过 onError 回调判断真实情况。
         */
        fun isAvailable(context: Context): Boolean =
            SpeechRecognizer.isRecognitionAvailable(context)
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    /**
     * 开始语音识别
     *
     * @param onResult 识别成功回调，text 为最佳识别文本
     * @param onError 识别失败回调，errorMsg 为错误描述
     * @param onReady 引擎就绪回调（可在此更新UI为"请说话"）
     * @param onEnd 语音结束回调（检测到说话结束）
     */
    fun startListening(
        onResult: (String?) -> Unit,
        onError: (String) -> Unit,
        onReady: () -> Unit = {},
        onEnd: () -> Unit = {}
    ) {
        // 如果正在监听，先停止
        if (isListening) {
            stopListening()
        }

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        } catch (e: Exception) {
            Log.e(TAG, "创建 SpeechRecognizer 失败", e)
            onError("无法初始化语音识别引擎")
            return
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "onReadyForSpeech: 引擎就绪，等待说话")
                onReady()
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "onBeginningOfSpeech: 检测到开始说话")
            }

            override fun onRmsChanged(rmsdB: Float) {
                // 音量变化，可用于波形动画
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d(TAG, "onEndOfSpeech: 说话结束")
                onEnd()
            }

            override fun onError(error: Int) {
                val errorMsg = when (error) {
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时，请重试"
                    SpeechRecognizer.ERROR_NETWORK -> "网络异常"
                    SpeechRecognizer.ERROR_AUDIO -> "录音失败"
                    SpeechRecognizer.ERROR_SERVER -> "服务器异常"
                    SpeechRecognizer.ERROR_CLIENT -> "客户端异常"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "未检测到说话，请重试"
                    SpeechRecognizer.ERROR_NO_MATCH -> "未识别到语音内容"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器忙碌，请稍后"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "麦克风权限不足"
                    else -> "识别失败（错误码 $error）"
                }
                Log.e(TAG, "onError: $errorMsg")
                isListening = false
                onError(errorMsg)
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                Log.d(TAG, "onResults: $text")
                isListening = false
                onResult(text)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                // 部分识别结果（实时显示，可选）
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

        isListening = true
        speechRecognizer?.startListening(intent)
    }

    /**
     * 停止语音识别
     */
    fun stopListening() {
        if (isListening) {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.e(TAG, "stopListening 异常", e)
            }
            isListening = false
        }
    }

    /**
     * 销毁识别器，释放资源
     * 必须在 Composable 的 DisposableEffect 或 Activity.onDestroy 中调用
     */
    fun destroy() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "destroy 异常", e)
        }
        speechRecognizer = null
        isListening = false
    }
}
