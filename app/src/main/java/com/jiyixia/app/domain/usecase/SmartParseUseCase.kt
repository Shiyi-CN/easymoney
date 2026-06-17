package com.jiyixia.app.domain.usecase

/**
 * 智能文本解析 UseCase —— 从文本中提取金额、自动分类、生成备注
 *
 * 设计目标（来自设计文档）：
 * - "午餐 38"     → 金额=38.00,  分类=餐饮
 * - "打车 25 块 5" → 金额=25.50,  分类=交通
 * - 使用本地规则引擎，不联网
 * - 简单的关键词匹配准确率已经 > 90%（设计文档第182行）
 *
 * 统一入口：语音输入、手动输入、通知自动记账都调用此 UseCase
 */
object SmartParseUseCase {

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
        val confidence: Int,          // 置信度 0-100
        val isReimbursable: Boolean = false,      // 是否可报销
        val reimbursementTarget: String = ""      // 报销对象
    )

    // ═══════════════════════════════════════════════════════════
    //  分类关键词库（按设计文档 P4 第89-93行的本地规则引擎思想）
    //  支出类
    // ═══════════════════════════════════════════════════════════

    private val expenseCategories = mapOf(
        "餐饮" to listOf(
            "午餐", "午饭", "晚餐", "晚饭", "早餐", "早饭", "吃饭", "外卖",
            "餐厅", "饭店", "食堂", "火锅", "奶茶", "咖啡", "饮品", "饮料",
            "水果", "零食", "小吃", "夜宵", "宵夜", "烧烤", "串串", "面", "粉", "米线",
            "星巴克", "麦当劳", "肯德基", "瑞幸", "蜜雪", "喜茶", "奈雪",
            "美团外卖", "饿了么", "盒马", "叮咚", "买菜", "海底捞", "必胜客",
            "肯德基", "KFC", "麦当劳", "M记", "便利店", "全家", "711", "罗森",
            // 平台+外卖复合关键词（优先于"淘宝"匹配到"购物"）
            "淘宝外卖", "京东外卖", "抖音外卖", "拼多多外卖",
            // 通用食物词
            "吃", "喝", "饭", "菜", "酒", "茶", "奶", "水", "汤", "粥",
            "蛋糕", "甜品", "冰淇淋", "巧克力", "饼干", "薯片"
        ),
        "交通" to listOf(
            "打车", "地铁", "公交", "加油", "停车", "滴滴", "出租车",
            "高铁", "火车", "机票", "飞机", "骑行", "共享单车", "哈啰",
            "摩拜", "青桔", "顺风车", "网约车", "高速", "过路费",
            "车", "出行", "导航", "充电", "洗车", "保养", "车险",
            "代驾", "租车", "油费", "充电桩", "ETC",
            // 打车/出行场景增强
            "行程", "车费", "路费", "打车费", "出行费",
            "快车", "专车", "拼车", "优享", "豪华车",
            "曹操出行", "T3出行", "神州", "首汽", "嘀嗒",
            "高德打车", "百度打车", "美团打车",
            "地铁票", "公交卡", "一卡通", "交通卡",
        ),
        "购物" to listOf(
            "超市", "百货", "商场", "淘宝", "京东", "拼多多",
            "买", "衣服", "鞋子", "裤子", "日用品", "化妆品", "护肤品",
            "电器", "手机", "电脑", "数码", "快递", "物流", "配送",
            "购物", "下单", "订单", "购", "件", "618", "双11", "双十一",
            "天猫", "苏宁", "唯品会", "抖音商城", "小红书"
        ),
        "居住" to listOf(
            "房租", "水电", "物业", "网费", "话费", "租金", "房贷",
            "维修", "装修", "水费", "电费", "燃气", "煤气", "煤气费",
            "宽带", "手机费", "物管", "取暖", "物业费", "暖气费",
            "家具", "家电", "保洁", "家政"
        ),
        "娱乐" to listOf(
            "电影", "游戏", "KTV", "唱歌", "旅游", "景点", "门票",
            "网吧", "网咖", "露营", "演唱会", "演出", "剧本杀",
            "密室", "游乐园", "迪士尼", "音乐", "视频", "会员",
            "玩", "乐", "唱", "看", "爱奇艺", "腾讯视频", "优酷",
            "B站", "网飞", "Netflix", "Spotify", "Apple Music",
            "Steam", "PS5", "Switch", "Xbox"
        ),
        "医疗" to listOf(
            "医院", "药店", "挂号", "药", "看病", "门诊", "体检",
            "诊所", "牙科", "眼科", "住院", "检查", "手术",
            "生病", "感冒", "发烧", "咳嗽", "医保", "保健品",
            "维生素", "钙片", "口罩", "消毒液"
        ),
        "教育" to listOf(
            "书", "课程", "培训", "考试", "学费", "资料", "文具",
            "补习", "学习", "教材", "图书", "报名", "考证",
            "学", "教", "读", "考研", "雅思", "托福", "PMP",
            "网课", "知乎", "得到", "极客时间"
        ),
        "通讯" to listOf(
            "话费", "流量", "套餐", "宽带", "充值", "移动", "联通", "电信",
            "手机费", "电话费", "流量包", "会员"
        ),
        "社交" to listOf(
            "份子钱", "随份子", "红包", "请客", "聚餐", "送礼", "礼物",
            "生日", "婚礼", "满月", "乔迁", "丧事", "人情",
            "社交", "应酬", "招待"
        ),
        "美容" to listOf(
            "理发", "美发", "染发", "烫发", "美甲", "美容", "护肤",
            "化妆品", "口红", "面膜", "洗面奶", "防晒", "香水",
            "SPA", "按摩", "美体", "减肥"
        ),
        "宠物" to listOf(
            "猫粮", "狗粮", "宠物", "猫", "狗", "宠物医院", "疫苗",
            "驱虫", "绝育", "宠物店", "猫砂", "宠物玩具", "鱼缸",
            "鸟笼", "仓鼠", "兔子"
        ),
        "办公" to listOf(
            "办公", "打印", "复印", "文具", "笔记本", "笔", "纸",
            "办公用品", "快递费", "差旅", "出差"
        ),
        "维修" to listOf(
            "维修", "修理", "修手机", "修电脑", "换屏", "换电池",
            "修车", "保养", "维修费"
        ),
        "捐赠" to listOf(
            "捐款", "捐赠", "慈善", "公益", "爱心", "众筹", "水滴筹"
        ),
        "其他" to listOf(
            "转账", "充值", "还款", "借款", "借钱", "罚款", "手续费",
            "其他", "杂项", " miscellaneous"
        )
    )

    // ═══════════════════════════════════════════════════════════
    //  收入类
    // ═══════════════════════════════════════════════════════════

    private val incomeCategories = mapOf(
        "工资" to listOf("工资", "薪水", "发工资", "基本工资", "到手", "月薪", "底薪", "月入"),
        "奖金" to listOf("奖金", "年终奖", "项目奖", "绩效", "奖励", "提成", "分红", "季度奖"),
        "理财" to listOf("理财", "利息", "基金", "股票", "收益", "存款", "余额宝", "理财通", "股息", "分红"),
        "兼职" to listOf("兼职", "副业", "外快", "稿费", "接单", "私活", "外包", "咨询费"),
        "红包" to listOf("红包", "收红包", "零花钱", "压岁钱", "份子钱", "转账"),
        "报销" to listOf("报销", "报销款", "报销到账", "差旅报销", "费用报销"),
        "租金" to listOf("租金", "房租", "收租", "租客", "房租收入"),
        "退款" to listOf("退款", "退货", "退款到账", "退钱", "返现"),
        "中奖" to listOf("中奖", "彩票", "抽奖", "奖品", "奖金")
    )

    // ═══════════════════════════════════════════════════════════
    //  金额正则可匹配的模式
    // ═══════════════════════════════════════════════════════════

    /**
     * 解析文本，返回金额 + 分类 + 备注
     *
     * @param text 识别后的文本，如 "午餐 38 块"、"打车 25 块 5"
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

        // 2. 检测支出分类
        val expenseCategory = findCategory(cleaned, expenseCategories)

        // 3. 检测收入分类
        val incomeCategory = findCategory(cleaned, incomeCategories)

        // 4. 判断是否包含"报销"关键词
        val hasReimbursementKeyword = listOf("报销", "可报销", "能报销", "要报销").any { cleaned.contains(it) }

        // 4.1 检测"XX公司/分公司/单位/部门"模式（自动识别为可报销）
        val companyReimbursementResult = detectCompanyReimbursement(cleaned)
        val hasCompanyPattern = companyReimbursementResult.first

        // 5. 明确的收入关键词（"到账"、"收入"、"收到"等），表示这是真正的收入而非可报销支出
        val hasExplicitIncomeKeyword = listOf("到账", "收入", "收到", "入账", "进账").any { cleaned.contains(it) }

        // 6. 报销关键词优先：如果有报销关键词且没有明确收入关键词，走支出+可报销路径
        //    例："报销 200" → 支出+可报销，而非收入
        //    例："报销到账 200" → 收入（有"到账"关键词）
        if (hasReimbursementKeyword && !hasExplicitIncomeKeyword) {
            val category = expenseCategory ?: "其他"
            val reimbursementResult = detectReimbursement(cleaned)
            return ParsedResult(
                amount = amountResult.value,
                amountText = amountResult.text,
                categoryName = category,
                categoryId = categoryNameToId[category] ?: defaultCategoryId,
                note = cleaned,  // 备注直接使用原始输入
                isExpense = true,
                confidence = 85,
                isReimbursable = true,
                reimbursementTarget = reimbursementResult.second
            )
        }

        // 6.1 公司名模式：如果有"XX公司/分公司"模式且没有明确收入关键词，走支出+可报销路径
        if (hasCompanyPattern && !hasExplicitIncomeKeyword) {
            val category = expenseCategory ?: "其他"
            return ParsedResult(
                amount = amountResult.value,
                amountText = amountResult.text,
                categoryName = category,
                categoryId = categoryNameToId[category] ?: defaultCategoryId,
                note = cleaned,  // 备注直接使用原始输入
                isExpense = true,
                confidence = 80,
                isReimbursable = true,
                reimbursementTarget = companyReimbursementResult.second
            )
        }

        // 7. 收入分类（如"工资"、"报销到账"等）
        // 优先匹配收入分类，解决"红包"等歧义关键词问题
        if (incomeCategory != null) {
            return ParsedResult(
                amount = amountResult.value,
                amountText = amountResult.text,
                categoryName = incomeCategory,
                categoryId = categoryNameToId[incomeCategory] ?: defaultCategoryId,
                note = cleaned,  // 备注直接使用原始输入
                isExpense = false,
                confidence = 85,
                isReimbursable = false,
                reimbursementTarget = ""
            )
        }

        // 8. 支出分类 + 报销关键词 = 支出+可报销
        if (expenseCategory != null && hasReimbursementKeyword) {
            val reimbursementResult = detectReimbursement(cleaned)
            return ParsedResult(
                amount = amountResult.value,
                amountText = amountResult.text,
                categoryName = expenseCategory,
                categoryId = categoryNameToId[expenseCategory] ?: defaultCategoryId,
                note = cleaned,  // 备注直接使用原始输入
                isExpense = true,
                confidence = 85,
                isReimbursable = true,
                reimbursementTarget = reimbursementResult.second
            )
        }

        // 9. 纯支出（没有报销关键词）
        if (expenseCategory != null) {
            return ParsedResult(
                amount = amountResult.value,
                amountText = amountResult.text,
                categoryName = expenseCategory,
                categoryId = categoryNameToId[expenseCategory] ?: defaultCategoryId,
                note = cleaned,  // 备注直接使用原始输入
                isExpense = true,
                confidence = 80,
                isReimbursable = false,
                reimbursementTarget = ""
            )
        }

        // 10. 只识别到金额，分到"其他"
        return ParsedResult(
            amount = amountResult.value,
            amountText = amountResult.text,
            categoryName = "其他",
            categoryId = categoryNameToId["其他"] ?: defaultCategoryId,
            note = cleaned,
            isExpense = true,
            confidence = 50,
            isReimbursable = false,
            reimbursementTarget = ""
        )
    }

    /**
     * 检测报销相关关键词
     * @return Pair<是否可报销, 报销对象>
     */
    private fun detectReimbursement(text: String): Pair<Boolean, String> {
        val reimbursementKeywords = listOf("报销", "可报销", "能报销", "要报销", "需报销")
        val isReimbursable = reimbursementKeywords.any { text.contains(it) }

        if (!isReimbursable) return Pair(false, "")

        // 模式1: "XX分公司/公司/单位/部门" + ... + "报销"
        val targetRegex1 = Regex("""([一-龥A-Za-z0-9]{1,15})(分公司|公司|单位|部门|人).*?报销""")
        val match1 = targetRegex1.find(text)
        if (match1 != null) {
            val raw = match1.groupValues[1].trim()
            val suffix = match1.groupValues[2]
            val filterWords = setOf("打车", "吃饭", "坐车", "的", "了", "要", "是", "可", "能", "交通", "餐饮")
            if (raw !in filterWords) {
                return Pair(true, "$raw$suffix")
            }
        }

        // 模式2: "报销" + "XX分公司/公司/单位/部门"（报销在前）
        val targetRegex2 = Regex("""报销.*?([一-龥A-Za-z0-9]{1,15})(分公司|公司|单位|部门|人)""")
        val match2 = targetRegex2.find(text)
        if (match2 != null) {
            val raw = match2.groupValues[1].trim()
            val suffix = match2.groupValues[2]
            val filterWords = setOf("打车", "吃饭", "坐车", "的", "了", "要", "是", "可", "能", "交通", "餐饮")
            if (raw !in filterWords) {
                return Pair(true, "$raw$suffix")
            }
        }

        // 模式3: "XX" + "报销"（无后缀，直接在报销前找2-10个字符）
        val targetRegex3 = Regex("""([一-龥A-Za-z]{2,10})报销""")
        val match3 = targetRegex3.find(text)
        if (match3 != null) {
            val raw = match3.groupValues[1].trim()
            val filterWords = setOf("打车", "吃饭", "坐车", "的", "了", "要", "是", "可", "能", "交通", "餐饮", "差旅", "费用", "出差")
            if (raw !in filterWords) {
                return Pair(true, raw)
            }
        }

        // 模式4: "报销" + "XX"（报销在前，后面跟2-10个字符作为对象）
        val targetRegex4 = Regex("""报销\s*([一-龥A-Za-z]{2,10})""")
        val match4 = targetRegex4.find(text)
        if (match4 != null) {
            val raw = match4.groupValues[1].trim()
            val filterWords = setOf("到账", "收入", "收到", "入账", "进账", "款", "了", "的", "是")
            if (raw !in filterWords) {
                return Pair(true, raw)
            }
        }

        return Pair(true, "")
    }

    /**
     * 检测"XX公司/分公司/单位/部门"模式（自动识别为可报销）
     * @return Pair<是否匹配, 报销对象>
     */
    private fun detectCompanyReimbursement(text: String): Pair<Boolean, String> {
        // 模式1: "XX分公司/公司/单位/部门"
        val targetRegex1 = Regex("""([一-龥A-Za-z0-9]{1,15})(分公司|公司|单位|部门)""")
        val match1 = targetRegex1.find(text)
        if (match1 != null) {
            val raw = match1.groupValues[1].trim()
            val suffix = match1.groupValues[2]
            val filterWords = setOf("打车", "吃饭", "坐车", "的", "了", "要", "是", "可", "能", "交通", "餐饮")
            if (raw !in filterWords) {
                return Pair(true, "$raw$suffix")
            }
        }

        return Pair(false, "")
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
            // "25块5" / "25块5毛" / "25块5角"
            Regex("""(\d+)\s*块\s*(\d+)"""),
            // 阿拉伯数字+万/千：如 "1万", "2千", "1.5万"（优先于小数匹配）
            Regex("""(\d+\.?\d*)\s*[万千]"""),
            // "128.50" 或 "128.5"
            Regex("""(\d+\.\d{1,2})"""),
            // "38 元" / "38元" / "38块" (不带角分)
            Regex("""(\d+)\s*[元块钱￥¥]"""),
            // 纯数字 "38"（没有块/元后缀的数字）
            Regex("""(?<![a-zA-Z0-9.])(\d+)(?![a-zA-Z0-9.])""")
        )

        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                val groups = match.groupValues
                val matchedText = match.value
                return when {
                    // "25块5" → groups = ["25块5", "25", "5"]
                    groups.size >= 3 && groups[1].isNotEmpty() && groups[2].isNotEmpty() -> {
                        val main = groups[1].toDoubleOrNull() ?: continue
                        val frac = groups[2].toDoubleOrNull() ?: continue
                        val fracAdjusted = if (frac >= 10) frac / 100.0 else frac / 10.0
                        val value = main + fracAdjusted
                        AmountResult(value, "%.2f".format(value))
                    }
                    // 普通数字（可能带万/千单位）
                    groups.size >= 2 -> {
                        val value = groups[1].toDoubleOrNull() ?: continue
                        // 检查是否带万/千单位
                        val multiplier = when {
                            matchedText.endsWith("万") -> 10000.0
                            matchedText.endsWith("千") -> 1000.0
                            else -> 1.0
                        }
                        val finalValue = value * multiplier
                        AmountResult(finalValue, "%.2f".format(finalValue))
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

        return AmountResult(value.toDouble(), "%.2f".format(value.toDouble()))
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

        // 正确的中文数字解析逻辑
        // 例："三千五百二十一" → 3521
        var result = 0L
        var current = 0L  // 当前累计的数字（万以下的）

        for (ch in cn) {
            when {
                ch in digitMap -> {
                    current = digitMap[ch]!!.toLong()
                }
                ch in unitMap -> {
                    val unit = unitMap[ch]!!
                    if (ch == '万') {
                        // "万" 是大单位，将之前的结果和 current 都乘以万
                        result = (result + current) * unit
                        current = 0
                    } else {
                        // 十/百/千：将 current 乘以单位加到 result
                        if (current == 0L) current = 1L  // "十" = 10
                        result += current * unit
                        current = 0
                    }
                }
            }
        }

        result += current
        return if (result > 0) result else null
    }

    // ═══════════════════════════════════════════════════════════
    //  分类匹配
    // ═══════════════════════════════════════════════════════════

    /**
     * 分类优先级权重：同长度关键词冲突时，高权重分类优先
     * 解决"淘宝外卖"匹配到"购物"而非"餐饮"的问题
     */
    private val categoryPriority = mapOf(
        "餐饮" to 10,    // 最高：外卖/餐饮语义最明确
        "交通" to 9,
        "医疗" to 8,
        "居住" to 7,
        "教育" to 6,
        "通讯" to 5,
        "美容" to 4,
        "娱乐" to 3,
        "社交" to 2,
        "购物" to 1,     // 较低：平台词（淘宝/京东）语义模糊，容易被覆盖
        "办公" to 1,
        "维修" to 1,
        "宠物" to 1,
        "捐赠" to 1,
        "其他" to 0,
    )

    /**
     * 在分类关键词库中查找匹配的分类
     * 使用最长匹配 + 分类优先级原则：
     * 1. 关键词越长，优先级越高（"淘宝外卖" > "淘宝"）
     * 2. 同长度时，高权重分类优先（"外卖"→餐饮 > "淘宝"→购物）
     */
    private fun findCategory(
        text: String,
        categoryKeywords: Map<String, List<String>>
    ): String? {
        var bestCategory: String? = null
        var bestLength = 0
        var bestPriority = 0

        for ((category, keywords) in categoryKeywords) {
            val priority = categoryPriority[category] ?: 0
            for (keyword in keywords) {
                if (text.contains(keyword)) {
                    // 长度优先；同长度时按分类优先级
                    if (keyword.length > bestLength ||
                        (keyword.length == bestLength && priority > bestPriority)) {
                        bestCategory = category
                        bestLength = keyword.length
                        bestPriority = priority
                    }
                }
            }
        }

        return bestCategory
    }
}
