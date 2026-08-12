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
        val reimbursementTarget: String = "",     // 报销对象
        val dateOffset: Int = 0       // 相对今天的偏移天数（-3=大前天,-2=前天,-1=昨天,0=今天,1=明天,2=后天,3=大后天）
    )

    // ═══════════════════════════════════════════════════════════
    //  分类关键词库（按设计文档 P4 第89-93行的本地规则引擎思想）
    //  支出类
    // ═══════════════════════════════════════════════════════════

    private val expenseCategories = mapOf(
        "餐饮" to listOf(
            "午餐", "午饭", "晚餐", "晚饭", "早餐", "早饭", "吃饭", "外卖",
            "餐厅", "饭店", "食堂", "火锅", "奶茶", "咖啡", "饮品", "饮料",
            "水果", "零食", "小吃", "夜宵", "宵夜", "烧烤", "串串", "米线",
            "星巴克", "麦当劳", "肯德基", "瑞幸", "蜜雪", "喜茶", "奈雪",
            "美团外卖", "饿了么", "盒马", "叮咚", "买菜", "海底捞", "必胜客",
            "KFC", "M记", "便利店", "全家", "罗森",
            // 平台+外卖复合关键词（优先于"淘宝"匹配到"购物"）
            "淘宝外卖", "京东外卖", "抖音外卖", "拼多多外卖",
            // 通用食物词（移除单字"吃/喝/饭/菜/酒/茶/奶/水/汤/粥"，避免误识别）
            "蛋糕", "甜品", "冰淇淋", "巧克力", "饼干", "薯片",
            "面条", "米粉", "汤面", "粥铺", "茶饮", "奶茶店"
        ),
        "交通" to listOf(
            "打车", "地铁", "公交", "加油", "停车", "滴滴", "出租车",
            "高铁", "火车", "机票", "飞机", "骑行", "共享单车", "哈啰",
            "摩拜", "青桔", "顺风车", "网约车", "高速", "过路费",
            "出行", "导航", "充电", "洗车", "保养", "车险",
            "代驾", "租车", "油费", "充电桩", "ETC",
            // 打车/出行场景增强（移除单字"车"，避免"购物车"误识别）
            "行程", "车费", "路费", "打车费", "出行费",
            "快车", "专车", "拼车", "优享", "豪华车",
            "曹操出行", "T3出行", "神州", "首汽", "嘀嗒",
            "高德打车", "百度打车", "美团打车",
            "地铁票", "公交卡", "一卡通", "交通卡"
        ),
        "购物" to listOf(
            "超市", "百货", "商场", "淘宝", "京东", "拼多多",
            "衣服", "鞋子", "裤子", "日用品", "化妆品", "护肤品",
            "电器", "手机", "电脑", "数码", "快递", "物流", "配送",
            "购物", "下单", "订单", "618", "双11", "双十一",
            "天猫", "苏宁", "唯品会", "抖音商城", "小红书"
            // 移除单字"买/购/件"，避免"买单"误识别为购物
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
            // 移除单字"玩/乐/唱/看"，避免误识别
            "爱奇艺", "腾讯视频", "优酷",
            "B站", "网飞", "Netflix", "Spotify", "Apple Music",
            "Steam", "PS5", "Switch", "Xbox"
        ),
        "医疗" to listOf(
            "医院", "药店", "挂号", "看病", "门诊", "体检",
            "诊所", "牙科", "眼科", "住院", "检查", "手术",
            "生病", "感冒", "发烧", "咳嗽", "医保", "保健品",
            "维生素", "钙片", "口罩", "消毒液"
            // 移除单字"药"，避免"药膳"误识别
        ),
        "教育" to listOf(
            "课程", "培训", "考试", "学费", "资料", "文具",
            "补习", "学习", "教材", "图书", "报名", "考证",
            "考研", "雅思", "托福", "PMP",
            "网课", "知乎", "得到", "极客时间"
            // 移除单字"书/学/教/读"，避免"书店"误识别
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
            "猫粮", "狗粮", "宠物", "宠物医院", "疫苗",
            "驱虫", "绝育", "宠物店", "猫砂", "宠物玩具", "鱼缸",
            "鸟笼", "仓鼠", "兔子"
            // 移除单字"猫/狗"，避免"猫粮"重复匹配
        ),
        "办公" to listOf(
            "办公", "打印", "复印", "文具", "笔记本", "办公用品",
            "快递费", "差旅", "出差"
            // 移除单字"笔/纸"
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
            "杂项"
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
     * 统一入口：语音输入、手动输入、通知自动记账都调用此 UseCase
     *
     * 参考方案（多方调研）：
     * - 一木记账：自然语言解析"昨天吃饭花了50" + 自定义关键词"过早"→"早餐"
     * - JioNLP：中文金额提取标准（支持中文数字、口语化、混合格式）
     * - CSDN记账App：pending 待确认机制、事件驱动解耦
     *
     * @param text 识别后的文本，如 "午餐 38 块"、"打车 25 块 5"、"昨天吃饭花了50"
     * @param categoryNameToId 分类名称 → ID 的映射（由调用方提供）
     * @param defaultCategoryId 默认分类 ID（无法识别时使用）
     * @param keywordMappings 用户自定义关键词映射（关键词 → 分类名），优先级最高。
     *        参考一木记账的自定义关键词功能：用户可将"过早"绑定至"早餐"分类，
     *        通过喂给系统个人习惯语料，建立高容错率的录入环境。
     */
    fun parse(
        text: String,
        categoryNameToId: Map<String, Long>,
        defaultCategoryId: Long = 0L,
        keywordMappings: Map<String, String> = emptyMap()
    ): ParsedResult? {
        if (text.isBlank()) return null

        val cleaned = text.trim().replace("。", "").replace("，", ",")

        // 1. 提取金额
        val amountResult = extractAmount(cleaned) ?: return null

        // 1.1 提取时间偏移（一木记账核心能力："昨天吃饭花了50" → dateOffset = -1）
        val dateOffset = extractDateOffset(cleaned)

        // 2. 检测自定义关键词（用户配置的，优先级最高，参考一木记账）
        //    例：用户配置"过早"→"餐饮"，则"过早 15" → 分类=餐饮
        val customCategory = findCustomKeywordCategory(cleaned, keywordMappings)

        // 3. 检测支出分类（自定义关键词优先于内置关键词）
        val expenseCategory = customCategory ?: findCategory(cleaned, expenseCategories)

        // 4. 检测收入分类（自定义关键词不覆盖收入识别，避免"报销"等被误映射）
        val incomeCategory = findCategory(cleaned, incomeCategories)

        // 5. 判断是否包含"报销"关键词
        val hasReimbursementKeyword = listOf("报销", "可报销", "能报销", "要报销").any { cleaned.contains(it) }

        // 5.1 检测"XX公司/分公司/单位/部门"模式（自动识别为可报销）
        val companyReimbursementResult = detectCompanyReimbursement(cleaned)
        val hasCompanyPattern = companyReimbursementResult.first

        // 6. 明确的收入关键词（"到账"、"收入"、"收到"等），表示这是真正的收入而非可报销支出
        val hasExplicitIncomeKeyword = listOf("到账", "收入", "收到", "入账", "进账").any { cleaned.contains(it) }

        // 7. 报销关键词优先：如果有报销关键词且没有明确收入关键词，走支出+可报销路径
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
                note = cleanNote(cleaned, amountResult.matchedText),
                isExpense = true,
                confidence = 85,
                isReimbursable = true,
                reimbursementTarget = reimbursementResult.second,
                dateOffset = dateOffset
            )
        }

        // 7.1 公司名模式：如果有"XX公司/分公司"模式且没有明确收入关键词，走支出+可报销路径
        if (hasCompanyPattern && !hasExplicitIncomeKeyword) {
            val category = expenseCategory ?: "其他"
            return ParsedResult(
                amount = amountResult.value,
                amountText = amountResult.text,
                categoryName = category,
                categoryId = categoryNameToId[category] ?: defaultCategoryId,
                note = cleanNote(cleaned, amountResult.matchedText),
                isExpense = true,
                confidence = 80,
                isReimbursable = true,
                reimbursementTarget = companyReimbursementResult.second,
                dateOffset = dateOffset
            )
        }

        // 8. 收入分类（如"工资"、"报销到账"等）
        // 优先匹配收入分类，解决"红包"等歧义关键词问题
        if (incomeCategory != null) {
            return ParsedResult(
                amount = amountResult.value,
                amountText = amountResult.text,
                categoryName = incomeCategory,
                categoryId = categoryNameToId[incomeCategory] ?: defaultCategoryId,
                note = cleanNote(cleaned, amountResult.matchedText),
                isExpense = false,
                confidence = 85,
                isReimbursable = false,
                reimbursementTarget = "",
                dateOffset = dateOffset
            )
        }

        // 9. 支出分类 + 报销关键词 = 支出+可报销
        if (expenseCategory != null && hasReimbursementKeyword) {
            val reimbursementResult = detectReimbursement(cleaned)
            return ParsedResult(
                amount = amountResult.value,
                amountText = amountResult.text,
                categoryName = expenseCategory,
                categoryId = categoryNameToId[expenseCategory] ?: defaultCategoryId,
                note = cleanNote(cleaned, amountResult.matchedText),
                isExpense = true,
                confidence = 85,
                isReimbursable = true,
                reimbursementTarget = reimbursementResult.second,
                dateOffset = dateOffset
            )
        }

        // 10. 纯支出（没有报销关键词）
        if (expenseCategory != null) {
            return ParsedResult(
                amount = amountResult.value,
                amountText = amountResult.text,
                categoryName = expenseCategory,
                categoryId = categoryNameToId[expenseCategory] ?: defaultCategoryId,
                note = cleanNote(cleaned, amountResult.matchedText),
                isExpense = true,
                confidence = 80,
                isReimbursable = false,
                reimbursementTarget = "",
                dateOffset = dateOffset
            )
        }

        // 11. 只识别到金额，分到"其他"
        return ParsedResult(
            amount = amountResult.value,
            amountText = amountResult.text,
            categoryName = "其他",
            categoryId = categoryNameToId["其他"] ?: defaultCategoryId,
            note = cleanNote(cleaned, amountResult.matchedText),
            isExpense = true,
            confidence = 50,
            isReimbursable = false,
            reimbursementTarget = "",
            dateOffset = dateOffset
        )
    }

    /**
     * 提取时间偏移（相对今天）
     * 一木记账核心能力：支持"昨天吃饭花了50"这类口语化时间表达
     * @return 偏移天数（-3 到 +3），默认 0（今天）
     */
    private fun extractDateOffset(text: String): Int {
        // 注意顺序：必须先检查"大前天/大后天"再检查"前天/后天"，否则会被部分匹配
        return when {
            text.contains("大前天") -> -3
            text.contains("前天") -> -2
            text.contains("昨天") -> -1
            text.contains("大后天") -> 3
            text.contains("后天") -> 2
            text.contains("明天") -> 1
            else -> 0  // "今天"或未识别都算今天
        }
    }

    /**
     * 查找自定义关键词匹配的分类（用户配置的，优先级最高）
     * 参考一木记账的自定义关键词功能：用户可将"过早"绑定至"早餐/餐饮"
     * @param text 输入文本
     * @param keywordMappings 关键词 → 分类名 映射
     * @return 匹配到的分类名，未匹配返回 null
     */
    private fun findCustomKeywordCategory(
        text: String,
        keywordMappings: Map<String, String>
    ): String? {
        if (keywordMappings.isEmpty()) return null
        var bestCategory: String? = null
        var bestLength = 0
        for ((keyword, categoryName) in keywordMappings) {
            if (keyword.isBlank() || categoryName.isBlank()) continue
            if (text.contains(keyword)) {
                // 最长匹配优先（与内置关键词一致）
                if (keyword.length > bestLength) {
                    bestCategory = categoryName
                    bestLength = keyword.length
                }
            }
        }
        return bestCategory
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

    private data class AmountResult(val value: Double, val text: String, val matchedText: String = "")

    private fun extractAmount(text: String): AmountResult? {
        // ═══════════════════════════════════════════════════════════
        //  金额提取策略（参考一木记账 + JioNLP）
        //  核心规则：若短句含多个数字，仅提取最后一个作为金额（一木记账解析边界）
        //  例："买菜15打车20" → 取 20，而非 15
        // ═══════════════════════════════════════════════════════════

        // 0. 完整中文金额优先（支持角分）："二十九块伍毛一分" → 29.51
        tryParseFullChineseAmount(text)?.let { return it }

        // 1. 角分模式（完整版，含"分"）：
        //    "25块5毛1" → 25.51 / "25块5角1分" → 25.51 / "25元5角1分" → 25.51
        val jiaoFenFullPattern = Regex("""(\d+)\s*[块元]\s*(\d+)\s*[毛角]\s*(\d+)\s*分?""")
        jiaoFenFullPattern.find(text)?.let { match ->
            val main = match.groupValues[1].toDoubleOrNull() ?: 0.0
            val jiao = match.groupValues[2].toDoubleOrNull() ?: 0.0
            val fen = match.groupValues[3].toDoubleOrNull() ?: 0.0
            val value = main + jiao / 10.0 + fen / 100.0
            return AmountResult(value, "%.2f".format(value), match.value)
        }

        // 1.1 只有角（无"分"）："25块5毛" / "25块5角" / "25块5" / "25块51"
        val jiaoOnlyPattern = Regex("""(\d+)\s*[块元]\s*(\d+)\s*[毛角]?""")
        jiaoOnlyPattern.find(text)?.let { match ->
            val main = match.groupValues[1].toDoubleOrNull() ?: 0.0
            val frac = match.groupValues[2].toDoubleOrNull() ?: 0.0
            val fracAdjusted = if (frac >= 10) frac / 100.0 else frac / 10.0
            val value = main + fracAdjusted
            return AmountResult(value, "%.2f".format(value), match.value)
        }

        // 2. 带万/千单位的数字："1万" / "2千" / "1.5万"
        //    多个时取最后一个（一木记账规则）
        val wanQianPattern = Regex("""(\d+\.?\d*)\s*[万千]""")
        val wanQianMatches = wanQianPattern.findAll(text).toList()
        if (wanQianMatches.isNotEmpty()) {
            val lastMatch = wanQianMatches.last()
            val value = lastMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            val multiplier = if (lastMatch.value.endsWith("万")) 10000.0 else 1000.0
            val finalValue = value * multiplier
            return AmountResult(finalValue, "%.2f".format(finalValue), lastMatch.value)
        }

        // 3. 小数格式："128.50" / "25.5" / "29.51"
        //    多个时取最后一个
        val decimalPattern = Regex("""(\d+\.\d{1,2})""")
        val decimalMatches = decimalPattern.findAll(text).toList()
        if (decimalMatches.isNotEmpty()) {
            val lastMatch = decimalMatches.last()
            val value = lastMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            return AmountResult(value, "%.2f".format(value), lastMatch.value)
        }

        // 4. 带货币后缀的数字："38元" / "38块" / "38块钱" / "￥38"
        //    多个时取最后一个（"买菜15块打车20块" → 取 20）
        val currencyPattern = Regex("""(\d+)\s*[元块钱￥¥]""")
        val currencyMatches = currencyPattern.findAll(text).toList()
        if (currencyMatches.isNotEmpty()) {
            val lastMatch = currencyMatches.last()
            val value = lastMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            return AmountResult(value, "%.2f".format(value), lastMatch.value)
        }

        // 5. 纯整数："38"（无任何后缀）
        //    一木记账核心规则：多数字取末位（"买菜15打车20" → 取 20）
        val pureNumberPattern = Regex("""(?<![a-zA-Z0-9.])(\d+)(?![a-zA-Z0-9.])""")
        val pureMatches = pureNumberPattern.findAll(text).toList()
        if (pureMatches.isNotEmpty()) {
            val lastMatch = pureMatches.last()
            val value = lastMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            return AmountResult(value, "%.2f".format(value), lastMatch.value)
        }

        // 6. 尝试简单中文数字金额（无角分）："三十八" → 38
        return tryParseSimpleChineseAmount(text)
    }

    /**
     * 完整中文金额解析（支持角分）
     * "二十九块伍毛一分" → 29.51
     * "二十九元伍角一分" → 29.51
     * "二十九块伍毛" → 29.5
     * "三十八" → 38（无角分时返回 null，交给 tryParseSimpleChineseAmount）
     */
    private fun tryParseFullChineseAmount(text: String): AmountResult? {
        val cnDigitChars = "零一二两三四五六七八九伍陆柒捌玖壹贰叁肆"
        val cnUnitChars = "十百千万拾佰仟"
        val allCnChars = cnDigitChars + cnUnitChars

        // 模式1：完整角分 "二十九块伍毛一分" / "二十九元伍角一分"
        val fullJiaoFen = Regex(
            """([${allCnChars}]+)\s*[块元]\s*([${cnDigitChars}])\s*[毛角]\s*([${cnDigitChars}])\s*分?"""
        )
        fullJiaoFen.find(text)?.let { match ->
            val yuan = chineseToArabic(match.groupValues[1]) ?: return null
            val jiao = chineseDigitToInt(match.groupValues[2])
            val fen = chineseDigitToInt(match.groupValues[3])
            val value = yuan.toDouble() + jiao / 10.0 + fen / 100.0
            return AmountResult(value, "%.2f".format(value), match.value)
        }

        // 模式2：只有角 "二十九块伍毛" / "二十九元伍角" / "二十九块伍"
        val jiaoOnly = Regex(
            """([${allCnChars}]+)\s*[块元]\s*([${cnDigitChars}])\s*[毛角]?"""
        )
        jiaoOnly.find(text)?.let { match ->
            val yuan = chineseToArabic(match.groupValues[1]) ?: return null
            val jiao = chineseDigitToInt(match.groupValues[2])
            val value = yuan.toDouble() + jiao / 10.0
            return AmountResult(value, "%.2f".format(value), match.value)
        }

        return null
    }

    /**
     * 简单中文数字金额解析（无角分）
     * 支持 "三十八"、"一百二"、"一千五" 等常见口语表达
     */
    private fun tryParseSimpleChineseAmount(text: String): AmountResult? {
        val cnDigitChars = "零一二两三四五六七八九伍陆柒捌玖壹贰叁肆"
        val cnUnitChars = "十百千万拾佰仟"
        val allCnChars = cnDigitChars + cnUnitChars

        // 用正则找中文数字段
        val cnNumPattern = Regex("""[${allCnChars}]+""")
        val match = cnNumPattern.find(text) ?: return null

        val value = chineseToArabic(match.value) ?: return null
        return AmountResult(value.toDouble(), "%.2f".format(value.toDouble()), match.value)
    }

    /**
     * 单个中文数字字符 → Int（支持大小写）
     */
    private fun chineseDigitToInt(ch: String): Int {
        val map = mapOf(
            "零" to 0, "一" to 1, "二" to 2, "三" to 3, "四" to 4,
            "五" to 5, "六" to 6, "七" to 7, "八" to 8, "九" to 9,
            "两" to 2,
            "伍" to 5, "陆" to 6, "柒" to 7, "捌" to 8, "玖" to 9,
            "壹" to 1, "贰" to 2, "叁" to 3, "肆" to 4
        )
        return map[ch] ?: 0
    }

    /**
     * 中文数字 → 阿拉伯数字（支持"三千五百二十一"→3521，含大写数字）
     */
    private fun chineseToArabic(cn: String): Long? {
        if (cn.isEmpty()) return null

        val digitMap = mapOf(
            '零' to 0, '一' to 1, '二' to 2, '三' to 3, '四' to 4,
            '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
            '两' to 2,
            '伍' to 5, '陆' to 6, '柒' to 7, '捌' to 8, '玖' to 9,
            '壹' to 1, '贰' to 2, '叁' to 3, '肆' to 4
        )
        val unitMap = mapOf(
            '十' to 10L, '百' to 100L, '千' to 1000L, '万' to 10000L,
            '拾' to 10L, '佰' to 100L, '仟' to 1000L
        )

        var result = 0L
        var current = 0L

        for (ch in cn) {
            when {
                ch in digitMap -> {
                    current = digitMap[ch]!!.toLong()
                }
                ch in unitMap -> {
                    val unit = unitMap[ch]!!
                    if (ch == '万') {
                        result = (result + current) * unit
                        current = 0
                    } else {
                        if (current == 0L) current = 1L
                        result += current * unit
                        current = 0
                    }
                }
            }
        }

        result += current
        return if (result > 0) result else null
    }

    /**
     * 从原始文本中剔除金额部分，生成干净的备注
     * 例："午饭二十九块伍毛一分" → "午饭"
     *     "午餐38" → "午餐"
     *     "打车25块5毛1" → "打车"
     */
    private fun cleanNote(text: String, matchedAmountText: String): String {
        if (matchedAmountText.isBlank()) return text

        // 从原始文本中删除金额匹配到的部分
        var note = text.replace(matchedAmountText, "")

        // 清理多余的空格和首尾标点
        note = note.trim()
        note = note.replace(Regex("""^[\s,，。、！!?？]+|[\s,，。、！!?？]+$"""), "")

        // 如果清洗后为空，返回原始文本（保留原始信息）
        if (note.isBlank()) return text

        return note
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
