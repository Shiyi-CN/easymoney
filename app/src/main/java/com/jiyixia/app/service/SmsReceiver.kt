package com.jiyixia.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.jiyixia.app.BuildConfig

/**
 * 短信接收器（重构版）
 *
 * 改进点：
 * 1. 使用 PaymentDetector.processSms 统一入口
 * 2. 移除本地关键词过滤和金额提取（由 NotificationParser 处理）
 * 3. 只负责：接收短信 → 委托给 PaymentDetector
 *
 * 隐私保护：
 * - 只处理包含支付关键词的短信（在 PaymentDetector 内过滤）
 * - 处理完立即丢弃原始短信内容
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"

        // 银行短信发送方号码前缀（用于快速过滤）
        // 非银行发送方的短信直接跳过，减少不必要的解析
        private val BANK_SENDER_PREFIXES = listOf(
            "95588", "95533", "95566", "95599", "95558", "95568", "95501",
            "95595", "95577", "95508", "95559", "95561", "95555", "95528",
            "95588", "95511", "95577", "95590"
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

            // 快速预过滤：只处理银行发送方的短信
            // （非银行短信几乎不可能是支付通知）
            val isBankSender = BANK_SENDER_PREFIXES.any { sender.contains(it) }
            if (!isBankSender) {
                if (BuildConfig.DEBUG) Log.d(TAG, "非银行发送方，跳过: $sender")
                continue
            }

            // 使用短信时间戳作为支付时间的参考
            val smsTime = message.timestampMillis?.takeIf { it > 0 }
                ?: System.currentTimeMillis()

            // 委托给 PaymentDetector 统一处理
            // （内部会做：结构化解析 → 营销过滤 → 去重 → 场景识别 → 记录）
            PaymentDetector.processSms(
                sender = sender,
                body = body,
                context = context,
                detectedTime = smsTime
            )
        }
    }
}
