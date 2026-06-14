package com.jiyixia.app.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局崩溃处理器
 * 捕获未处理异常，将崩溃日志保存到本地文件
 */
class CrashHandler private constructor() : Thread.UncaughtExceptionHandler {

    private lateinit var context: Context
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    /**
     * 初始化，注册为全局崩溃处理器
     */
    fun init(context: Context) {
        this.context = context.applicationContext
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            // 保存崩溃日志
            saveCrashLog(thread, throwable)
        } catch (e: Exception) {
            // 日志保存失败，忽略
        }

        // 调用系统默认处理器（通常会杀死进程）
        defaultHandler?.uncaughtException(thread, throwable)
    }

    /**
     * 保存崩溃日志到文件
     */
    private fun saveCrashLog(thread: Thread, throwable: Throwable) {
        val crashDir = File(context.filesDir, "crash")
        if (!crashDir.exists()) {
            crashDir.mkdirs()
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val crashFile = File(crashDir, "crash_${timestamp}.txt")

        val log = buildString {
            // 时间
            appendLine("=== 崩溃日志 ===")
            appendLine("时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            appendLine("线程: ${thread.name}")
            appendLine()

            // 设备信息
            appendLine("=== 设备信息 ===")
            appendLine("品牌: ${Build.BRAND}")
            appendLine("型号: ${Build.MODEL}")
            appendLine("系统版本: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine()

            // APP 版本
            appendLine("=== 应用信息 ===")
            try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                appendLine("版本名: ${packageInfo.versionName}")
                appendLine("版本号: ${packageInfo.versionCode}")
            } catch (e: PackageManager.NameNotFoundException) {
                appendLine("版本信息获取失败")
            }
            appendLine()

            // 异常堆栈
            appendLine("=== 异常堆栈 ===")
            val stringWriter = StringWriter()
            throwable.printStackTrace(PrintWriter(stringWriter))
            appendLine(stringWriter.toString())
        }

        crashFile.writeText(log)
    }

    companion object {
        @JvmStatic
        fun getInstance(): CrashHandler {
            return Holder.instance
        }

        private object Holder {
            val instance = CrashHandler()
        }

        /**
         * 获取崩溃日志目录
         */
        fun getCrashDir(context: Context): File {
            return File(context.filesDir, "crash")
        }

        /**
         * 获取所有崩溃日志文件，按时间倒序
         */
        fun getCrashLogs(context: Context): List<File> {
            val crashDir = getCrashDir(context)
            if (!crashDir.exists()) return emptyList()
            return crashDir.listFiles { file -> file.name.startsWith("crash_") }
                ?.sortedByDescending { it.name }
                ?: emptyList()
        }

        /**
         * 清空所有崩溃日志
         */
        fun clearCrashLogs(context: Context) {
            val crashDir = getCrashDir(context)
            if (crashDir.exists()) {
                crashDir.listFiles()?.forEach { it.delete() }
            }
        }
    }
}
