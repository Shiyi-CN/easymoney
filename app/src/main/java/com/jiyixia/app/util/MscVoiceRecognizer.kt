package com.jiyixia.app.util

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.iflytek.cloud.InitListener
import com.iflytek.cloud.RecognizerListener
import com.iflytek.cloud.RecognizerResult
import com.iflytek.cloud.SpeechConstant
import com.iflytek.cloud.SpeechError
import com.iflytek.cloud.SpeechRecognizer
import org.json.JSONArray
import org.json.JSONObject

/**
 * 讯飞 MSC SDK 语音听写封装
 *
 * 支持在线+离线识别。使用方法：
 * 1. startListening(onResult) 开始识别
 * 2. stopListening() 主动停止并获取最终结果
 * 3. cancel() 取消识别
 */
object MscVoiceRecognizer {

    private const val TAG = "MscVoiceRecognizer"

    private var recognizer: SpeechRecognizer? = null
    private var appContext: Context? = null
    private var resultBuilder = StringBuilder()
    private var resultCallback: ((String, Boolean) -> Unit)? = null
    private var errorCallback: ((String) -> Unit)? = null
    private var isListening = false

    /**
     * 确保识别器已初始化
     */
    private fun ensureInit(context: Context): Boolean {
        if (recognizer != null) return true
        appContext = context.applicationContext
        return try {
            recognizer = SpeechRecognizer.createRecognizer(
                context.applicationContext,
                InitListener { code ->
                    Log.i(TAG, "MSC初始化回调: code=$code")
                }
            )
            recognizer != null
        } catch (e: Exception) {
            Log.e(TAG, "MSC识别器创建失败", e)
            false
        }
    }

    /**
     * 开始语音听写
     * @param context 上下文
     * @param onResult 识别结果回调（text=识别文本, isLast=是否最终结果）
     * @param onError 错误回调
     */
    fun startListening(
        context: Context,
        onResult: (text: String, isLast: Boolean) -> Unit,
        onError: (error: String) -> Unit
    ): Boolean {
        if (!ensureInit(context)) {
            onError("识别器初始化失败")
            return false
        }

        if (isListening) {
            Log.w(TAG, "已经在识别中，忽略重复请求")
            return false
        }

        resultBuilder.clear()
        resultCallback = onResult
        errorCallback = onError
        isListening = true

        return try {
            val r = recognizer!!

            // 设置参数
            r.setParameter(SpeechConstant.PARAMS, null)
            // 引擎类型：cloud=纯在线，plus=云端+本地混合（推荐，支持离线）
            r.setParameter(SpeechConstant.ENGINE_TYPE, "cloud")
            // 语种：中文
            r.setParameter(SpeechConstant.LANGUAGE, "zh_cn")
            // 方言：mandarin=普通话
            r.setParameter(SpeechConstant.ACCENT, "mandarin")
            // 标点符号：1=开启
            r.setParameter(SpeechConstant.ASR_PTT, "0")
            // 音频采样率
            r.setParameter(SpeechConstant.SAMPLE_RATE, "16000")
            // VAD 前端点超时（静音多久停止，毫秒）
            r.setParameter(SpeechConstant.VAD_BOS, "4000")
            // VAD 后端点超时（说话后静音多久停止，毫秒）
            r.setParameter(SpeechConstant.VAD_EOS, "2000")

            val code = r.startListening(listener)
            if (code != 0) {
                isListening = false
                onError("开始识别失败，错误码: $code")
                false
            } else {
                Log.i(TAG, "开始语音识别")
                true
            }
        } catch (e: Exception) {
            isListening = false
            Log.e(TAG, "startListening 异常", e)
            onError("启动识别异常: ${e.message}")
            false
        }
    }

    /**
     * 主动停止识别（会触发最终结果回调）
     */
    fun stopListening() {
        if (!isListening) return
        try {
            recognizer?.stopListening()
            Log.i(TAG, "停止语音识别")
        } catch (e: Exception) {
            Log.e(TAG, "stopListening 异常", e)
        }
    }

    /**
     * 取消识别（不触发结果回调）
     */
    fun cancel() {
        isListening = false
        resultBuilder.clear()
        resultCallback = null
        errorCallback = null
        try {
            recognizer?.cancel()
            Log.i(TAG, "取消语音识别")
        } catch (e: Exception) {
            Log.e(TAG, "cancel 异常", e)
        }
    }

    /**
     * 销毁识别器（App 退出时调用）
     */
    fun destroy() {
        isListening = false
        resultBuilder.clear()
        resultCallback = null
        errorCallback = null
        try {
            recognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "destroy 异常", e)
        }
        recognizer = null
        appContext = null
    }

    /**
     * 是否正在识别
     */
    fun isRecognizing(): Boolean = isListening

    // ==================== 识别回调 ====================

    private val listener = object : RecognizerListener {

        override fun onVolumeChanged(volume: Int, data: ByteArray?) {
            // 音量变化，可用于 UI 动画
        }

        override fun onResult(result: RecognizerResult?, isLast: Boolean) {
            if (result == null) {
                Log.w(TAG, "onResult: result=null")
                return
            }

            val rawText = parseResult(result.resultString)
            // 将中文数字转换为阿拉伯数字："午餐二十九块五毛一分" → "午餐29.51"
            val text = if (rawText.isNotEmpty()) ChineseNumberConverter.convert(rawText) else rawText
            if (text.isNotEmpty()) {
                resultBuilder.append(text)
            }

            Log.i(TAG, "识别片段(原始): \"$rawText\" → (转换后): \"$text\", isLast=$isLast, 累计=\"${resultBuilder}\"")

            if (isLast) {
                isListening = false
                val finalText = resultBuilder.toString()
                resultCallback?.invoke(finalText, true)
                resultBuilder.clear()
            } else {
                resultCallback?.invoke(resultBuilder.toString(), false)
            }
        }

        override fun onEndOfSpeech() {
            Log.i(TAG, "检测到语音结束")
            isListening = false
        }

        override fun onBeginOfSpeech() {
            Log.i(TAG, "检测到语音开始")
        }

        override fun onError(error: SpeechError?) {
            isListening = false
            resultBuilder.clear()
            val desc = error?.errorDescription ?: "未知错误"
            val code = error?.errorCode ?: -1
            Log.e(TAG, "识别错误: code=$code, desc=$desc")

            // 常见错误码处理
            val msg = when (code) {
                10118 -> "您没有说话或声音太小"
                20006 -> "录音权限被拒绝"
                10411 -> "请求超时，请重试"
                else -> "识别失败: $desc ($code)"
            }
            errorCallback?.invoke(msg)
        }

        override fun onEvent(eventType: Int, arg1: Int, arg2: Int, obj: Bundle?) {
            // 事件回调，通常不需要处理
        }
    }

    /**
     * 解析讯飞 MSC 识别结果 JSON
     * 格式示例：
     * {"asr":{"ws":[{"w":[{"w":"早餐"}]},{"w":[{"w":"10元"}]}]}}
     */
    private fun parseResult(jsonStr: String?): String {
        if (jsonStr.isNullOrBlank()) return ""
        return try {
            val json = JSONObject(jsonStr)
            val asr = json.optJSONArray("asr") ?: json
            val ws = (if (asr is JSONArray) asr else null)
                ?: (json as? JSONObject)?.optJSONArray("ws")
                ?: return ""

            val sb = StringBuilder()
            for (i in 0 until ws.length()) {
                val item = ws.optJSONObject(i) ?: continue
                val cwArr = item.optJSONArray("cw") ?: continue
                // cw 数组中第一个元素通常包含最佳候选
                val cw = cwArr.optJSONObject(0) ?: continue
                val w = cw.optString("w", "")
                sb.append(w)
            }
            sb.toString()
        } catch (e: Exception) {
            Log.e(TAG, "解析识别结果失败: $jsonStr", e)
            ""
        }
    }
}
