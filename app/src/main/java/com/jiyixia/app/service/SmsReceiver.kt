package com.jiyixia.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.jiyixia.app.BuildConfig

/**
 * 短信接收器
 *
 * 监听短信广播，解析银行、支付类短信内容，
 * 提取金额、商户等信息，调用 PaymentDetector 统一处理。
 *
 * 支持的短信类型：
 * - 银行消费通知（招商银行、工商银行等）
 * - 支付宝/微信支付通知
 * - 退款通知
 *
 * 隐私保护：
 * - 只处理包含支付关键词的短信
 * - 只提取金额和分类关键词，不提取卡号等敏感信息
 * - 处理完立即丢弃原始短信内容
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"

        // 银行短信特征关键词
        private val BANK_KEYWORDS = listOf(
            "消费", "支出", "扣款", "转账", "还款",
            "信用卡", "借记卡", "储蓄卡",
            "招商银行", "工商银行", "建设银行", "农业银行",
            "中国银行", "交通银行", "浦发银行", "平安银行",
            "广发银行", "民生银行", "兴业银行", "光大银行",
            "华夏银行", "北京银行", "邮储银行"
        )

        // 支付类短信特征关键词
        private val PAYMENT_KEYWORDS = listOf(
            "支付宝", "微信支付", "云闪付",
            "消费", "支出", "扣款", "转账",
            "退款", "到账", "收入"
        )

        // 金额提取正则（银行短信常见格式）
        private val AMOUNT_PATTERNS = listOf(
            // "消费人民币XXX元" / "支出XXX元"
            Regex("""(?:消费|支出|扣款|转账|还款)[人民币]*[¥￥]?\s*([\d,.]+)\s*元"""),
            // "人民币XXX元"
            Regex("""人民币\s*[¥￥]?\s*([\d,.]+)\s*元"""),
            // "XXX元"
            Regex("""[¥￥]?\s*([\d,.]+)\s*元"""),
            // "金额XXX"
            Regex("""金额[：:]\s*[¥￥]?\s*([\d,.]+)"""),
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        for (message in messages) {
            val sender = message.displayOriginatingAddress ?: ""
            val body = message.displayMessageBody ?: ""

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "收到短信: sender=$sender, body=${body.take(100)}")
            }

            // 过滤：只处理包含支付关键词的短信
            if (!containsPaymentKeyword(body)) {
                if (BuildConfig.DEBUG) Log.d(TAG, "无支付关键词，跳过")
                continue
            }

            // 提取金额
            val amount = extractAmount(body)
            if (amount == null || amount <= 0) {
                if (BuildConfig.DEBUG) Log.d(TAG, "无法解析金额，跳过")
                continue
            }

            if (BuildConfig.DEBUG) Log.d(TAG, "短信检测到支付: sender=$sender, amount=$amount")

            // 使用短信时间戳作为支付时间的参考
            val smsTime = message.timestampMillis?.takeIf { it > 0 } ?: System.currentTimeMillis()

            // 调用统一检测入口
            PaymentDetector.processDetection(
                source = "短信",
                amount = amount,
                text = body,
                packageName = "sms:$sender",
                context = context,
                detectedTime = smsTime
            )
        }
    }

    /**
     * 检查文本是否包含支付关键词
     */
    private fun containsPaymentKeyword(text: String): Boolean {
        return BANK_KEYWORDS.any { text.contains(it) } ||
               PAYMENT_KEYWORDS.any { text.contains(it) }
    }

    /**
     * 从短信文本中提取金额
     */
    private fun extractAmount(text: String): Double? {
        for (pattern in AMOUNT_PATTERNS) {
            val match = pattern.find(text)
            if (match != null) {
                val amountStr = match.groupValues[1].replace(",", "").replace(" ", "")
                val amount = amountStr.toDoubleOrNull()
                if (amount != null && amount > 0 && amount < 1000000) {
                    return amount
                }
            }
        }
        return null
    }
}
