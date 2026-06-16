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
    private const val DEDUP_WINDOW_MS = 60_000L // 1 分钟去重窗口

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
     * @param source 检测来源（"通知" 或 "屏幕"）
     * @param amount 金额
     * @param text 原始文本（用于分类和备注）
     * @param packageName 来源 app 包名
     * @param context Context
     */
    fun processDetection(
        source: String,
        amount: Double,
        text: String,
        packageName: String,
        context: Context
    ) {
        if (amount <= 0) return

        // 去重：同一分钟内相同金额只处理一次
        val minuteTimestamp = System.currentTimeMillis() / 60_000 * 60_000
        val dedupKey = "${amount}_$minuteTimestamp"
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
                val type = if (parsed?.isExpense == false) 1 else 0
                var confidence = parsed?.confidence ?: 50
                val isReimbursable = parsed?.isReimbursable ?: false
                val reimbursementTarget = parsed?.reimbursementTarget ?: ""

                // 基于包名的场景推断：修正分类
                // 1. 外卖专属 app → 强制分类为餐饮
                if (packageName in FOOD_DELIVERY_PACKAGES && categoryName != "餐饮") {
                    Log.d(TAG, "外卖app包名推断: $categoryName → 餐饮")
                    categoryName = "餐饮"
                    confidence = 90
                }
                // 2. 综合平台 → 检查外卖/出行线索
                else if (packageName in MULTI_CATEGORY_PACKAGES) {
                    val isFoodHint = FOOD_DELIVERY_HINTS.any { text.contains(it) }
                    val isRideHint = RIDE_HAILING_HINTS.any { text.contains(it) }
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

                val category = categories.find { it.name == categoryName && it.type == type }
                    ?: categories.find { it.name == categoryName }
                    ?: categories.find { it.name == "其他" && it.type == type }
                    ?: categories.firstOrNull()

                if (category != null) {
                    val isPending = confidence < 80
                    val typeLabel = if (type == 1) "收入" else "支出"
                    db.recordDao().insert(
                        Record(
                            type = type,
                            amount = parsedAmount.toCents(),
                            categoryId = category.id,
                            note = "$typeLabel·$categoryName",
                            date = System.currentTimeMillis(),
                            isPendingConfirm = isPending,
                            confidence = confidence,
                            isReimbursable = isReimbursable,
                            reimbursementTarget = reimbursementTarget
                        )
                    )
                    showDetectNotification(context, parsedAmount, "$typeLabel·$categoryName", isPending)
                    addLog("$source $typeLabel ¥$parsedAmount → $categoryName (${confidence}%)")
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
                                date = System.currentTimeMillis(),
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

    /** 聊天类 app 必须包含的支付确认关键词（二选一） */
    private val CHAT_APP_PAYMENT_CONFIRM = listOf(
        "微信支付", "微信转账", "收款到账", "零钱到账",
        "支付成功", "付款成功", "转账成功", "退款到账",
        "已支付", "已付款", "已转账", "已退款",
        "商户消费", "扫码支付", "付款码",
        "QQ钱包", "QQ支付",
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
     * @return true 表示该通知应该被处理
     */
    fun shouldProcessFromChatApp(packageName: String, text: String): Boolean {
        if (packageName !in CHAT_APP_PACKAGES) return true // 非聊天类 app 走普通过滤
        return CHAT_APP_PAYMENT_CONFIRM.any { text.contains(it) }
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
