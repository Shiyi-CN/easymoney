package com.jiyixia.app.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.core.app.ActivityCompat

/**
 * 语音识别管理器 —— 基于系统 Intent 弹窗模式
 *
 * 为什么不用 SpeechRecognizer 直接 API？
 * - MIUI/HyperOS 上 SpeechRecognizer 服务即使权限已授权也会报
 *   ERROR_INSUFFICIENT_PERMISSIONS，这是小米系统级限制
 * - RecognizerIntent.ACTION_RECOGNIZE_SPEECH 走系统语音输入弹窗，
 *   由系统内置引擎（小爱同学）处理，兼容性最好
 *
 * 设计要点：
 * - createRecognitionIntent() 生成配置好的 Intent，由 UI 层通过
 *   rememberLauncherForActivityResult 启动
 * - parseRecognitionResult() 从返回 Intent 中提取识别文本
 * - 内置权限请求（ActivityCompat + VoicePermissionBridge）
 */
class VoiceRecognitionManager(private val context: Context) {

    /**
     * 检查是否已授予录音权限
     */
    fun hasRecordPermission(): Boolean {
        return context.checkSelfPermission(
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 请求录音权限（走传统 ActivityCompat，兼容所有 ROM）
     * 请求结果通过 VoicePermissionBridge.result → onRequestPermissionsResult 回传
     *
     * @return true 表示已发起请求或已有权限，false 表示找不到 Activity 无法请求
     */
    fun requestRecordPermission(): Boolean {
        if (hasRecordPermission()) return true
        val activity = findActivity() ?: return false
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(android.Manifest.permission.RECORD_AUDIO),
            VoicePermissionBridge.REQUEST_CODE
        )
        return true
    }

    /**
     * 检查用户是否勾选了"不再询问"（权限被永久拒绝）
     * 必须在权限请求回调之后调用才有意义
     */
    fun isPermissionPermanentlyDenied(): Boolean {
        val activity = findActivity() ?: return false
        return !hasRecordPermission() &&
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity, android.Manifest.permission.RECORD_AUDIO
                )
    }

    /**
     * 打开应用设置页，让用户手动授予录音权限
     */
    fun openAppSettings() {
        val activity = findActivity() ?: return
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
    }

    /**
     * 从 Context 链中找到 Activity
     */
    fun findActivity(): Activity? {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    /**
     * 检查设备是否支持语音识别（是否有可用的语音引擎）
     * 使用 PackageManager 查询，兼容所有 Android 版本
     * 部分国产 ROM 可能未预装 Google 语音服务或系统引擎被禁用
     */
    fun isVoiceAvailable(): Boolean {
        return try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            context.packageManager.queryIntentActivities(
                intent, PackageManager.MATCH_DEFAULT_ONLY
            ).isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取语音引擎不可用时的用户引导文本
     * 根据品牌给出安装/开启指引
     */
    fun getUnavailableGuide(): String {
        val m = android.os.Build.MANUFACTURER.lowercase()
        val brand = android.os.Build.BRAND.lowercase()
        return when {
            m.contains("xiaomi") || m.contains("redmi") ->
                "本机暂无可用语音引擎。请确保「小爱同学」App 已安装并更新到最新版本"
            m.contains("huawei") || brand.contains("honor") ->
                "本机暂无可用语音引擎。请确保「智慧语音」服务已开启（设置→智慧助手→智慧语音）"
            m.contains("oppo") || m.contains("realme") ->
                "本机暂无可用语音引擎。请确保 Breeno 语音已开启"
            m.contains("vivo") || m.contains("iqoo") ->
                "本机暂无可用语音引擎。请确保 Jovi 语音已开启"
            m.contains("samsung") ->
                "本机暂无可用语音引擎。请确保 Google 应用已安装并登录（三星使用 Google 语音服务）"
            else ->
                "本机暂无语音识别服务。请安装 Google 应用或确保系统语音助手已启用"
        }
    }

    /**
     * 创建语音识别 Intent —— 走系统语音输入弹窗
     *
     * 关键参数：
     * - ACTION_RECOGNIZE_SPEECH：启动系统语音识别 UI
     * - LANGUAGE_MODEL_FREE_FORM：自由格式，不限词表
     * - EXTRA_LANGUAGE = "zh-CN"：中文普通话
     * - 不使用 EXTRA_PREFER_OFFLINE：MIUI 离线识别不稳定，走在线
     */
    fun createRecognitionIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "说出金额和用途，如「午餐 38 元」")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
    }

    /**
     * 从 activity result 的 Intent 中提取识别文本
     *
     * @param data onActivityResult 返回的 Intent
     * @return 识别出的文本，null 表示无结果
     */
    fun parseRecognitionResult(data: Intent?): String? {
        val matches = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        return matches?.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    /**
     * 释放资源（Intent 模式无持久资源，保留用于 API 兼容）
     */
    fun destroy() {
        // Intent 模式无需手动释放资源
    }
}
