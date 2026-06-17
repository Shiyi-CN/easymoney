package com.jiyixia.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jiyixia.app.JiYiXiaApp
import com.jiyixia.app.data.entity.Record
import com.jiyixia.app.domain.usecase.SmartParseUseCase
import com.jiyixia.app.ui.MainActivity
import com.jiyixia.app.util.RuleManager
import com.jiyixia.app.util.UserLearningManager
import com.jiyixia.app.util.toCents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 支付检测统一入口
 *
 * 两个检测层（通知监听 / 无障碍检测）共享此入口，
 * 统一处理去重、分类、记录、通知逻辑，避免同一笔交易重复记录。
 */
object PaymentDetector {

    private const val TAG = "PaymentDetector"
    private const val CHANNEL_ID = "payment_monitor"
    private const val DEDUP_WINDOW_MS = 3_600_000L // 1 小时去重窗口（防止同一通知跨分钟重复）

    // 受管理的协程作用域，使用 SupervisorJob 避免一个子协程失败影响其他
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 通知去重：key = "金额_分钟时间戳"，value = 首次出现时间
    // 使用 LinkedHashMap 实现 LRU 缓存，最多保留 100 条记录
    private val recentDetections = object : LinkedHashMap<String, Long>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
            return size > 100
        }
    }

    // 最近识别日志（用于调试展示）
    private val recentLogs = mutableListOf<String>()
    val detectionLogs: List<String> get() = synchronized(recentLogs) { recentLogs.toList() }

    /**
     * 处理一次支付检测
     *
     * @param source 检测来源（"通知" 或 "屏幕" 或 "短信"）
     * @param amount 金额
     * @param text 原始文本（用于分类和备注）
     * @param packageName 来源 app 包名
     * @param context Context
     * @param detectedTime 检测到的时间（通知时间/短信时间），默认为当前时间
     */
    fun processDetection(
        source: String,
        amount: Double,
        text: String,
        packageName: String,
        context: Context,
        detectedTime: Long = System.currentTimeMillis()
    ) {
        if (amount <= 0) return

        // 去重：1小时内相同金额 + 相同包名 + 相似文本只处理一次
        // 唯一键 = 金额_包名_文本前50字符（避免同一通知被系统重新投递时重复记录）
        val textSignature = text.take(50).replace(" ", "")
        val dedupKey = "${amount}_${packageName}_${textSignature.hashCode()}"
        synchronized(recentDetections) {
            val lastTime = recentDetections[dedupKey]
            if (lastTime != null && System.currentTimeMillis() - lastTime < DEDUP_WINDOW_MS) {
                Log.d(TAG, "重复检测，跳过: source=$source, key=$dedupKey")
                return
            }
            recentDetections[dedupKey] = System.currentTimeMillis()
            // 清理过期记录
            val now = System.currentTimeMillis()
            recentDetections.entries.removeIf { now - it.value > DEDUP_WINDOW_MS * 2 }
        }

        Log.d(TAG, "检测到支付: source=$source, amount=$amount, pkg=$packageName")

        val app = context.applicationContext as JiYiXiaApp
        coroutineScope.launch {
            try {
                val db = app.database
                val categories = db.categoryDao().getAll().first()
                val nameToId = categories.associate { it.name to it.id }
                val defaultCategoryId = categories.firstOrNull()?.id ?: 0L

                val parsed = SmartParseUseCase.parse(
                    text = text,
                    categoryNameToId = nameToId,
                    defaultCategoryId = defaultCategoryId
                )

                val parsedAmount = parsed?.amount ?: amount
                var categoryName = parsed?.categoryName ?: "其他"
                var type = if (parsed?.isExpense == false) 1 else 0
                var confidence = parsed?.confidence ?: 50
                val isReimbursable = parsed?.isReimbursable ?: false
                val reimbursementTarget = parsed?.reimbursementTarget ?: ""

                // 收支类型修正：基于包名+文本判断
                // 微信/支付宝主动转账给他人 = 支出（即使文本含"到账"等收入词）
                // 银行短信"到账" = 收入
                val isTransferOut = isTransferOutgoing(packageName, text)
                if (isTransferOut && type == 1) {
                    Log.d(TAG, "转账支出修正: 收入→支出 (主动转账给他人)")
                    type = 0  // 修正为支出
                    if (categoryName == "退款" || categoryName == "红包") {
                        categoryName = "其他"
                    }
                    confidence = minOf(confidence, 75)  // 降低置信度，标记为待确认
                }

                // 出行/打车 app 的支付不可能是红包/退款收入
                // 高德/滴滴等 app 通知常含"领红包"营销文案，需排除
                val rideHailingApps = setOf(
                    "com.autonavi.minimap",       // 高德地图
                    "com.baidu.BaiduMap",         // 百度地图
                    "com.didiglobal.passenger",   // 滴滴出行
                    "com.didi.global",            // 滴滴出行（国际版）
                    "com.sdu.didi.psnger",        // 滴滴出行（国内新版）
                    "com.hellobike",              // 哈啰出行
                    "com.meituan.taxi",           // 美团打车
                )
                if (packageName in rideHailingApps && type == 1) {
                    Log.d(TAG, "出行app收入修正: 收入→支出 (出行app支付不可能是红包/退款)")
                    type = 0  // 修正为支出
                    if (categoryName == "红包" || categoryName == "退款" || categoryName == "中奖") {
                        categoryName = "交通"
                    }
                    confidence = minOf(confidence, 75)
                }

                // 基于包名的场景推断：修正分类
                // 优先从 RuleManager 加载规则，回退到硬编码
                val ruleFoodApps = RuleManager.getFoodDeliveryApps()
                val ruleMultiApps = RuleManager.getMultiCategoryApps()
                val ruleFoodHints = RuleManager.getFoodDeliveryHints()
                val ruleRideHints = RuleManager.getRideHailingHints()

                val foodApps = if (ruleFoodApps.isNotEmpty()) ruleFoodApps else FOOD_DELIVERY_PACKAGES
                val multiApps = if (ruleMultiApps.isNotEmpty()) ruleMultiApps else MULTI_CATEGORY_PACKAGES
                val foodHints = if (ruleFoodHints.isNotEmpty()) ruleFoodHints else FOOD_DELIVERY_HINTS
                val rideHints = if (ruleRideHints.isNotEmpty()) ruleRideHints else RIDE_HAILING_HINTS

                // 1. 外卖专属 app → 强制分类为餐饮
                if (packageName in foodApps && categoryName != "餐饮") {
                    Log.d(TAG, "外卖app包名推断: $categoryName → 餐饮")
                    categoryName = "餐饮"
                    confidence = 90
                }
                // 2. 综合平台 → 检查外卖/出行线索
                else if (packageName in multiApps) {
                    val isFoodHint = foodHints.any { text.contains(it) }
                    val isRideHint = rideHints.any { text.contains(it) }
                    when {
                        isFoodHint && categoryName != "餐饮" -> {
                            Log.d(TAG, "综合平台外卖线索: $categoryName → 餐饮")
                            categoryName = "餐饮"
                            confidence = 85
                        }
                        isRideHint && categoryName != "交通" -> {
                            Log.d(TAG, "综合平台出行线索: $categoryName → 交通")
                            categoryName = "交通"
                            confidence = 85
                        }
                    }
                }

                // 用户自学习：检查是否学习过该商户的分类
                val learnedCategory = UserLearningManager.getLearnedCategory(context, text.take(20))
                if (learnedCategory != null && learnedCategory != categoryName) {
                    Log.d(TAG, "用户学习修正: $categoryName → $learnedCategory")
                    categoryName = learnedCategory
                    confidence = 95  // 用户学习的分类置信度最高
                }

                val category = categories.find { it.name == categoryName && it.type == type }
                    ?: categories.find { it.name == categoryName }
                    ?: categories.find { it.name == "其他" && it.type == type }
                    ?: categories.firstOrNull()

                if (category != null) {
                    val typeLabel = if (type == 1) "收入" else "支出"

                    // 置信度分级处理
                    // 高置信度（>80）→ 直接入账
                    // 中置信度（60-80）→ 标记"待确认"，通知用户确认
                    // 低置信度（<60）→ 标记"待分类"，通知用户确认
                    val isPending = confidence < 80
                    val notePrefix = when {
                        confidence >= 80 -> typeLabel
                        confidence >= 60 -> "$typeLabel·待确认"
                        else -> "$typeLabel·待分类"
                    }

                    db.recordDao().insert(
                        Record(
                            type = type,
                            amount = parsedAmount.toCents(),
                            categoryId = category.id,
                            note = "$notePrefix·$categoryName",
                            date = detectedTime,  // 使用检测到的时间（通知时间/短信时间）
                            isPendingConfirm = isPending,
                            confidence = confidence,
                            isReimbursable = isReimbursable,
                            reimbursementTarget = reimbursementTarget
                        )
                    )
                    showDetectNotification(context, parsedAmount, "$notePrefix·$categoryName", isPending)
                    addLog("$source $notePrefix ¥$parsedAmount → $categoryName (${confidence}%)")
                } else {
                    // 分类查找失败，仍记录并通知（标记为待确认）
                    val fallbackCategory = categories.firstOrNull()
                    if (fallbackCategory != null) {
                        db.recordDao().insert(
                            Record(
                                type = type,
                                amount = parsedAmount.toCents(),
                                categoryId = fallbackCategory.id,
                                note = "支出·待分类",
                                date = detectedTime,  // 使用检测到的时间
                                isPendingConfirm = true,
                                confidence = 30,
                                isReimbursable = false,
                                reimbursementTarget = ""
                            )
                        )
                    }
                    showDetectNotification(context, parsedAmount, "待分类", true)
                    addLog("$source 支出 ¥$parsedAmount → 待分类 (30%)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "处理检测失败: source=$source", e)
                addLog("错误: ${e.message}")
            }
        }
    }

    /** 显示识别到支付的通知 */
    private fun showDetectNotification(
        context: Context,
        amount: Double,
        category: String,
        isPending: Boolean
    ) {
        // Android 13+ 检查通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                Log.w(TAG, "通知权限未授予，跳过通知显示")
                return
            }
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel(nm)

        val text = if (isPending) {
            "识别到 ¥${String.format("%.2f", amount)} → $category（待确认）"
        } else {
            "识别到 ¥${String.format("%.2f", amount)} → $category"
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("记一下 - 自动记账")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        nm.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }

    private fun createChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "支付监听",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "监听支付通知并自动记账" }
            nm.createNotificationChannel(channel)
        }
    }

    private fun addLog(msg: String) {
        synchronized(recentLogs) {
            val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
            recentLogs.add(0, "$time $msg")
            if (recentLogs.size > 50) recentLogs.removeLast()
        }
    }

    /**
     * 支付相关 app 包名集合
     * 供 NotificationListener 和 AccessibilityService 共用
     */
    val PAYMENT_APP_PACKAGES = setOf(
        // 第三方支付
        "com.eg.android.AlipayGphone",      // 支付宝
        "com.tencent.mm",                     // 微信
        "com.tencent.mobileqq",               // QQ
        "com.unionpay",                       // 云闪付
        "com.tencent.wetype",                 // 微信输入法
        // 电商 / 外卖
        "com.taobao.taobao",                  // 淘宝
        "com.jingdong.app.mall",              // 京东
        "com.sankuai.meituan",                // 美团
        "com.sankuai.meituan.takeoutnew",     // 美团外卖
        "me.ele",                             // 饿了么
        "com.dianping.v1",                    // 大众点评
        "com.xunmeng.pinduoduo",              // 拼多多
        "com.ss.android.ugc.aweme",           // 抖音
        "com.sina.weibo",                     // 微博
        // 出行 / 打车
        "com.autonavi.minimap",               // 高德地图
        "com.baidu.BaiduMap",                 // 百度地图
        "com.didiglobal.passenger",           // 滴滴出行
        "com.didi.global",                    // 滴滴出行（国际版）
        "com.sdu.didi.psnger",                // 滴滴出行（国内新版）
        "com.yongche",                        // 易到用车
        "com.jingyao.driver",                 // 曹操出行
        "com.taxiservice",                    // T3出行
        "com.hellobike",                      // 哈啰出行
        "com.mobike",                         // 摩拜
        "com.meituan.taxi",                   // 美团打车
        "com.xiaojukeji.hitch",               // 滴滴顺风车
    )

    /** 银行 app 包名前缀 */
    val BANK_APP_PREFIXES = listOf(
        "com.icbc",                           // 工商银行
        "com.chinamworld",                    // 建设银行
        "com.android.bankabc",                // 农业银行
        "com.boc.bocsoft",                    // 中国银行
        "com.bankcomm",                       // 交通银行
        "com.cmbchina",                       // 招商银行
        "com.spdbccc",                        // 浦发银行
        "com.pingan",                         // 平安银行
        "com.cgbchina",                       // 广发银行
        "com.cmbc.mbank",                     // 民生银行
        "com.cib",                            // 兴业银行
        "com.cebbank",                        // 光大银行
        "com.hxb",                            // 华夏银行
        "com.bankofbeijing",                  // 北京银行
        "com.yitong.mbank.psbc",              // 邮储银行
        "com.psbc",                           // 邮储银行（另一种包名）
    )

    /** 判断是否为支付相关 app */
    fun isPaymentApp(packageName: String): Boolean {
        // 优先从 RuleManager 加载规则
        val rulePackages = RuleManager.getPaymentAppPackages()
        val rulePrefixes = RuleManager.getBankAppPrefixes()

        if (rulePackages.isNotEmpty()) {
            // 使用动态规则
            if (packageName in rulePackages) return true
            return rulePrefixes.any { packageName.startsWith(it) }
        }

        // 回退到硬编码规则
        if (packageName in PAYMENT_APP_PACKAGES) return true
        return BANK_APP_PREFIXES.any { packageName.startsWith(it) }
    }

    /**
     * 聊天类 app（微信/QQ）需要更严格的通知过滤
     * 这些 app 的通知可能是聊天消息，不能仅凭"退款"等词就触发
     */
    private val CHAT_APP_PACKAGES = setOf(
        "com.tencent.mm",       // 微信
        "com.tencent.mobileqq", // QQ
        "com.sina.weibo",       // 微博
    )

    /** 聊天类 app 必须包含的支付确认关键词 */
    private val CHAT_APP_PAYMENT_CONFIRM = listOf(
        "微信支付", "微信转账", "零钱到账",
        "支付成功", "付款成功", "转账成功",
        "商户消费", "扫码支付", "付款码",
        "QQ钱包", "QQ支付",
        // 注意：移除了"退款到账"、"已退款"、"收款到账"、"已支付"、"已付款"、"已转账"
        // 这些词在聊天消息中太常见，会导致误识别
    )

    /**
     * 外卖专属 app：这些 app 的支付几乎都是餐饮/外卖
     * 即使通知文本不含"外卖"关键词，也强制分类为餐饮
     */
    private val FOOD_DELIVERY_PACKAGES = setOf(
        "com.sankuai.meituan.takeoutnew",     // 美团外卖
        "me.ele",                             // 饿了么
        "com.meituan.taxi",                   // 美团打车（虽然不是外卖，但美团系）
    )

    /**
     * 综合平台 app：可能是外卖也可能是购物
     * 需要通过通知文本中的线索判断场景
     */
    private val MULTI_CATEGORY_PACKAGES = mapOf(
        "com.taobao.taobao" to "淘宝",
        "com.jingdong.app.mall" to "京东",
        "com.sankuai.meituan" to "美团",
        "com.dianping.v1" to "大众点评",
        "com.xunmeng.pinduoduo" to "拼多多",
        "com.ss.android.ugc.aweme" to "抖音",
    )

    /** 综合平台的外卖/餐饮线索关键词 */
    private val FOOD_DELIVERY_HINTS = listOf(
        "外卖", "饿了么", "美团外卖", "配送", "骑手",
        "餐", "饭", "菜", "吃", "喝", "奶茶", "咖啡",
        "午餐", "晚餐", "早餐", "夜宵", "宵夜",
        "汉堡", "披萨", "炸鸡", "麻辣烫", "烧烤",
        "美团买菜", "叮咚买菜", "盒马",
    )

    /** 综合平台的出行/打车线索关键词 */
    private val RIDE_HAILING_HINTS = listOf(
        "打车", "行程", "车费", "出行", "快车", "专车",
        "顺风车", "网约车", "代驾", "骑行",
    )

    /** 检查文本是否包含支付关键词 */
    fun containsPaymentKeyword(text: String): Boolean {
        // 优先从 RuleManager 加载规则
        val ruleKeywords = RuleManager.getPaymentKeywords()
        if (ruleKeywords.isNotEmpty()) {
            return ruleKeywords.any { text.contains(it) }
        }

        // 回退到硬编码规则
        val keywords = listOf(
            "支付", "付款", "转账", "扣款", "消费", "支出", "收款", "到账",
            "买单", "结算", "充值", "缴费", "还款", "汇款", "入账", "退款",
            "微信支付", "支付宝", "Alipay",
            "信用卡", "借记卡", "银行卡",
            "订单", "商户", "门店", "交易",
            "工资", "报销", "提现"
        )
        return keywords.any { text.contains(it) }
    }

    /**
     * 聊天类 app 的严格过滤
     * 微信/QQ 的通知可能是聊天消息，必须包含明确的支付确认词才处理
     *
     * 额外规则：
     * - 微信/QQ 通知的标题通常是联系人名，文本是消息内容
     * - 如果标题是"微信支付"等官方账号 → 直接通过
     * - 聊天消息中提到"退款"/"红包"/"兼职"等不应触发记账
     * - 聊天消息中提到"工资"/"报销"等也不应触发记账
     *
     * @param packageName 来源包名
     * @param text 通知全文
     * @param title 通知标题（可选，用于更精确过滤）
     * @return true 表示该通知应该被处理
     */
    fun shouldProcessFromChatApp(packageName: String, text: String, title: String? = null): Boolean {
        // 非聊天类 app 走普通过滤
        if (packageName !in CHAT_APP_PACKAGES) return true

        // 规则1：通知标题是官方支付账号 → 直接通过
        val officialAccounts = setOf(
            "微信支付", "微信转账", "支付宝", "支付宝通知",
            "QQ钱包", "QQ支付", "云闪付",
        )
        if (title != null && officialAccounts.any { title.contains(it) }) return true

        // 规则2：文本包含支付确认词 → 通过
        if (CHAT_APP_PAYMENT_CONFIRM.any { text.contains(it) }) return true

        // 规则3：聊天消息中的收入关键词不应触发记账
        // 朋友说"我退款了"/"收到红包"/"兼职赚了100" → 不应记账
        val chatMessageIncomeKeywords = listOf(
            "退款", "红包", "兼职", "工资", "报销",
            "奖金", "提成", "返现", "中奖", "彩票",
            "收入", "到账", "入账",
        )
        if (chatMessageIncomeKeywords.any { text.contains(it) }) {
            Log.d(TAG, "聊天消息包含收入关键词但缺少支付确认词，跳过: ${text.take(50)}")
            return false
        }

        return false
    }

    /**
     * 判断是否为"主动转账给他人"（支出行为）
     *
     * 微信/支付宝中用户主动转账给他人，虽然文本可能包含"到账"等收入词，
     * 但实际是支出行为。需要根据包名+文本特征判断。
     *
     * 判断规则：
     * 1. 来自微信/支付宝/QQ（非银行短信）
     * 2. 文本包含"转账给"/"向...转账"/"付款给"等主动转账特征
     * 3. 或者文本包含"转账"但不包含"收款"/"收到"等被动收入特征
     */
    private fun isTransferOutgoing(packageName: String, text: String): Boolean {
        // 银行短信和短信来源不做转账支出修正（银行"到账"通常是收入）
        if (packageName.startsWith("sms:")) return false
        if (packageName.startsWith("com.icbc") || packageName.startsWith("com.chinamworld") ||
            packageName.startsWith("com.cmbchina") || packageName.startsWith("com.bankcomm") ||
            packageName.startsWith("com.boc") || packageName.startsWith("com.android.bankabc")) {
            return false
        }

        // 明确的主动转账特征
        val outgoingKeywords = listOf(
            "转账给", "向", "付款给", "转给",
            "确认转账", "转账成功", "已转账",
        )
        val hasOutgoingKeyword = outgoingKeywords.any { text.contains(it) }

        // 明确的被动收入特征（收款方视角）
        val incomingKeywords = listOf(
            "收款", "收到", "入账", "收到转账",
            "转账收款", "好友转账", "收到红包",
        )
        val hasIncomingKeyword = incomingKeywords.any { text.contains(it) }

        // 有主动转账特征且无被动收入特征 = 转账支出
        return hasOutgoingKeyword && !hasIncomingKeyword
    }

    /** 金额正则（供两个检测层共用） */
    val AMOUNT_PATTERNS = listOf(
        Regex("""(?:支出|消费|付款|扣款|支付|转账|付款成功|支付成功|还款|缴费|退款|汇入|汇出)[^\d]*([\d,.]+)\s*元"""),
        Regex("""[¥￥]\s*([\d,.]+)"""),
        Regex("""([\d,.]+)\s*元"""),
        Regex("""(?:向|收|转给).{0,15}转(?:账|款)[^\d]*([\d,.]+)"""),
        Regex("""到账[^\d]*([\d,.]+)"""),
        Regex("""(?:交易)?金额[：:]\s*[¥￥]?\s*([\d,.]+)"""),
        Regex("""扣款[^\d]*([\d,.]+)"""),
        Regex("""入账[^\d]*([\d,.]+)"""),
    )

    /**
     * 从文本中提取金额
     * @return 提取到的金额，无法提取返回 null
     */
    fun extractAmount(text: String): Double? {
        for (pattern in AMOUNT_PATTERNS) {
            val match = pattern.find(text)
            if (match != null) {
                val numStr = match.groupValues[1].replace(",", "")
                val amount = numStr.toDoubleOrNull()
                if (amount != null && amount > 0) return amount
            }
        }
        return null
    }
}
