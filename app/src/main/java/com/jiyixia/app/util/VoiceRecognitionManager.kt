package com.jiyixia.app.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.Locale

/**
 * 语音识别管理器 —— 封装 Android 原生 SpeechRecognizer
 *
 * 设计要点：
 * - 优先使用离线识别（EXTRA_PREFER_OFFLINE）
 * - 中文普通话识别
 * - 通过 Flow 暴露识别结果，方便 Compose 集成
 * - 提供权限检查
 */
class VoiceRecognitionManager(private val context: Context) {

    sealed class State {
        /** 空闲，准备就绪 */
        data object Idle : State()
        /** 正在监听语音 */
        data object Listening : State()
        /** 识别完成 */
        data class Result(val text: String) : State()
        /** 出错 */
        data class Error(val message: String) : State()
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val _state = Channel<State>(Channel.CONFLATED)
    val state: Flow<State> = _state.receiveAsFlow()

    /**
     * 检查设备是否支持语音识别
     */
    fun isRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    /**
     * 检查是否已授予录音权限
     */
    fun hasRecordPermission(): Boolean {
        return context.checkSelfPermission(
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 开始监听语音
     */
    fun startListening() {
        // 先销毁旧实例
        destroy()

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _state.trySend(State.Listening)
                }

                override fun onBeginningOfSpeech() {
                    // 用户开始说话
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // 音量变化，可用于显示波形动画
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    // 用户停止说话
                }

                override fun onError(error: Int) {
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "音频错误"
                        SpeechRecognizer.ERROR_CLIENT -> "客户端错误"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "缺少录音权限"
                        SpeechRecognizer.ERROR_NETWORK -> "网络错误（语音识别需要联网）"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
                        SpeechRecognizer.ERROR_NO_MATCH -> "未识别到语音，请重试"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别引擎忙，请稍后"
                        SpeechRecognizer.ERROR_SERVER -> "服务器错误"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "语音超时，请再说一遍"
                        else -> "识别失败 (错误码: $error)"
                    }
                    _state.trySend(State.Error(msg))
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )
                    val text = matches?.firstOrNull() ?: ""
                    if (text.isNotBlank()) {
                        _state.trySend(State.Result(text))
                    } else {
                        _state.trySend(State.Error("未识别到语音内容"))
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    // 部分结果，可用于实时显示
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                // 语言：中文普通话
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "zh-CN")

                // 优先离线识别
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)

                // 部分结果
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)

                // 最长静音检测时间
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000)

                // 最大结果数
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }

            startListening(intent)
        }
    }

    /**
     * 停止监听
     */
    fun stopListening() {
        speechRecognizer?.stopListening()
    }

    /**
     * 销毁识别器实例，释放资源
     */
    fun destroy() {
        speechRecognizer?.apply {
            stopListening()
            destroy()
        }
        speechRecognizer = null
    }
}
