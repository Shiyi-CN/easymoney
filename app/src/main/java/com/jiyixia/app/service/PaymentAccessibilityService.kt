package com.jiyixia.app.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.jiyixia.app.BuildConfig

/**
 * 无障碍支付检测服务（重构版）
 *
 * 改进点：
 * 1. 页面停留检测：支付成功页需稳定 3 秒才触发，避免滑过页面误触发
 * 2. 节流延长到 5 秒：避免同一页面多次触发
 * 3. 使用 ScreenParser 结构化解析：节点深度限制 + 文本过滤
 * 4. 委托给 PaymentDetector 统一处理：共享去重和分类逻辑
 *
 * 隐私保护：
 * - 只处理支付相关 app 的事件（通过包名过滤）
 * - 只在检测到支付成功关键词时读取内容
 * - 处理完立即丢弃原始节点信息
 */
class PaymentAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "PaymentA11y"

        @Volatile
        var isServiceEnabled = false
            private set

        // 事件节流：同一包名 5 秒内只处理一次内容变化事件
        // （从 2 秒延长到 5 秒，避免支付成功页动画多次触发）
        private const val THROTTLE_MS = 5_000L

        // 页面停留检测：检测到支付成功页后，等待 3 秒再次确认
        // 避免用户只是滑过支付页面就触发记录
        private const val STABLE_CHECK_DELAY_MS = 3_000L

        // 记录每个包名上次处理时间
        private var lastProcessTime = mutableMapOf<String, Long>()

        // 记录待确认的支付页面：packageName → 首次检测时间
        private var pendingPaymentPages = mutableMapOf<String, Long>()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceEnabled = true
        Log.d(TAG, "无障碍支付检测服务已启动")
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceEnabled = false
        Log.d(TAG, "无障碍支付检测服务已停止")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return

        // 严格过滤：只处理支付相关 app
        if (!PaymentDetector.isPaymentApp(packageName)) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // 页面切换，重置节流和待确认状态
                lastProcessTime.remove(packageName)
                pendingPaymentPages.remove(packageName)
                if (BuildConfig.DEBUG) Log.d(TAG, "页面切换: pkg=$packageName")
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // 节流：同一包名 5 秒内只处理一次
                val now = System.currentTimeMillis()
                val lastTime = lastProcessTime[packageName] ?: 0
                if (now - lastTime < THROTTLE_MS) return
                lastProcessTime[packageName] = now

                processCurrentScreen(packageName)
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "无障碍服务被中断")
    }

    /**
     * 处理当前屏幕内容
     *
     * 采用"页面停留检测"策略：
     * 1. 首次检测到支付成功关键词 → 记录为"待确认"，3 秒后再次检查
     * 2. 3 秒后仍在同一页面 → 确认为真实支付场景，触发记录
     * 3. 3 秒内页面切换 → 取消，避免误触发
     *
     * 这样可以避免：
     * - 用户滑过支付页面就触发记录
     * - 页面动画/倒计时多次触发
     */
    private fun processCurrentScreen(packageName: String) {
        val rootNode = rootInActiveWindow ?: return

        try {
            // 使用 ScreenParser 结构化解析（带深度限制和文本过滤）
            val content = ScreenParser.parse(rootNode, packageName)
            val allText = content.allText

            if (allText.isBlank()) return

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "屏幕文本: pkg=$packageName, text=${allText.take(200)}")
            }

            // 快速预检：必须包含支付成功关键词才继续
            // （避免每个内容变化都做完整解析）
            val paymentSuccessKeywords = listOf(
                "支付成功", "付款成功", "转账成功", "交易成功", "购买成功",
                "支付完成", "付款完成", "转账完成", "交易完成",
                "已支付", "已付款", "已扣款"
            )
            val hasPaymentSuccess = paymentSuccessKeywords.any { allText.contains(it) }
            if (!hasPaymentSuccess) return

            // 页面停留检测
            val now = System.currentTimeMillis()
            val pendingTime = pendingPaymentPages[packageName]

            if (pendingTime == null) {
                // 首次检测到支付成功页，记录为待确认
                pendingPaymentPages[packageName] = now
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "检测到支付成功页，等待 ${STABLE_CHECK_DELAY_MS}ms 确认: pkg=$packageName")
                }
                return
            }

            // 已在待确认状态，检查是否超过稳定时间
            val dwellTime = now - pendingTime
            if (dwellTime < STABLE_CHECK_DELAY_MS) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "支付页停留 ${dwellTime}ms，未达 ${STABLE_CHECK_DELAY_MS}ms，继续等待")
                }
                return
            }

            // 停留时间足够，确认为真实支付场景
            // 清除待确认状态，避免重复触发
            pendingPaymentPages.remove(packageName)

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "支付页确认，触发检测: pkg=$packageName, dwell=${dwellTime}ms")
            }

            // 委托给 PaymentDetector 处理（包含结构化解析、去重、场景识别）
            PaymentDetector.processScreen(
                rootNode = rootNode,
                packageName = packageName,
                context = applicationContext
            )

        } catch (e: Exception) {
            Log.e(TAG, "处理屏幕内容失败", e)
        } finally {
            // 立即丢弃节点信息，保护隐私
            try {
                rootNode.recycle()
            } catch (_: Exception) {
                // ignore recycle errors
            }
        }
    }
}
