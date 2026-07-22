package com.jiyixia.app.service

import android.util.Log

/**
 * 场景识别器
 *
 * 解决"收支类型判断错误"和"分类错误"问题。
 *
 * 核心思路：
 * 1. 基于包名 + 场景词 + 文本特征，判断交易类型
 * 2. 区分：支出支付 / 主动转账 / 收款到账 / 退款 / 还款 / 营销
 * 3. 修正收支类型（type: 0=支出, 1=收入）
 * 4. 推断分类（餐饮/交通/购物等）
 *
 * 替代旧 PaymentDetector 中分散的 isTransferOutgoing、支付宝修正、出行修正等逻辑。
 */
object SceneDetector {

    private const val TAG = "SceneDetector"

    /**
     * 识别的场景类型
     */
    enum class SceneType {
        EXPENSE_PAYMENT,      // 支出支付（最常见：扫码消费、购物）
        TRANSFER_OUTGOING,    // 主动转账给他人（支出）
        INCOME_RECEIVE,       // 收款到账（收入）
        REFUND,               // 退款（收入）
        LOAN_REPAYMENT,       // 还款（支出，但不计入消费统计）
        SALARY_INCOME,        // 工资收入
        REIMBURSE_INCOME,     // 报销到账
        MARKETING,            // 营销（拒绝记录）
        UNKNOWN               // 未知（待用户确认）
    }

    /**
     * 场景识别结果
     */
    data class SceneResult(
        val sceneType: SceneType,
        val isExpense: Boolean,      // true=支出, false=收入
        val categoryName: String,    // 推断的分类名
        val confidence: Int,         // 置信度 0-100
        val reason: String           // 判断理由（用于日志）
    )

    /** 出行/打车 app 包名 */
    private val RIDE_HAILING_APPS = setOf(
        "com.autonavi.minimap",       // 高德地图
        "com.baidu.BaiduMap",         // 百度地图
        "com.didiglobal.passenger",   // 滴滴出行
        "com.didi.global",
        "com.sdu.didi.psnger",
        "com.yongche",
        "com.jingyao.driver",         // 曹操出行
        "com.taxiservice",            // T3出行
        "com.hellobike",
        "com.meituan.taxi",
        "com.xiaojukeji.hitch"
    )

    /** 外卖专属 app */
    private val FOOD_DELIVERY_APPS = setOf(
        "com.sankuai.meituan.takeoutnew",
        "me.ele"
    )

    /** 购物平台 app */
    private val SHOPPING_APPS = setOf(
        "com.taobao.taobao",
        "com.jingdong.app.mall",
        "com.xunmeng.pinduoduo",
        "com.ss.android.ugc.aweme"  // 抖音
    )

    /** 综合平台（需通过文本判断场景） */
    private val MULTI_CATEGORY_APPS = setOf(
        "com.sankuai.meituan",        // 美团
        "com.dianping.v1"             // 大众点评
    )

    /** 银行 app 包名前缀 */
    private val BANK_PREFIXES = listOf(
        "com.icbc", "com.chinamworld", "com.android.bankabc",
        "com.boc.bocsoft", "com.bankcomm", "com.cmbchina",
        "com.spdbccc", "com.pingan", "com.cgbchina", "com.cmbc.mbank",
        "com.cib", "com.cebbank", "com.hxb", "com.bankofbeijing",
        "com.yitong.mbank.psbc", "com.psbc"
    )

    /** 收入场景关键词 */
    private val INCOME_KEYWORDS = listOf(
        "工资", "薪水", "奖金", "年终奖", "提成", "分红",
        "利息", "收益", "理财收益", "基金分红",
        "报销到账", "报销款",
        "租金收入", "收租",
        "兼职", "稿费", "外快"
    )

    /** 退款场景关键词 */
    private val REFUND_KEYWORDS = listOf(
        "退款", "退货", "退款到账", "退钱", "返现", "已退款"
    )

    /** 还款场景关键词 */
    private val REPAYMENT_KEYWORDS = listOf(
        "还款", "信用卡还款", "贷款还款", "还房贷", "还车贷", "已还款"
    )

    /** 主动转账特征（支出） */
    private val TRANSFER_OUTGOING_KEYWORDS = listOf(
        "转账给", "向", "付款给", "转给",
        "已转账", "转账成功"
    )

    /** 被动收款特征（收入） */
    private val TRANSFER_INCOMING_KEYWORDS = listOf(
        "收款", "收到", "入账", "收到转账",
        "转账收款", "好友转账", "收到红包",
        "零钱到账", "转账到账"
    )

    /**
     * 识别场景
     *
     * @param parsedNotification 解析后的通知/屏幕内容
     * @return 场景识别结果
     */
    fun detect(parsedNotification: NotificationParser.ParsedNotification): SceneResult {
        val content = parsedNotification.content
        val packageName = content.packageName
        val allText = content.allText
        val scene = parsedNotification.scene
        val merchantName = parsedNotification.merchantName

        // 1. 营销场景（已在 parser 过滤，这里二次确认）
        if (parsedNotification.isMarketing) {
            return SceneResult(
                sceneType = SceneType.MARKETING,
                isExpense = false,
                categoryName = "其他",
                confidence = 0,
                reason = "营销通知"
            )
        }

        // 2. 退款场景（优先级高，因为是收入）
        if (REFUND_KEYWORDS.any { allText.contains(it) }) {
            return SceneResult(
                sceneType = SceneType.REFUND,
                isExpense = false,
                categoryName = "退款",
                confidence = 90,
                reason = "退款关键词"
            )
        }

        // 3. 工资/报销等明确收入场景
        if (INCOME_KEYWORDS.any { allText.contains(it) }) {
            val category = when {
                allText.contains("工资") || allText.contains("薪水") -> "工资"
                allText.contains("奖金") || allText.contains("年终奖") || allText.contains("提成") -> "奖金"
                allText.contains("利息") || allText.contains("收益") || allText.contains("理财") -> "理财"
                allText.contains("报销") -> "报销"
                allText.contains("租金") -> "租金"
                else -> "其他"
            }
            return SceneResult(
                sceneType = if (category == "报销") SceneType.REIMBURSE_INCOME else SceneType.SALARY_INCOME,
                isExpense = false,
                categoryName = category,
                confidence = 90,
                reason = "收入关键词: $category"
            )
        }

        // 4. 还款场景（支出，但属于特殊类型）
        if (REPAYMENT_KEYWORDS.any { allText.contains(it) }) {
            return SceneResult(
                sceneType = SceneType.LOAN_REPAYMENT,
                isExpense = true,
                categoryName = "还款",
                confidence = 85,
                reason = "还款关键词"
            )
        }

        // 5. 银行短信特殊处理
        if (packageName.startsWith("sms:")) {
            return detectBankSmsScene(parsedNotification)
        }

        // 6. 主动转账 vs 被动收款
        val hasOutgoing = TRANSFER_OUTGOING_KEYWORDS.any { allText.contains(it) }
        val hasIncoming = TRANSFER_INCOMING_KEYWORDS.any { allText.contains(it) }

        if (hasOutgoing && !hasIncoming) {
            // 主动转账给他人 = 支出
            return SceneResult(
                sceneType = SceneType.TRANSFER_OUTGOING,
                isExpense = true,
                categoryName = "转账",
                confidence = 80,
                reason = "主动转账特征"
            )
        }

        if (hasIncoming && !hasOutgoing) {
            // 被动收款 = 收入
            return SceneResult(
                sceneType = SceneType.INCOME_RECEIVE,
                isExpense = false,
                categoryName = "红包",
                confidence = 80,
                reason = "收款到账特征"
            )
        }

        // 7. 基于包名的场景推断（支付类 app 的默认场景是支出）
        return detectByPackage(parsedNotification)
    }

    /**
     * 银行短信场景识别
     *
     * 银行短信的"到账"通常是收入（工资、转账收款），
     * "消费/扣款"是支出。
     */
    private fun detectBankSmsScene(parsedNotification: NotificationParser.ParsedNotification): SceneResult {
        val allText = parsedNotification.content.allText

        // 收入场景
        if (allText.contains("存入") || allText.contains("到账") || allText.contains("入账")) {
            val category = when {
                allText.contains("工资") -> "工资"
                allText.contains("报销") -> "报销"
                allText.contains("利息") -> "理财"
                else -> "其他"
            }
            return SceneResult(
                sceneType = SceneType.INCOME_RECEIVE,
                isExpense = false,
                categoryName = category,
                confidence = 85,
                reason = "银行到账"
            )
        }

        // 支出场景
        return SceneResult(
            sceneType = SceneType.EXPENSE_PAYMENT,
            isExpense = true,
            categoryName = "其他",
            confidence = 75,
            reason = "银行消费/扣款"
        )
    }

    /**
     * 基于包名的场景推断
     */
    private fun detectByPackage(parsedNotification: NotificationParser.ParsedNotification): SceneResult {
        val packageName = parsedNotification.content.packageName
        val allText = parsedNotification.content.allText
        val merchantName = parsedNotification.merchantName

        // 出行/打车 app → 交通
        if (packageName in RIDE_HAILING_APPS) {
            return SceneResult(
                sceneType = SceneType.EXPENSE_PAYMENT,
                isExpense = true,
                categoryName = "交通",
                confidence = 90,
                reason = "出行app包名"
            )
        }

        // 外卖专属 app → 餐饮
        if (packageName in FOOD_DELIVERY_APPS) {
            return SceneResult(
                sceneType = SceneType.EXPENSE_PAYMENT,
                isExpense = true,
                categoryName = "餐饮",
                confidence = 90,
                reason = "外卖app包名"
            )
        }

        // 购物 app → 购物
        if (packageName in SHOPPING_APPS) {
            return SceneResult(
                sceneType = SceneType.EXPENSE_PAYMENT,
                isExpense = true,
                categoryName = "购物",
                confidence = 85,
                reason = "购物app包名"
            )
        }

        // 综合平台 → 根据文本线索判断
        if (packageName in MULTI_CATEGORY_APPS) {
            val foodHints = listOf("外卖", "餐", "饭", "菜", "吃", "喝", "奶茶", "咖啡", "配送", "骑手")
            val rideHints = listOf("打车", "行程", "车费", "出行", "快车", "专车")

            return when {
                foodHints.any { allText.contains(it) } -> SceneResult(
                    SceneType.EXPENSE_PAYMENT, true, "餐饮", 85, "综合平台外卖线索"
                )
                rideHints.any { allText.contains(it) } -> SceneResult(
                    SceneType.EXPENSE_PAYMENT, true, "交通", 85, "综合平台出行线索"
                )
                else -> SceneResult(
                    SceneType.EXPENSE_PAYMENT, true, "其他", 70, "综合平台默认支出"
                )
            }
        }

        // 银行 app → 默认支出
        if (BANK_PREFIXES.any { packageName.startsWith(it) }) {
            return SceneResult(
                sceneType = SceneType.EXPENSE_PAYMENT,
                isExpense = true,
                categoryName = "其他",
                confidence = 75,
                reason = "银行app包名"
            )
        }

        // 微信/支付宝 → 默认支出（最常见的支付场景）
        // 但通过商户名进一步推断分类
        val category = inferCategoryFromMerchant(merchantName, allText)
        return SceneResult(
            sceneType = SceneType.EXPENSE_PAYMENT,
            isExpense = true,
            categoryName = category,
            confidence = if (merchantName.isNotBlank()) 80 else 70,
            reason = "支付app默认支出" + if (merchantName.isNotBlank()) "+商户名推断" else ""
        )
    }

    /**
     * 基于商户名推断分类
     *
     * 改进：扩充商户词库，覆盖更多常见支付场景
     */
    private fun inferCategoryFromMerchant(merchantName: String, allText: String): String {
        if (merchantName.isBlank()) return "其他"

        val text = "$merchantName $allText"

        // 餐饮商户
        val foodMerchants = listOf(
            "星巴克", "瑞幸", "蜜雪", "喜茶", "奈雪", "麦当劳", "肯德基",
            "必胜客", "汉堡王", "海底捞", "美团外卖", "饿了么",
            "餐厅", "饭店", "食堂", "火锅", "奶茶", "咖啡", "饮品",
            "汉堡", "炸鸡", "烧烤", "麻辣烫", "拉面", "饺子", "包子",
            "早餐", "午餐", "晚餐", "夜宵", "小吃", "外卖", "美团", "大众点评",
            "西贝", "呷哺", "凑凑", "茶百道", "书亦烧仙草", "CoCo", "1点点",
            "便利蜂", "良品铺子", "三只松鼠", "百果园", "鲜丰水果"
        )
        if (foodMerchants.any { text.contains(it) }) return "餐饮"

        // 交通商户
        val transportMerchants = listOf(
            "滴滴", "高德打车", "美团打车", "曹操出行", "T3出行", "哈啰",
            "地铁", "公交", "加油", "停车", "高速", "ETC",
            "12306", "铁路", "机票", "航空", "携程", "去哪儿", "飞猪",
            "共享单车", "青桔", "美团单车", "哈啰单车",
            "中石油", "中石化", "壳牌", "加油站"
        )
        if (transportMerchants.any { text.contains(it) }) return "交通"

        // 购物商户
        val shoppingMerchants = listOf(
            "淘宝", "京东", "拼多多", "天猫", "苏宁", "超市", "便利店",
            "唯品会", "当当", "得物", "闲鱼", "转转", "1688",
            "永辉", "沃尔玛", "家乐福", "大润发", "盒马", "叮咚买菜",
            "美团优选", "多多买菜", "淘菜菜", "每日优鲜",
            "名创优品", "无印良品", "优衣库", "ZARA", "H&M"
        )
        if (shoppingMerchants.any { text.contains(it) }) return "购物"

        // 医疗商户
        val medicalMerchants = listOf(
            "医院", "药店", "诊所", "牙科", "眼科",
            "大药房", "仁和", "同仁堂", "海王星辰", "益丰", "老百姓",
            "健康", "体检", "口腔", "皮肤", "中医", "西医"
        )
        if (medicalMerchants.any { text.contains(it) }) return "医疗"

        // 娱乐商户
        val entertainmentMerchants = listOf(
            "电影", "影院", "万达", "CGV", "大地影院",
            "KTV", "密室", "剧本杀", "网吧", "网咖",
            "游戏", "Steam", " PlayStation", "Xbox", "任天堂",
            "抖音", "快手", "B站", "bilibili",
            "演唱会", "音乐节", "话剧", "展览", "博物馆"
        )
        if (entertainmentMerchants.any { text.contains(it) }) return "娱乐"

        // 居住/生活商户
        val livingMerchants = listOf(
            "水电", "燃气", "物业", "房租", "租金",
            "国家电网", "自来水", "华润燃气",
            "美容", "美发", "理发", "美甲", "SPA", "按摩",
            "干洗", "洗衣", "家政", "保洁"
        )
        if (livingMerchants.any { text.contains(it) }) return "居家"

        // 通讯商户
        val telecomMerchants = listOf(
            "移动", "联通", "电信", "话费", "宽带",
            "10086", "10010", "10000"
        )
        if (telecomMerchants.any { text.contains(it) }) return "通讯"

        return "其他"
    }
}
