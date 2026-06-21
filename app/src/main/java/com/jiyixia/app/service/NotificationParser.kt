package com.jiyixia.app.service

import android.app.Notification
import android.util.Log

/**
 * 结构化通知解析器
 *
 * 解决"乱识别"问题的核心：从通知的结构化字段提取信息，而不是整段文本正则匹配。
 *
 * 设计原理：
 * 1. 微信支付/支付宝/银行 app 的通知都有固定模板
 * 2. 通知的 title 通常是商户名或"微信支付"等官方账号
 * 3. 通知的 text 通常是金额和场景描述
 * 4. 通知的 bigText 通常是详细信息（含商户名、时间）
 *
 * 解析流程：
 * 1. 提取 title / subText / text / bigText / textLines 分别保存
 * 2. 识别通知模板（微信支付/支付宝/银行）
 * 3. 从对应模板的字段提取金额和商户名
 * 4. 拒绝营销通知（含"优惠券"、"红包"、"活动"等词）
 */
object NotificationParser {

    private const val TAG = "NotificationParser"

    /**
     * 结构化通知内容
     */
    data class NotificationContent(
        val title: String,           // 通知标题（通常是商户名或官方账号）
        val subText: String,         // 副标题
        val text: String,            // 主文本（通常是金额摘要）
        val bigText: String,         // 大文本（详细信息）
        val summaryText: String,     // 摘要
        val textLines: List<String>, // 多行文本
        val packageName: String,     // 来源包名
        val allText: String          // 拼接后的完整文本（供 SmartParseUseCase 使用）
    )

    /**
     * 解析结果
     */
    data class ParsedNotification(
        val amount: Double,          // 提取到的金额
        val merchantName: String,    // 商户名（可能为空）
        val scene: String,           // 场景描述（如"微信支付"、"支付宝转账"）
        val isMarketing: Boolean,    // 是否为营销通知（应拒绝）
        val content: NotificationContent // 原始结构化内容
    )

    /** 营销通知特征词（出现这些词应拒绝识别） */
    private val MARKETING_KEYWORDS = listOf(
        "优惠券", "折扣", "满减", "满赠", "限时", "抢购", "特惠", "特价",
        "活动", "促销", "打折", "秒杀", "领红包", "抢红包", "红包雨",
        "积分兑换", "签到", "抽奖", "中奖", "免费领", "0元购", "0元领",
        "为你推荐", "猜你喜欢", "热门活动", "立即查看",
        "余额宝收益", "基金推荐", "理财产品", "保险推荐"
    )

    /** 微信支付通知模板特征 */
    private val WECHAT_PAY_TITLES = setOf("微信支付", "微信转账", "微信红包")

    /** 支付宝通知模板特征 */
    private val ALIPAY_TITLES = setOf("支付宝", "支付宝通知", "支付宝账单", "蚂蚁财富", "余额宝")

    /** 银行短信发送方特征 */
    private val BANK_SMS_SENDERS = setOf(
        "95588", "95533", "95566", "95599", "95558", "95568", "95501",
        "95595", "95577", "95508", "95559", "95561", "95555", "95528"
    )

    /**
     * 从 Notification 提取结构化内容
     */
    fun parse(notification: Notification, packageName: String): NotificationContent {
        val extras = notification.extras

        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val subText = extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val summaryText = extras?.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString() ?: ""

        @Suppress("DEPRECATION")
        val textLinesArray = extras?.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        val textLines = textLinesArray?.map { it.toString() } ?: emptyList()

        // 拼接完整文本（供 SmartParseUseCase 分类使用）
        val allText = listOf(title, subText, text, bigText, summaryText)
            .plus(textLines)
            .filter { it.isNotBlank() }
            .joinToString(" ")

        return NotificationContent(
            title = title,
            subText = subText,
            text = text,
            bigText = bigText,
            summaryText = summaryText,
            textLines = textLines,
            packageName = packageName,
            allText = allText
        )
    }

    /**
     * 解析短信内容（结构化）
     *
     * @param sender 短信发送方
     * @param body 短信正文
     * @return 结构化内容
     */
    fun parseSms(sender: String, body: String): NotificationContent {
        return NotificationContent(
            title = sender,
            subText = "",
            text = body,
            bigText = "",
            summaryText = "",
            textLines = emptyList(),
            packageName = "sms:$sender",
            allText = "$sender $body"
        )
    }

    /**
     * 深度解析通知：提取金额、商户名、识别场景
     *
     * @param content 结构化通知内容
     * @return 解析结果，如果无法解析返回 null
     */
    fun extractTransaction(content: NotificationContent): ParsedNotification? {
        // 1. 营销通知过滤（最高优先级）
        if (isMarketing(content)) {
            Log.d(TAG, "营销通知拒绝: title=${content.title}, text=${content.text.take(50)}")
            return null
        }

        // 2. 根据包名选择解析模板
        val template = when {
            content.packageName == "com.tencent.mm" -> parseWeChatPay(content)
            content.packageName == "com.eg.android.AlipayGphone" -> parseAlipay(content)
            content.packageName.startsWith("sms:") -> parseBankSms(content)
            else -> parseGeneric(content)
        }

        if (template == null || template.amount <= 0) {
            return null
        }

        return template
    }

    /**
     * 营销通知检测
     *
     * 关键：营销通知通常包含"领红包"、"优惠券"等词，且金额往往很小（¥0.01、¥0.88）
     * 但要注意：真正的"退款到账"也可能金额小，需要结合场景判断
     */
    private fun isMarketing(content: NotificationContent): Boolean {
        val checkText = content.allText

        // 明确的营销词
        if (MARKETING_KEYWORDS.any { checkText.contains(it) }) {
            // 例外：如果同时包含"退款到账"或"退货"，则不是营销
            val isRefund = checkText.contains("退款") || checkText.contains("退货")
            if (!isRefund) return true
        }

        return false
    }

    /**
     * 微信支付通知解析
     *
     * 模板示例：
     * - title: "微信支付"
     * - text: "¥38.00"
     * - bigText: "商户：星巴克\n金额：¥38.00\n时间：2026-06-21 12:30"
     */
    private fun parseWeChatPay(content: NotificationContent): ParsedNotification? {
        // 微信支付的 title 必须是官方账号
        val isOfficial = WECHAT_PAY_TITLES.any { content.title.contains(it) }
        if (!isOfficial) return null

        // 从 bigText 或 text 提取金额
        val amountText = content.bigText.ifBlank { content.text }
        val amount = extractAmountStrict(amountText) ?: return null

        // 从 bigText 提取商户名
        val merchantName = extractMerchantFromWeChat(content.bigText)

        // 场景判断
        val scene = when {
            content.allText.contains("转账") -> "微信转账"
            content.allText.contains("红包") -> "微信红包"
            content.allText.contains("退款") -> "微信退款"
            else -> "微信支付"
        }

        return ParsedNotification(
            amount = amount,
            merchantName = merchantName,
            scene = scene,
            isMarketing = false,
            content = content
        )
    }

    /**
     * 支付宝通知解析
     *
     * 模板示例：
     * - title: "支付宝"
     * - text: "在星巴克消费¥38.00"
     */
    private fun parseAlipay(content: NotificationContent): ParsedNotification? {
        val isOfficial = ALIPAY_TITLES.any { content.title.contains(it) }
        if (!isOfficial) return null

        // 支付宝的金额通常在 text 中
        val amountText = content.text.ifBlank { content.bigText }
        val amount = extractAmountStrict(amountText) ?: return null

        // 从 text 提取商户名（"在XXX消费"、"XXX-付款"）
        val merchantName = extractMerchantFromAlipay(content.text, content.bigText)

        val scene = when {
            content.allText.contains("转账") -> "支付宝转账"
            content.allText.contains("退款") -> "支付宝退款"
            content.allText.contains("收款") -> "支付宝收款"
            else -> "支付宝支付"
        }

        return ParsedNotification(
            amount = amount,
            merchantName = merchantName,
            scene = scene,
            isMarketing = false,
            content = content
        )
    }

    /**
     * 银行短信解析
     *
     * 模板示例：
     * "【招商银行】您尾号1234信用卡于06月21日12:30在星巴克消费人民币38.00元"
     */
    private fun parseBankSms(content: NotificationContent): ParsedNotification? {
        val body = content.text

        // 银行短信必须包含明确的消费/扣款词
        val hasBankAction = listOf(
            "消费", "扣款", "支出", "转账", "还款", "存入", "到账", "入账"
        ).any { body.contains(it) }

        if (!hasBankAction) return null

        val amount = extractAmountStrict(body) ?: return null

        // 提取商户名（"在XXX消费"、"XXX-")
        val merchantName = extractMerchantFromBankSms(body)

        val scene = when {
            body.contains("存入") || body.contains("到账") || body.contains("入账") -> "银行到账"
            body.contains("退款") -> "银行退款"
            body.contains("还款") -> "银行还款"
            else -> "银行消费"
        }

        return ParsedNotification(
            amount = amount,
            merchantName = merchantName,
            scene = scene,
            isMarketing = false,
            content = content
        )
    }

    /**
     * 通用通知解析（其他支付 app）
     */
    private fun parseGeneric(content: NotificationContent): ParsedNotification? {
        // 必须包含支付确认词
        val hasPaymentConfirm = listOf(
            "支付成功", "付款成功", "转账成功", "交易成功",
            "已支付", "已付款", "消费成功", "扣款成功"
        ).any { content.allText.contains(it) }

        if (!hasPaymentConfirm) return null

        val amount = extractAmountStrict(content.allText) ?: return null

        // 商户名通常在 title
        val merchantName = if (content.title.isNotBlank() &&
            !content.title.contains("支付") && !content.title.contains("通知")) {
            content.title
        } else ""

        return ParsedNotification(
            amount = amount,
            merchantName = merchantName,
            scene = "支付",
            isMarketing = false,
            content = content
        )
    }

    // ═══════════════════════════════════════════════════════════
    //  严格金额提取（只提取明确的金额，避免营销文案误匹配）
    // ═══════════════════════════════════════════════════════════

    /**
     * 严格金额提取
     *
     * 与旧的 extractAmount 不同，这里只匹配明确的金额格式：
     * 1. ¥/￥ 符号 + 数字（最可靠）
     * 2. "金额：XXX" / "金额 XXX"
     * 3. "消费/支出/扣款/转账/支付 + 数字 + 元"
     *
     * 不匹配：
     * - 纯数字（容易匹配到订单号、时间等）
     * - "X元"（容易匹配到"满100元减50元"）
     */
    private fun extractAmountStrict(text: String): Double? {
        // 优先级 1：¥/￥ 符号 + 数字（最可靠）
        val yenPattern = Regex("""[¥￥]\s*(\d+(?:\.\d{1,2})?)""")
        yenPattern.find(text)?.let { match ->
            val amount = match.groupValues[1].toDoubleOrNull()
            if (amount != null && amount > 0 && amount < 1_000_000) return amount
        }

        // 优先级 2："金额：XXX" / "金额 XXX"
        val amountLabelPattern = Regex("""金额[：:\s]*[¥￥]?\s*(\d+(?:\.\d{1,2})?)""")
        amountLabelPattern.find(text)?.let { match ->
            val amount = match.groupValues[1].toDoubleOrNull()
            if (amount != null && amount > 0 && amount < 1_000_000) return amount
        }

        // 优先级 3：动作词 + 数字 + 元（银行短信常见）
        val actionPattern = Regex(
            """(?:消费|支出|扣款|转账|支付|还款|存入|到账|入账|退款)[^\d]{0,10}(\d+(?:\.\d{1,2})?)\s*元"""
        )
        actionPattern.find(text)?.let { match ->
            val amount = match.groupValues[1].toDoubleOrNull()
            if (amount != null && amount > 0 && amount < 1_000_000) return amount
        }

        // 优先级 4：人民币 + 数字 + 元
        val rmbPattern = Regex("""人民币\s*(\d+(?:\.\d{1,2})?)\s*元""")
        rmbPattern.find(text)?.let { match ->
            val amount = match.groupValues[1].toDoubleOrNull()
            if (amount != null && amount > 0 && amount < 1_000_000) return amount
        }

        return null
    }

    // ═══════════════════════════════════════════════════════════
    //  商户名提取
    // ═══════════════════════════════════════════════════════════

    /** 微信支付 bigText 中的商户名提取 */
    private fun extractMerchantFromWeChat(bigText: String): String {
        if (bigText.isBlank()) return ""

        // 模式1："商户：XXX" / "商户 XXX" / "付款给XXX" / "向XXX付款"
        val patterns = listOf(
            Regex("""(?:商户|商家|对方)[：:\s]*([^\n\s,，。]{2,20})"""),
            Regex("""付款给\s*([^\n\s,，。]{2,20})"""),
            Regex("""向\s*([^\n\s,，。]{2,20})\s*付款"""),
            Regex("""转账给\s*([^\n\s,，。]{2,20})""")
        )

        for (pattern in patterns) {
            val match = pattern.find(bigText)
            if (match != null) {
                val name = match.groupValues[1].trim()
                // 过滤明显不是商户名的词
                if (name.length >= 2 && !isNonMerchantWord(name)) {
                    return name
                }
            }
        }
        return ""
    }

    /** 支付宝通知中的商户名提取 */
    private fun extractMerchantFromAlipay(text: String, bigText: String): String {
        val source = bigText.ifBlank { text }
        if (source.isBlank()) return ""

        // 模式1："在XXX消费" / "在XXX付款"
        val patterns = listOf(
            Regex("""在\s*([^\n\s,，。]{2,20})\s*(?:消费|付款|支付)"""),
            Regex("""(?:商户|商家)[：:\s]*([^\n\s,，。]{2,20})"""),
            Regex("""付款给\s*([^\n\s,，。]{2,20})"""),
            Regex("""([^\n\s,，。]{2,20})\s*[-－]\s*(?:消费|付款|支付)""")
        )

        for (pattern in patterns) {
            val match = pattern.find(source)
            if (match != null) {
                val name = match.groupValues[1].trim()
                if (name.length >= 2 && !isNonMerchantWord(name)) {
                    return name
                }
            }
        }
        return ""
    }

    /** 银行短信中的商户名提取 */
    private fun extractMerchantFromBankSms(body: String): String {
        if (body.isBlank()) return ""

        // 模式："在XXX消费" / "在XXX刷卡" / "XXX-"
        val patterns = listOf(
            Regex("""在\s*([^\n\s,，。]{2,20})\s*(?:消费|刷卡|支付|付款)"""),
            Regex("""商户[：:\s]*([^\n\s,，。]{2,20})""")
        )

        for (pattern in patterns) {
            val match = pattern.find(body)
            if (match != null) {
                val name = match.groupValues[1].trim()
                if (name.length >= 2 && !isNonMerchantWord(name)) {
                    return name
                }
            }
        }
        return ""
    }

    /** 非商户名词过滤 */
    private fun isNonMerchantWord(word: String): Boolean {
        val nonMerchant = setOf(
            "微信", "支付宝", "余额宝", "零钱", "银行卡", "信用卡",
            "招商银行", "工商银行", "建设银行", "中国银行", "农业银行",
            "交通银行", "浦发银行", "平安银行", "广发银行", "民生银行",
            "兴业银行", "光大银行", "华夏银行", "北京银行", "邮储银行",
            "消费", "支出", "扣款", "转账", "支付", "还款", "存入",
            "成功", "失败", "完成", "确认", "取消"
        )
        return word in nonMerchant
    }
}
