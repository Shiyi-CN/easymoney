package com.jiyixia.app.util

/**
 * 语音分类引擎 —— 从语音识别文本中提取金额和自动分类
 *
 * 设计目标（来自设计文档）：
 * - "午餐 38"     → 金额=38.00,  分类=餐饮
 * - "打车 25 块 5" → 金额=25.50,  分类=交通
 * - 使用 Android 本地语音识别（SpeechRecognizer），不联网
 * - 简单的关键词匹配准确率已经 > 90%（设计文档第182行）
 */
object VoiceCategorizer {

    /**
     * 解析结果
     */
    data class ParsedResult(
        val amount: Double,           // 解析出的金额
        val amountText: String,       // 金额文本（如 "25.50"）
        val categoryName: String,     // 分类名称
        val categoryId: Long,         // 分类 ID（需调用方根据名称映射）
        val note: String,             // 提取的备注
        val isExpense: Boolean,       // 是否为支出
        val confidence: Int           // 置信度 0-100
    )

    // ═══════════════════════════════════════════════════════════
    //  分类关键词库（按设计文档 P4 第89-93行的本地规则引擎思想）
    //  支出类
    // ═══════════════════════════════════════════════════════════

    private val expenseCategories = mapOf(
        "餐饮" to listOf(
            "午餐", "午饭", "晚餐", "晚饭", "早餐", "早饭", "吃饭", "外卖",
            "餐厅", "饭店", "食堂", "火锅", "奶茶", "咖啡", "饮品", "饮料",
            "水果", "零食", "小吃", "夜宵", "烧烤", "串串", "面", "粉", "米线",
            "星巴克", "麦当劳", "肯德基", "瑞幸", "蜜雪", "喜茶", "奈雪",
            "美团外卖", "饿了么", "盒马", "叮咚", "买菜",
            // 通用食物词
            "吃", "喝", "饭", "菜", "酒", "茶", "奶", "水", "汤", "粥"
        ),
        "交通" to listOf(
            "打车", "地铁", "公交", "加油", "停车", "滴滴", "出租车",
            "高铁", "火车", "机票", "飞机", "骑行", "共享单车", "哈啰",
            "摩拜", "青桔", "顺风车", "网约车", "高速", "过路费",
            "车", "出行", "导航"
        ),
        "购物" to listOf(
            "超市", "便利店", "百货", "商场", "淘宝", "京东", "拼多多",
            "买", "衣服", "鞋子", "裤子", "日用品", "化妆品", "护肤品",
            "电器", "手机", "电脑", "数码", "快递", "物流", "配送",
            "购物", "下单", "订单", "购", "件"
        ),
        "娱乐" to listOf(
            "电影", "游戏", "KTV", "唱歌", "旅游", "景点", "门票",
            "网吧", "网咖", "露营", "演唱会", "演出", "剧本杀",
            "密室", "游乐园", "迪士尼", "音乐", "视频", "会员",
            "玩", "乐", "唱", "看"
        ),
        "居住" to listOf(
            "房租", "水电", "物业", "网费", "话费", "租金", "房贷",
            "维修", "装修", "水费", "电费", "燃气", "煤气", "煤气费",
            "宽带", "手机费", "物管", "取暖", "物业费"
        ),
        "医疗" to listOf(
            "医院", "药店", "挂号", "药", "看病", "门诊", "体检",
            "诊所", "牙科", "眼科", "住院", "检查", "手术",
            "生病", "感冒", "发烧", "咳嗽"
        ),
        "教育" to listOf(
            "书", "课程", "培训", "考试", "学费", "资料", "文具",
            "补习", "学习", "教材", "图书", "报名", "考证",
            "学", "教", "读"
        ),
        "其他" to listOf(
            "转账", "充值", "还款", "还款", "借款", "借钱", "随份子",
            "捐款", "罚款", "手续费"
        )
    )

    // ═══════════════════════════════════════════════════════════
    //  收入类
    // ═══════════════════════════════════════════════════════════

    private val incomeCategories = mapOf(
        "工资" to listOf("工资", "薪水", "发工资", "基本工资", "到手"),
        "奖金" to listOf("奖金", "年终奖", "项目奖", "绩效", "奖励"),
        "理财" to listOf("理财", "利息", "基金", "股票", "收益", "存款", "余额宝"),
        "兼职" to listOf("兼职", "副业", "外快", "稿费", "接单"),
        "红包" to listOf("红包", "收红包", "零花钱", "压岁钱", "份子钱")
    )

    // ═══════════════════════════════════════════════════════════
    //  金额正则可匹配的模式
    // ═══════════════════════════════════════════════════════════

    /**
     * 解析语音文本，返回金额 + 分类 + 备注
     *
     * @param text 语音识别后的文本，如 "午餐 38 块"、"打车 25 块 5"
     * @param categoryNameToId 分类名称 → ID 的映射（由调用方提供）
     * @param defaultCategoryId 默认分类 ID（无法识别时使用）
     */
    fun parse(
        text: String,
        categoryNameToId: Map<String, Long>,
        defaultCategoryId: Long = 0L
    ): ParsedResult? {
        if (text.isBlank()) return null

        val cleaned = text.trim().replace("。", "").replace("，", ",")

        // 1. 提取金额
        val amountResult = extractAmount(cleaned) ?: return null

        // 2. 先判断是否为收入
        val incomeCategory = findCategory(cleaned, incomeCategories)
        if (incomeCategory != null) {
            // 收入类的金额描述
            val note = buildNote(cleaned, incomeCategory)
            return ParsedResult(
                amount = amountResult.value,
                amountText = amountResult.text,
                categoryName = incomeCategory,
                categoryId = categoryNameToId[incomeCategory] ?: defaultCategoryId,
                note = note,
                isExpense = false,
                confidence = 85
            )
        }

        // 3. 按支出分类匹配
        val expenseCategory = findCategory(cleaned, expenseCategories)
        if (expenseCategory != null) {
            val note = buildNote(cleaned, expenseCategory)
            return ParsedResult(
                amount = amountResult.value,
                amountText = amountResult.text,
                categoryName = expenseCategory,
                categoryId = categoryNameToId[expenseCategory] ?: defaultCategoryId,
                note = note,
                isExpense = true,
                confidence = 80
            )
        }

        // 4. 只识别到金额，分到"其他"
        return ParsedResult(
            amount = amountResult.value,
            amountText = amountResult.text,
            categoryName = "其他",
            categoryId = categoryNameToId["其他"] ?: defaultCategoryId,
            note = cleaned,
            isExpense = true,
            confidence = 50
        )
    }

    // ═══════════════════════════════════════════════════════════
    //  金额提取
    // ═══════════════════════════════════════════════════════════

    private data class AmountResult(val value: Double, val text: String)

    private fun extractAmount(text: String): AmountResult? {
        // 1. "X块Y" / "X块Y毛" 模式，如 "二十五块五" → 暂时只支持数字格式
        //    先尝试中文数字 → 阿拉伯数字转换的简单模式
        val cnToNum = mapOf(
            "零" to 0, "一" to 1, "二" to 2, "两" to 2, "三" to 3,
            "四" to 4, "五" to 5, "六" to 6, "七" to 7, "八" to 8, "九" to 9,
            "十" to 10
        )

        // 2. 标准数字格式：匹配如 "38", "25.5", "128.50", "38元", "25块5", "38块"
        val patterns = listOf(
            // "128.50" 或 "128.5"
            Regex("""(\d+\.\d{1,2})"""),
            // "25块5" / "25块5毛" / "25块5角"
            Regex("""(\d+)\s*块\s*(\d+)"""),
            // "38 元" / "38元" / "38块" (不带角分)
            Regex("""(\d+)\s*[元块钱￥¥]"""),
            // 纯数字 "38"（没有块/元后缀的数字）
            Regex("""(?<![a-zA-Z0-9.])(\d+)(?![a-zA-Z0-9.])""")
        )

        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                val groups = match.groupValues
                return when {
                    // "25块5" → groups = ["25块5", "25", "5"]
                    groups.size >= 3 && groups[1].isNotEmpty() && groups[2].isNotEmpty() -> {
                        val main = groups[1].toDoubleOrNull() ?: continue
                        val frac = groups[2].toDoubleOrNull() ?: continue
                        val fracAdjusted = if (frac >= 10) frac / 100.0 else frac / 10.0
                        val value = main + fracAdjusted
                        AmountResult(value, "%.2f".format(value))
                    }
                    // 普通数字
                    groups.size >= 2 -> {
                        val value = groups[1].toDoubleOrNull() ?: continue
                        AmountResult(value, "%.2f".format(value))
                    }
                    else -> continue
                }
            }
        }

        // 3. 尝试中文数字金额（简单版，只处理整数）
        // "三十八" → 38
        return tryParseChineseAmount(text)
    }

    /**
     * 简单的中文数字金额解析
     * 支持 "三十八"、"一百二"、"一千五" 等常见口语表达
     */
    private fun tryParseChineseAmount(text: String): AmountResult? {
        // 用正则找中文数字段
        val cnNumPattern = Regex("""[零一二两三四五六七八九十百千万]+""")
        val match = cnNumPattern.find(text) ?: return null
        val cnStr = match.value

        // 简单的转换：去掉"元/块"后的部分
        val numStr = cnStr.replace(Regex("""[元块钱￥¥]"""), "")

        val value = chineseToArabic(numStr) ?: return null

        // 检查是否跟着"块5"这样的角分
        val afterCnNum = text.substring(match.range.last + 1)
        val jiaoFen = Regex("""块\s*(\d+)""").find(afterCnNum)
        if (jiaoFen != null) {
            val fen = jiaoFen.groupValues[1].toDoubleOrNull() ?: 0.0
            val total = value + (if (fen >= 10) fen / 100.0 else fen / 10.0)
            return AmountResult(total, "%.2f".format(total))
        }

        return AmountResult(value.toDouble(), "%.2f".format(value))
    }

    /**
     * 中文数字 → 阿拉伯数字（支持"三千五百二十一"→3521）
     */
    private fun chineseToArabic(cn: String): Long? {
        if (cn.isEmpty()) return null

        val digitMap = mapOf(
            '零' to 0, '一' to 1, '二' to 2, '三' to 3, '四' to 4,
            '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
            '两' to 2
        )
        val unitMap = mapOf('十' to 10L, '百' to 100L, '千' to 1000L, '万' to 10000L)

        // 特殊情况：纯数字如 "三十八"
        // 处理逻辑：遍历，遇到数字则累计，遇到单位则乘以单位
        var result = 0L
        var temp = 0L  // 当前累计的数字（万以下的）

        for (ch in cn) {
            when {
                ch in digitMap -> {
                    temp += digitMap[ch]!!.toLong()
                }
                ch in unitMap -> {
                    val unit = unitMap[ch]!!
                    if (temp == 0L) temp = 1L  // "十" = 10
                    if (ch == '万') {
                        result += temp * unit
                        temp = 0
                    } else {
                        temp *= unit
                    }
                }
            }
        }

        result += temp
        return if (result > 0) result else null
    }

    // ═══════════════════════════════════════════════════════════
    //  分类匹配
    // ═══════════════════════════════════════════════════════════

    /**
     * 在分类关键词库中查找匹配的分类
     * 使用最长匹配原则：匹配到的关键词越长，置信度越高
     */
    private fun findCategory(
        text: String,
        categoryKeywords: Map<String, List<String>>
    ): String? {
        var bestCategory: String? = null
        var bestLength = 0

        for ((category, keywords) in categoryKeywords) {
            for (keyword in keywords) {
                if (text.contains(keyword) && keyword.length > bestLength) {
                    bestCategory = category
                    bestLength = keyword.length
                }
            }
        }

        return bestCategory
    }

    // ═══════════════════════════════════════════════════════════
    //  备注生成
    // ═══════════════════════════════════════════════════════════

    private fun buildNote(text: String, categoryName: String): String {
        // 去掉金额相关部分和分类关键词，剩下的作为备注
        var note = text
            .replace(Regex("""\d+\.?\d*\s*[元块钱￥¥]?"""), "")
            .replace(Regex("""块\s*\d*"""), "")
            .replace(Regex("""[零一二两三四五六七八九十百千万]+"""), "")
            .replace(categoryName, "")
            .replace(Regex("""公司报销|单位报销|部门报销|个人报销|报销"""), "")     // 报销是标记指令，不是备注内容
            .trim()
            .replace(Regex("""\s+"""), " ")

        // 移除金额单位残留
        note = note.replace(Regex("""[元块￥¥角毛分]"""), "").trim()

        // 如果备注太短或就是一些无意义字符，返回空
        if (note.length <= 1 || note.matches(Regex("""^[的了吧吗呢啊哦嗯]+$"""))) {
            return ""
        }

        return note
    }
}
