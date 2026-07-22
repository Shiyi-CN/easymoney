package com.jiyixia.app.service

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 结构化屏幕解析器
 *
 * 解决"屏幕检测乱识别"问题：
 * 1. 节点深度限制（避免递归过深）
 * 2. 节点类型过滤（只取有意义的文本）
 * 3. 文本长度限制（避免收集广告位长文本）
 * 4. 商户名/金额结构化提取
 */
object ScreenParser {

    private const val TAG = "ScreenParser"

    /** 最大递归深度（避免无限递归） */
    private const val MAX_DEPTH = 8

    /** 单节点文本最大长度（避免广告位长文本干扰） */
    private const val MAX_NODE_TEXT_LENGTH = 200

    /** 屏幕总文本最大长度（避免内存爆炸） */
    private const val MAX_TOTAL_TEXT_LENGTH = 5000

    /**
     * 屏幕解析结果
     */
    data class ScreenContent(
        val allText: String,           // 拼接后的完整文本
        val textBlocks: List<String>,  // 独立文本块（按节点分割）
        val packageName: String
    )

    /**
     * 从根节点提取结构化屏幕内容
     *
     * 改进点：
     * 1. 深度限制：超过 MAX_DEPTH 不再递归
     * 2. 文本过滤：跳过空文本、过长文本（广告位）
     * 3. 总长度限制：超过上限停止收集
     */
    fun parse(rootNode: AccessibilityNodeInfo, packageName: String): ScreenContent {
        val textBlocks = mutableListOf<String>()
        val sb = StringBuilder()

        collectTexts(rootNode, 0, textBlocks, sb)

        return ScreenContent(
            allText = sb.toString().trim(),
            textBlocks = textBlocks,
            packageName = packageName
        )
    }

    /**
     * 递归收集文本（带深度限制和长度过滤）
     */
    private fun collectTexts(
        node: AccessibilityNodeInfo,
        depth: Int,
        textBlocks: MutableList<String>,
        sb: StringBuilder
    ) {
        // 深度限制
        if (depth > MAX_DEPTH) return

        // 总长度限制
        if (sb.length > MAX_TOTAL_TEXT_LENGTH) return

        // 提取当前节点的文本
        node.text?.let { text ->
            val textStr = text.toString().trim()
            if (textStr.isNotBlank() && textStr.length <= MAX_NODE_TEXT_LENGTH) {
                textBlocks.add(textStr)
                sb.append(textStr).append(" ")
            }
        }

        // contentDescription 只在深度较浅时提取（避免广告位）
        if (depth <= 3) {
            node.contentDescription?.let { desc ->
                val descStr = desc.toString().trim()
                if (descStr.isNotBlank() && descStr.length <= MAX_NODE_TEXT_LENGTH &&
                    textBlocks.none { it == descStr }) {
                    textBlocks.add(descStr)
                    sb.append(descStr).append(" ")
                }
            }
        }

        // 递归子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                collectTexts(child, depth + 1, textBlocks, sb)
            } finally {
                child.recycle()
            }
        }
    }

    /**
     * 从屏幕内容提取交易信息
     *
     * 与 NotificationParser.extractTransaction 类似，但针对屏幕特点：
     * 1. 屏幕文本更杂乱，需要更严格的支付确认词过滤
     * 2. 商户名通常在特定位置（如页面顶部标题）
     *
     * @param content 屏幕内容
     * @return 解析结果，无法解析返回 null
     */
    fun extractTransaction(content: ScreenContent): NotificationParser.ParsedNotification? {
        val allText = content.allText
        if (allText.isBlank()) return null

        // 1. 营销内容过滤
        val marketingKeywords = listOf(
            "优惠券", "折扣", "满减", "限时", "抢购", "特惠",
            "活动", "促销", "秒杀", "领红包", "红包雨",
            "积分兑换", "签到", "抽奖", "免费领",
            "为你推荐", "猜你喜欢"
        )
        if (marketingKeywords.any { allText.contains(it) }) {
            // 例外：退款场景
            val isRefund = allText.contains("退款") || allText.contains("退货")
            if (!isRefund) {
                Log.d(TAG, "屏幕含营销词，跳过")
                return null
            }
        }

        // 2. 必须包含支付成功确认词（屏幕检测比通知更严格）
        val paymentConfirmKeywords = listOf(
            "支付成功", "付款成功", "转账成功", "交易成功", "购买成功",
            "支付完成", "付款完成", "转账完成", "交易完成",
            "已支付", "已付款", "已扣款"
        )
        val hasPaymentSuccess = paymentConfirmKeywords.any { allText.contains(it) }
        if (!hasPaymentSuccess) {
            return null
        }

        // 3. 排除转账输入界面
        val inputPageKeywords = listOf(
            "转账给", "付款给", "输入金额", "输入密码", "请输入",
            "选择付款方式", "选择到账方式", "确认转账", "确认付款"
        )
        val hasConfirmWord = allText.contains("成功") || allText.contains("完成") ||
                allText.contains("已支付") || allText.contains("已付款")
        val isInputPage = inputPageKeywords.any { allText.contains(it) } && !hasConfirmWord
        if (isInputPage) {
            Log.d(TAG, "检测到输入界面，跳过")
            return null
        }

        // 4. 严格金额提取
        val amount = extractAmountStrict(allText) ?: run {
            Log.d(TAG, "屏幕检测到支付页但无法提取金额")
            return null
        }

        // 5. 商户名提取（从文本块中找）
        val merchantName = extractMerchantFromScreen(content.textBlocks, allText)

        // 6. 场景判断
        val scene = when {
            allText.contains("转账") -> "屏幕转账"
            allText.contains("退款") -> "屏幕退款"
            allText.contains("行程") || allText.contains("车费") -> "屏幕出行"
            else -> "屏幕支付"
        }

        return NotificationParser.ParsedNotification(
            amount = amount,
            merchantName = merchantName,
            scene = scene,
            isMarketing = false,
            content = NotificationParser.NotificationContent(
                title = content.textBlocks.firstOrNull() ?: "",
                subText = "",
                text = allText,
                bigText = "",
                summaryText = "",
                textLines = content.textBlocks,
                packageName = content.packageName,
                allText = allText
            )
        )
    }

    /**
     * 严格金额提取（与 NotificationParser 一致）
     *
     * 改进：
     * 1. ¥/￥ 符号后取最大金额（避免"红包抵扣¥0.01"取到 0.01）
     * 2. 支持"实付¥XXX"等动作词+¥组合
     * 3. 支持"消费38.00"等无"元"后缀的格式
     */
    private fun extractAmountStrict(text: String): Double? {
        // ¥/￥ 符号 + 数字（取所有匹配中的最大值）
        val yenPattern = Regex("""[¥￥]\s*(\d+(?:\.\d{1,2})?)""")
        val yenMatches = yenPattern.findAll(text).mapNotNull { match ->
            match.groupValues[1].toDoubleOrNull()
        }.filter { it > 0 && it < 1_000_000 }.toList()

        if (yenMatches.isNotEmpty()) {
            return yenMatches.max()
        }

        // "金额：XXX" / "实付¥XXX" / "实扣¥XXX"
        val amountLabelPattern = Regex(
            """(?:金额|实付|实扣|应付|已付|消费金额|支付金额|交易金额)[：:\s]*[¥￥]?\s*(\d+(?:\.\d{1,2})?)"""
        )
        amountLabelPattern.find(text)?.let { match ->
            val amount = match.groupValues[1].toDoubleOrNull()
            if (amount != null && amount > 0 && amount < 1_000_000) return amount
        }

        // 动作词 + 数字 + 元
        val actionPattern = Regex(
            """(?:消费|支出|扣款|转账|支付|还款|存入|到账|入账|退款)[^\d]{0,10}(\d+(?:\.\d{1,2})?)\s*元"""
        )
        actionPattern.find(text)?.let { match ->
            val amount = match.groupValues[1].toDoubleOrNull()
            if (amount != null && amount > 0 && amount < 1_000_000) return amount
        }

        // 动作词 + 数字（无"元"后缀）
        val actionNoYuanPattern = Regex(
            """(?:消费|支出|扣款|支付|付款)[^\d]{0,5}(\d+\.\d{1,2})"""
        )
        actionNoYuanPattern.find(text)?.let { match ->
            val amount = match.groupValues[1].toDoubleOrNull()
            if (amount != null && amount > 0 && amount < 1_000_000) return amount
        }

        return null
    }

    /**
     * 从屏幕文本块提取商户名
     */
    private fun extractMerchantFromScreen(textBlocks: List<String>, allText: String): String {
        // 模式1：明确的商户标签
        val patterns = listOf(
            Regex("""(?:商户|商家|对方)[：:\s]*([^\n\s,，。]{2,20})"""),
            Regex("""付款给\s*([^\n\s,，。]{2,20})"""),
            Regex("""在\s*([^\n\s,，。]{2,20})\s*(?:消费|付款|支付)"""),
            Regex("""向\s*([^\n\s,，。]{2,20})\s*转账""")
        )

        for (pattern in patterns) {
            val match = pattern.find(allText)
            if (match != null) {
                val name = match.groupValues[1].trim()
                if (name.length >= 2 && !isNonMerchantWord(name)) {
                    return name
                }
            }
        }

        // 模式2：从文本块中找（通常是页面标题）
        // 支付成功页的标题通常是商户名
        for (block in textBlocks.take(5)) {
            val cleaned = block.trim()
            // 排除含"支付"、"成功"等词的块（这些是状态描述，不是商户名）
            if (cleaned.length in 2..15 &&
                !cleaned.contains("支付") && !cleaned.contains("成功") &&
                !cleaned.contains("完成") && !cleaned.contains("确认") &&
                !cleaned.contains("¥") && !cleaned.contains("￥") &&
                !cleaned.matches(Regex("""\d+.*""")) && // 排除纯数字开头的
                !isNonMerchantWord(cleaned)) {
                return cleaned
            }
        }

        return ""
    }

    private fun isNonMerchantWord(word: String): Boolean {
        val nonMerchant = setOf(
            "微信", "支付宝", "余额宝", "零钱", "银行卡", "信用卡",
            "消费", "支出", "扣款", "转账", "支付", "还款", "存入",
            "成功", "失败", "完成", "确认", "取消", "返回", "关闭",
            "详情", "记录", "订单", "账单", "明细"
        )
        return word in nonMerchant
    }
}
