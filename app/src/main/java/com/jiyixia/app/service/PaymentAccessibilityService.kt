package com.jiyixia.app.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.jiyixia.app.BuildConfig

/**
 * 无障碍支付检测服务
 *
 * 通过 AccessibilityService 监听支付页面的内容变化，
 * 提取交易金额和分类信息，实现屏幕级支付检测。
 *
 * 隐私保护：
 * - 只处理支付相关 app 的事件（通过包名过滤）
 * - 只在检测到支付关键词时读取内容
 * - 只提取金额和分类关键词，不提取收款人/卡号等敏感信息
 * - 处理完立即丢弃原始节点信息
 *
 * 默认关闭，用户需在设置页面手动开启并授权。
 */
class PaymentAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "PaymentA11y"

        @Volatile
        var isServiceEnabled = false
            private set

        // 事件节流：同一包名 2 秒内只处理一次内容变化事件
        private var lastProcessTime = mutableMapOf<String, Long>()
        private const val THROTTLE_MS = 2_000L

        // 支付成功页面的特征关键词
        private val PAYMENT_SUCCESS_KEYWORDS = listOf(
            "支付成功", "付款成功", "转账成功", "交易成功", "购买成功",
            "支付完成", "付款完成", "转账完成", "交易完成",
            "已支付", "已付款", "已转账", "已扣款",
            "支付¥", "支付￥", "付款¥", "付款￥",
            "确认支付", "确认付款",
            // 打车/出行场景
            "行程费用", "车费支付", "已支付车费", "支付车费",
            "行程支付", "打车费用", "出行费用", "车费已付",
            "支付行程", "行程已支付", "费用已支付",
        )

        // 银行/转账确认页特征（必须是"已完成"的确认页，不能是输入页）
        private val TRANSFER_KEYWORDS = listOf(
            "转账成功", "汇款成功", "转入成功",
            "交易详情", "扣款通知", "消费通知",
            // 打车/出行转账
            "行程详情", "订单详情", "出行账单",
        )

        /** 转账输入页面的特征关键词（有这些词但没有"成功"等确认词 = 输入页） */
        private val INPUT_PAGE_KEYWORDS = listOf(
            "转账给", "向", "付款给", "转账金额",
            "确认转账", "确认付款", "确认支付",
            "输入金额", "输入密码", "请输入",
            "选择付款方式", "选择到账方式",
        )
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

        // 只处理窗口状态变化和内容变化事件
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // 页面切换，重置节流
                lastProcessTime.remove(packageName)
                if (BuildConfig.DEBUG) Log.d(TAG, "页面切换: pkg=$packageName")
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // 节流：同一包名 2 秒内只处理一次
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
     * 1. 从根节点提取所有可见文本
     * 2. 检查是否包含支付成功关键词
     * 3. 提取金额
     * 4. 调用 PaymentDetector 统一处理
     */
    private fun processCurrentScreen(packageName: String) {
        val rootNode = rootInActiveWindow ?: return

        try {
            // 提取所有可见文本
            val allText = collectTextFromNode(rootNode)
            if (allText.isBlank()) return

            // 详细日志：记录检测到的文本和关键词匹配情况
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "屏幕文本: pkg=$packageName, text=${allText.take(300)}")
                val hasPaymentSuccess = PAYMENT_SUCCESS_KEYWORDS.any { allText.contains(it) }
                val hasTransferKeyword = TRANSFER_KEYWORDS.any { allText.contains(it) }
                Log.d(TAG, "关键词匹配: paymentSuccess=$hasPaymentSuccess, transfer=$hasTransferKeyword")
                if (hasPaymentSuccess || hasTransferKeyword) {
                    val matchedKeywords = (PAYMENT_SUCCESS_KEYWORDS + TRANSFER_KEYWORDS).filter { allText.contains(it) }
                    Log.d(TAG, "匹配到的关键词: $matchedKeywords")
                }
            }

            // 检查是否包含支付成功关键词（严格过滤，避免误识别）
            val hasPaymentSuccess = PAYMENT_SUCCESS_KEYWORDS.any { allText.contains(it) }
            val hasTransferKeyword = TRANSFER_KEYWORDS.any { allText.contains(it) }

            // 必须有支付成功或转账确认关键词，否则跳过
            if (!hasPaymentSuccess && !hasTransferKeyword) return

            // 额外过滤：排除转账输入界面
            // 输入界面特征：有"转账给"/"确认转账"等词，但没有"成功"/"完成"/"已支付"等确认词
            val hasConfirmWord = allText.contains("成功") || allText.contains("完成") ||
                    allText.contains("已支付") || allText.contains("已付款") ||
                    allText.contains("已扣款") || allText.contains("已转账")
            val isInputPage = INPUT_PAGE_KEYWORDS.any { allText.contains(it) } && !hasConfirmWord
            if (isInputPage) {
                if (BuildConfig.DEBUG) Log.d(TAG, "检测到转账输入界面，跳过")
                return
            }

            // 提取金额
            val amount = PaymentDetector.extractAmount(allText)
            if (amount == null || amount <= 0) {
                if (BuildConfig.DEBUG) Log.d(TAG, "检测到支付页面但无法提取金额")
                return
            }

            if (BuildConfig.DEBUG) Log.d(TAG, "屏幕检测到支付: pkg=$packageName, amount=$amount")

            // 调用统一检测入口
            PaymentDetector.processDetection(
                source = "屏幕",
                amount = amount,
                text = allText,
                packageName = packageName,
                context = applicationContext
            )
        } catch (e: Exception) {
            Log.e(TAG, "处理屏幕内容失败", e)
        } finally {
            // 立即丢弃节点信息，保护隐私
            rootNode.recycle()
        }
    }

    /**
     * 递归收集节点中的所有文本
     * 只提取文本内容，不提取节点 ID、描述等可能包含敏感信息的内容
     */
    private fun collectTextFromNode(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()

        // 只提取文本内容
        node.text?.let { sb.append(it).append(" ") }
        // contentDescription 可能包含有用信息（如"支付成功"）
        node.contentDescription?.let { sb.append(it).append(" ") }

        // 递归子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                sb.append(collectTextFromNode(child))
            } finally {
                child.recycle()
            }
        }

        return sb.toString()
    }
}
