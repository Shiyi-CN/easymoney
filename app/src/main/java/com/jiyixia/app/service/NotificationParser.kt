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
 * 4. 拒绝营销通知（含"优惠券"、"红包雨"、"活动"等词，但有退款/消费例外）
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

    /**
     * 营销通知特征词（出现这些词应拒绝识别）
     *
     * 注意：以下词在真实消费通知中也可能出现（如"使用红包抵扣"、"满减活动"），
     * 因此 isMarketing() 会结合支付确认词做例外判断。
     */
    private val MARKETING_KEYWORDS = listOf(
        "优惠券", "折扣", "满减", "满赠", "限时", "抢购", "特惠", "特价",
        "活动", "促销", "打折", "秒杀", "领红包", "抢红包", "红包雨",
        "积分兑换", "积分清零", "签到", "抽奖", "中奖", "免费领", "0元购", "0元领",
        "为你推荐", "猜你喜欢", "热门活动", "立即查看", "点击查看",
        "余额宝收益", "基金推荐", "理财产品", "保险推荐",
        "推广", "广告", "推荐有礼", "福利", "赠送",
        // 游戏/任务/等级类（防止游戏通知被误识别）
        "积分", "天数", "等级", "经验", "升级", "任务", "成就",
        "金币", "钻石", "体力", "活力", "关卡", "排行", "榜单",
        // 提醒类（非消费）
        "过期", "到期", "提醒", "更新", "版本", "公告", "通知提醒"
    )

    /**
     * 真实消费通知的支付确认词
     *
     * 如果通知同时包含营销词和支付确认词，优先按消费处理。
     * 例如："使用红包抵扣，实付¥38.00" 应识别为消费，不是营销。
     */
    private val PAYMENT_CONFIRM_KEYWORDS = listOf(
        "支付成功", "付款成功", "转账成功", "交易成功", "购买成功",
        "支付完成", "付款完成", "转账完成", "交易完成",
        "已支付", "已付款", "已扣款", "已转账",
        "消费", "扣款", "支出", "付款", "支付",
        "收款到账", "转账到账", "退款到账", "退款金额"
    )

    /** 微信支付通知模板特征 */
    private val WECHAT_PAY_TITLES = setOf(
        "微信支付", "微信转账", "微信红包", "微信收款",
        "WeChat Pay", "转账通知", "收款通知"
    )

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
        // 判断是否为已知支付App（包名已确认来源，可放宽金额提取规则）
        val isKnownPaymentApp = content.packageName == "com.tencent.mm" ||
                content.packageName == "com.eg.android.AlipayGphone"

        // 1. 营销通知过滤（最高优先级）
        //    已知支付App放宽营销检测：必须有明确营销词且无支付确认词才拒绝
        if (isMarketing(content, isKnownPaymentApp)) {
            Log.d(TAG, "营销通知拒绝: title=${content.title}, text=${content.text.take(50)}")
            return null
        }

        // 2. 根据包名选择解析模板
        //    重要：模板解析失败时逐级回退，避免"一个都不记"
        val template = when {
            content.packageName == "com.tencent.mm" -> {
                // 微信：严格模板 → 通用 → 宽松
                parseWeChatPay(content) ?: parseGeneric(content) ?: parsePaymentAppLenient(content)
            }
            content.packageName == "com.eg.android.AlipayGphone" -> {
                // 支付宝：严格模板 → 通用 → 宽松
                parseAlipay(content) ?: parseGeneric(content) ?: parsePaymentAppLenient(content)
            }
            content.packageName.startsWith("sms:") -> parseBankSms(content)
            else -> parseGeneric(content)
        }

        if (template == null || template.amount <= 0) {
            Log.d(TAG, "所有解析模板均失败: pkg=${content.packageName}, " +
                    "title=${content.title}, text=${content.text.take(60)}")
            return null
        }

        return template
    }

    /**
     * 营销通知检测
     *
     * 关键改进：
     * - 已知支付App（微信/支付宝）：只有明确营销词且无支付确认词时才拒绝
     * - 未知App：保持严格，有营销词就拒绝
     *
     * 营销通知的特征：
     * - 含营销词（优惠券/红包雨/积分兑换等）
     * - 不含支付确认词
     * - 没有明确金额（或金额为 0.01/0.88 等极小值）
     */
    private fun isMarketing(content: NotificationContent, isKnownPaymentApp: Boolean = false): Boolean {
        val checkText = content.allText

        // 含营销词时，检查是否同时含支付确认词
        if (MARKETING_KEYWORDS.any { checkText.contains(it) }) {
            // 例外1：退款/退货场景，保留
            val isRefund = checkText.contains("退款") || checkText.contains("退货")
            if (isRefund) return false

            // 例外2：同时含支付确认词 + 明确金额，视为消费（非营销）
            val hasPaymentConfirm = PAYMENT_CONFIRM_KEYWORDS.any { checkText.contains(it) }
            val hasYenAmount = Regex("""[¥￥]\s*\d+\.\d{2}""").containsMatchIn(checkText)
            if (hasPaymentConfirm && hasYenAmount) {
                Log.d(TAG, "含营销词但有支付确认+金额，视为消费: ${checkText.take(60)}")
                return false
            }

            // 已知支付App：即使没有¥金额，只要有支付确认词也放行
            // （微信/支付宝的通知格式多样，不能因为含"积分"等词就一刀切拒绝）
            if (isKnownPaymentApp && hasPaymentConfirm) {
                Log.d(TAG, "已知支付App含营销词但有支付确认词，放行: ${checkText.take(60)}")
                return false
            }

            return true
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

        // 从 bigText 或 text 提取金额（已知支付App，放宽纯数字过滤）
        val amountText = content.bigText.ifBlank { content.text }
        val amount = extractAmountStrict(amountText, isKnownPaymentApp = true) ?: return null

        // 从 bigText 提取商户名
        val merchantName = extractMerchantFromWeChat(content.bigText, content.text)

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
     * - text: "在星巴克消费¥38.00" 或 "星巴克 消费¥38.00"
     */
    private fun parseAlipay(content: NotificationContent): ParsedNotification? {
        val isOfficial = ALIPAY_TITLES.any { content.title.contains(it) }
        if (!isOfficial) return null

        // 支付宝的金额通常在 text 中（已知支付App，放宽纯数字过滤）
        val amountText = content.text.ifBlank { content.bigText }
        val amount = extractAmountStrict(amountText, isKnownPaymentApp = true) ?: return null

        // 从 text 提取商户名（多种格式）
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
     *
     * 放宽确认词要求，支持美团/京东/滴滴等 app 的多种通知格式。
     */
    private fun parseGeneric(content: NotificationContent): ParsedNotification? {
        // 必须包含支付确认词（放宽版，覆盖更多 App 格式）
        val hasPaymentConfirm = listOf(
            "支付成功", "付款成功", "转账成功", "交易成功", "购买成功", "下单成功",
            "支付完成", "付款完成", "转账完成", "交易完成",
            "已支付", "已付款", "已扣款", "已下单", "消费成功", "扣款成功",
            "支付¥", "支付￥", "付款¥", "付款￥",
            "行程费用", "车费支付", "已支付车费", "支付车费",
            "费用已支付", "订单已支付",
            // 新增：覆盖更多格式
            "实付", "实扣", "消费¥", "消费￥", "扣款¥", "扣款￥",
            "付款金额", "支付金额", "交易金额", "消费金额",
            "花费", "已花", "订单金额", "待付款", "需付款"
        ).any { content.allText.contains(it) }

        if (!hasPaymentConfirm) return null

        val amount = extractAmountStrict(content.allText) ?: return null

        // 商户名通常在 title
        val merchantName = if (content.title.isNotBlank() &&
            !content.title.contains("支付") && !content.title.contains("通知") &&
            !content.title.contains("成功") && !content.title.contains("完成")) {
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

    /**
     * 支付App宽松解析（最后回退方案）
     *
     * 当微信/支付宝的严格模板和通用模板都失败时使用。
     * 必须同时满足：包含支付确认词 + 能提取到带符号金额，才记录。
     * 避免营销通知（积分/红包/天数提醒等）被误识别。
     */
    private fun parsePaymentAppLenient(content: NotificationContent): ParsedNotification? {
        // 判断来源
        val isWeChat = content.packageName == "com.tencent.mm"
        val isAlipay = content.packageName == "com.eg.android.AlipayGphone"

        // 已知支付App：如果标题是官方账号，直接放行（不强制要求支付确认词）
        // 微信支付/支付宝的官方账号通知几乎都是交易相关
        val isOfficialTitle = (isWeChat && WECHAT_PAY_TITLES.any { content.title.contains(it) }) ||
                (isAlipay && ALIPAY_TITLES.any { content.title.contains(it) })

        if (!isOfficialTitle) {
            // 非官方账号标题：必须包含支付确认词
            val hasPaymentConfirm = listOf(
                "支付成功", "付款成功", "转账成功", "交易成功", "购买成功",
                "支付完成", "付款完成", "转账完成", "交易完成",
                "已支付", "已付款", "已扣款", "已转账",
                "消费", "扣款", "支出", "付款", "支付", "转账"
            ).any { content.allText.contains(it) }

            if (!hasPaymentConfirm) {
                Log.d(TAG, "宽松解析跳过（非官方标题且无支付确认词）: title=${content.title}, text=${content.text.take(50)}")
                return null
            }
        }

        // 从所有文本中提取金额（已知支付App，放宽纯数字过滤）
        val amount = extractAmountStrict(content.allText, isKnownPaymentApp = true) ?: return null
        if (amount <= 0) return null

        val scene = when {
            isWeChat -> "微信支付"
            isAlipay -> "支付宝支付"
            else -> "支付"
        }

        // 商户名：尝试从 title 或 text 提取
        val merchantName = content.title
            .takeIf { it.isNotBlank() && !it.contains("支付") && !it.contains("通知") }
            ?: ""

        Log.d(TAG, "宽松解析成功: scene=$scene, amount=$amount, title=${content.title}")

        return ParsedNotification(
            amount = amount,
            merchantName = merchantName,
            scene = scene,
            isMarketing = false,
            content = content
        )
    }

    // ═══════════════════════════════════════════════════════════
    //  严格金额提取（支持多种真实通知格式）
    // ═══════════════════════════════════════════════════════════

    /**
     * 严格金额提取
     *
     * 改进点：
     * 1. ¥/￥ 符号后取最大金额（避免"红包抵扣¥0.01，实付¥38.00"取到 0.01）
     * 2. 支持"支付38.00"、"消费38"等无"元"后缀的格式（支付宝/微信常见）
     * 3. 支持"实付¥38"、"实扣¥38"等动作词+¥组合
     * 4. 支持千分位逗号格式（如"5,000.00元"），银行大额交易常见
     * 5. 对已知支付App放宽纯数字过滤（包名已确认来源）
     *
     * 不匹配：
     * - 纯数字（容易匹配到订单号、时间等）—— 已知支付App除外
     * - "X元"（容易匹配到"满100元减50元"）
     */
    private fun extractAmountStrict(text: String, isKnownPaymentApp: Boolean = false): Double? {
        // ═══════════════════════════════════════════════════════════
        //  多候选金额评分系统（借鉴 Moneytask 项目）
        //
        //  核心思想：通知中可能有多个金额（如"交易金额42.10"和"可用额度￥58497.69"），
        //  收集所有候选金额，根据上下文关键词打分，取得分最高的作为真实金额。
        // ═══════════════════════════════════════════════════════════

        // 统一金额正则：匹配 ¥数字、数字.元、数字元 等各种格式
        val amountRegex = Regex("""[¥￥]?\s*([\d,]+(?:\.\d{1,2})?)\s*[元块]?""")
        val candidates = mutableListOf<Pair<Double, Int>>() // (金额, 得分)

        // 高权关键词（+50）：明确表示这是交易金额
        val highWeight = listOf(
            "实付", "实际支付", "支付金额", "付款金额", "订单金额",
            "消费金额", "交易金额", "扣款金额", "转账金额"
        )
        // 中权关键词（+20）：表示这是一笔消费
        val midWeight = listOf(
            "消费成功", "已成功付款", "支付成功", "付款成功",
            "消费", "扣款", "支出", "支付", "付款", "转账"
        )
        // 低权关键词（-20）：表示这不是交易金额
        val lowWeight = listOf(
            "优惠", "余额", "可用", "额度", "剩余", "红包",
            "抵扣", "折扣", "积分", "订单号", "交易号", "流水号",
            "尾号", "验证码", "可用额度"
        )

        for (match in amountRegex.findAll(text)) {
            val rawValue = match.groupValues[1].replace(",", "")
            val amount = rawValue.toDoubleOrNull() ?: continue

            // 范围过滤：1 分 ~ 99,999,999.99 元
            if (amount <= 0 || amount >= 1_000_000) continue

            // 提取金额前 15 个字符的上下文
            val prefix = text.substring(
                (match.range.first - 15).coerceAtLeast(0),
                match.range.first
            )

            // 时间格式排除：如果上下文包含时间格式，跳过
            if (Regex("""\d{4}年|\d{1,2}:\d{2}|\d{4}-\d{2}-\d{2}""").containsMatchIn(prefix)) {
                continue
            }

            // 检查是否带货币符号（¥/￥/元/块）
            val matchStr = match.value
            val hasCurrencySymbol = matchStr.contains("¥") || matchStr.contains("￥") ||
                    matchStr.contains("元") || matchStr.contains("块")

            // 打分
            var score = 0
            var hasHighOrMid = false
            // 高权关键词
            for (kw in highWeight) {
                if (prefix.contains(kw)) { score += 50; hasHighOrMid = true; break }
            }
            // 中权关键词
            for (kw in midWeight) {
                if (prefix.contains(kw)) { score += 20; hasHighOrMid = true; break }
            }
            // 低权关键词（扣分）
            var lowHits = 0
            for (kw in lowWeight) {
                if (prefix.contains(kw)) lowHits++
            }
            score -= lowHits * 20

            // 带 ¥/￥/元 符号加分
            if (matchStr.contains("¥") || matchStr.contains("￥") || matchStr.contains("元")) {
                score += 3
            }

            // ★ 关键修复：无货币符号且无高/中权关键词 → 跳过
            // 防止纯数字（如游戏中的"33"、营销通知中的"33积分"）被误识别为金额
            // 例外：已知支付App（微信/支付宝）放行，因为包名已确认来源是支付App
            // 微信支付通知的 text 经常是纯数字"38.00"（无¥符号）
            if (!hasCurrencySymbol && !hasHighOrMid) {
                if (isKnownPaymentApp) {
                    Log.d(TAG, "已知支付App纯数字候选放行: $amount")
                } else {
                    Log.d(TAG, "跳过无符号无关键词候选: $amount (prefix=${prefix.takeLast(10)})")
                    continue
                }
            }

            candidates.add(Pair(amount, score))
        }

        if (candidates.isEmpty()) return null

        // 取得分最高的（得分相同取金额较大的）
        val best = candidates.maxByOrNull { it.second } ?: return null

        Log.d(TAG, "金额候选: ${candidates.size}个, 最优: ¥${best.first} (得分${best.second})")

        // 如果最优候选得分为负，说明很可能是错误金额，返回 null
        if (best.second < 0) {
            Log.d(TAG, "所有候选得分均为负，可能是非交易通知")
            return null
        }

        return best.first
    }

    // ═══════════════════════════════════════════════════════════
    //  商户名提取
    // ═══════════════════════════════════════════════════════════

    /** 微信支付 bigText 中的商户名提取 */
    private fun extractMerchantFromWeChat(bigText: String, text: String): String {
        val source = bigText.ifBlank { text }
        if (source.isBlank()) return ""

        // 模式1："商户：XXX" / "商户 XXX" / "付款给XXX" / "向XXX付款" / "转账给XXX"
        val patterns = listOf(
            Regex("""(?:商户|商家|对方)[：:\s]*([^\n\s,，。]{2,20})"""),
            Regex("""付款给\s*([^\n\s,，。]{2,20})"""),
            Regex("""向\s*([^\n\s,，。]{2,20})\s*付款"""),
            Regex("""转账给\s*([^\n\s,，。]{2,20})""")
        )

        for (pattern in patterns) {
            val match = pattern.find(source)
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
        // 支付宝的商户名通常在 text 中（如"在星巴克消费¥38.00"），
        // bigText 可能是补充信息（如"使用红包抵扣¥0.01"），不一定含商户名。
        // 因此优先搜索 text，找不到再搜索 bigText。
        val patterns = listOf(
            Regex("""在\s*([^\n\s,，。]{2,20})\s*(?:消费|付款|支付)"""),
            Regex("""(?:商户|商家)[：:\s]*([^\n\s,，。]{2,20})"""),
            Regex("""付款给\s*([^\n\s,，。]{2,20})"""),
            Regex("""([^\n\s,，。]{2,20})\s*[-－]\s*(?:消费|付款|支付)"""),
            // 无"在"字的格式 "星巴克 消费¥38.00" / "星巴克消费¥38.00"
            Regex("""([^\n\s,，。¥￥]{2,20})\s*(?:消费|付款|支付)[¥￥]""")
        )

        // 依次搜索 text 和 bigText
        for (source in listOf(text, bigText)) {
            if (source.isBlank()) continue
            for (pattern in patterns) {
                val match = pattern.find(source)
                if (match != null) {
                    val name = match.groupValues[1].trim()
                    if (name.length >= 2 && !isNonMerchantWord(name)) {
                        return name
                    }
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
            "成功", "失败", "完成", "确认", "取消",
            "实付", "实扣", "应付", "已付"
        )
        return word in nonMerchant
    }
}
