package com.jiyixia.app.service

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.jiyixia.app.JiYiXiaApp
import com.jiyixia.app.data.entity.Record
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PaymentNotificationListener : NotificationListenerService() {

    companion object {
        // 支付关键词 → 分类映射
        private val MERCHANT_RULES = mapOf(
            "餐饮" to listOf("外卖", "餐厅", "火锅", "奶茶", "咖啡", "快餐", "小吃", "食堂", "美团外卖", "饿了么", "肯德基", "麦当劳", "星巴克", "瑞幸"),
            "交通" to listOf("打车", "地铁", "加油", "停车", "公交", "滴滴", "高德", "哈啰", "青桔"),
            "购物" to listOf("超市", "便利店", "百货", "淘宝", "京东", "拼多多", "全家", "711", "罗森"),
            "娱乐" to listOf("电影", "游戏", "KTV", "酒吧", "门票"),
            "居住" to listOf("房租", "水电", "物业", "燃气", "宽带"),
            "医疗" to listOf("医院", "药", "体检", "诊所"),
            "教育" to listOf("课程", "培训", "书", "学费"),
        )

        private val AMOUNT_REGEX = Regex("""(?:支出|消费|付款|扣款|支付)[^\d]*([\d,.]+)元""", RegexOption.IGNORE_CASE)
        private val AMOUNT_REGEX2 = Regex("""¥([\d,.]+)""")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName ?: return

        // 只处理支付类通知
        if (!isPaymentApp(packageName)) return

        val notification = sbn.notification ?: return
        val text = extractText(notification) ?: return

        // 解析金额
        val amount = parseAmount(text) ?: return
        if (amount <= 0) return

        // 解析商户 → 分类
        val (categoryName, confidence) = matchCategory(text)

        // 保存到数据库
        val app = applicationContext as JiYiXiaApp
        CoroutineScope(Dispatchers.IO).launch {
            val db = app.database
            val categories = db.categoryDao().getAll().first()
            val category = categories.find { it.name == categoryName } ?: categories.find { it.name == "其他" }

            if (category != null) {
                val isPending = confidence < 80
                db.recordDao().insert(
                    Record(
                        type = 0, // 支出
                        amount = amount,
                        categoryId = category.id,
                        note = text.take(50),
                        date = System.currentTimeMillis(),
                        isPendingConfirm = isPending,
                        confidence = confidence
                    )
                )
            }
        }
    }

    private fun isPaymentApp(packageName: String): Boolean {
        return packageName in setOf(
            "com.eg.android.AlipayGphone",  // 支付宝
            "com.tencent.mm",                // 微信
            "com.icbc",                      // 工商银行
            "com.cmbchina",                  // 招商银行
            "com.chinamworld.mainapp",       // 建设银行
            "com.bankcomm.Bankcomm",         // 交通银行
            "com.spdbccc.app",               // 浦发银行
        )
    }

    private fun extractText(notification: Notification): String? {
        val extras = notification.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        return "$title $text".ifBlank { null }
    }

    private fun parseAmount(text: String): Double? {
        AMOUNT_REGEX.find(text)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
            ?.let { return it }
        AMOUNT_REGEX2.find(text)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
            ?.let { return it }
        return null
    }

    private fun matchCategory(text: String): Pair<String, Int> {
        for ((category, keywords) in MERCHANT_RULES) {
            for (keyword in keywords) {
                if (text.contains(keyword)) return category to 90
            }
        }
        return "其他" to 50  // 低置信度，标记为待确认
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
    }
}
