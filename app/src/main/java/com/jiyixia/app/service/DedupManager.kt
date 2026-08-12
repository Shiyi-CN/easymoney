package com.jiyixia.app.service

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * 智能去重管理器
 *
 * 解决"同一条交易被识别多次"的核心问题。
 *
 * 设计要点：
 * 1. 跨来源去重：通知/屏幕/短信三方共享同一去重表
 *    - 一笔支付发生时，通知和屏幕可能同时触发，必须去重
 * 2. 去重键稳定：基于"金额 + 来源类型 + 时间窗口"，不依赖文本哈希
 *    - 屏幕文本含动态时间戳，文本哈希不稳定
 * 3. 同来源窗口 2 分钟：同来源同金额 2 分钟内只处理一次
 *    - 覆盖通知延迟 + 屏幕多次触发，同时避免误杀连续支付
 * 4. 跨来源窗口 5 分钟：不同来源同金额 5 分钟内只记录一次
 *    - 用户可能在支付页面停留数分钟，通知先到、屏幕后触发
 *    - 2 分钟太短会导致通知+屏幕双重记录
 * 5. recorded 标记：只有成功入库的检测才会阻止其他来源记录，
 *    避免"通知被营销过滤拒绝 → 屏幕也被跨来源去重跳过"的双重拒绝问题
 * 6. 持久化：APP 被杀重启后，去重缓存不丢失，防止重启后重复记录
 */
object DedupManager {

    private const val TAG = "DedupManager"

    /** 同来源去重窗口：2 分钟 */
    private const val DEDUP_WINDOW_MS = 2 * 60 * 1000L

    /** 跨来源去重窗口：5 分钟（用户可能在支付页停留数分钟） */
    private const val CROSS_SOURCE_DEDUP_WINDOW_MS = 5 * 60 * 1000L

    /** 缓存上限：避免内存无限增长 */
    private const val MAX_CACHE_SIZE = 200

    /** 持久化文件名 */
    private const val PREFS_NAME = "dedup_cache"
    private const val PREFS_KEY = "entries"

    private var appContext: Context? = null

    /**
     * 去重条目
     *
     * @param amount 金额（统一为分，避免浮点比较问题）
     * @param sourceType 来源类型："notification" / "screen" / "sms"
     * @param appSignature app 签名（包名或短信发送方），用于区分不同来源
     * @param firstSeen 首次出现时间
     * @param recorded 是否已成功记录到数据库（false=仅检测到，未入库）
     */
    private data class DedupEntry(
        val amount: Long,
        val sourceType: String,
        val appSignature: String,
        val firstSeen: Long,
        val recorded: Boolean = false
    )

    // 线程安全的去重缓存：key = "amount_sourceType_appSignature"
    private val dedupCache = ConcurrentHashMap<String, DedupEntry>()

    /**
     * 初始化：加载持久化的去重缓存（APP 启动时调用）
     * 防止 APP 被杀重启后，同一笔交易被重复记录
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        loadFromPrefs()
        Log.d(TAG, "DedupManager 初始化完成，加载 ${dedupCache.size} 条缓存")
    }

    /**
     * 检查是否为重复检测（同来源去重，窗口 2 分钟）
     *
     * @param amount 金额（元）
     * @param sourceType 来源类型："notification" / "screen" / "sms"
     * @param appSignature app 签名（包名或短信发送方）
     * @return true 表示重复，应该跳过；false 表示新检测，已记录
     */
    fun isDuplicate(
        amount: Double,
        sourceType: String,
        appSignature: String
    ): Boolean {
        val amountCents = (amount * 100).toLong()
        val now = System.currentTimeMillis()
        val key = "${amountCents}_${sourceType}_${appSignature}"

        // 清理过期条目（懒清理，每次调用检查少量条目）
        cleanupExpired(now)

        val existing = dedupCache[key]
        if (existing != null && now - existing.firstSeen < DEDUP_WINDOW_MS) {
            Log.d(TAG, "重复检测跳过: amount=$amount, source=$sourceType, app=$appSignature, " +
                    "age=${now - existing.firstSeen}ms")
            return true
        }

        // 记录新条目（标记为未入库，等真正写入 DB 后才标记 recorded=true）
        dedupCache[key] = DedupEntry(amountCents, sourceType, appSignature, now, recorded = false)
        saveToPrefs()
        return false
    }

    /**
     * 跨来源去重检查：检查是否其他来源已经【成功记录】过此金额
     *
     * 重要：只对 recorded=true 的条目判重。
     * 通知被营销过滤拒绝时，recorded 仍为 false，不会阻止屏幕检测记录。
     * 避免"通知被拒绝 → 屏幕也被跨来源去重跳过"的双重拒绝问题。
     *
     * 使用 5 分钟跨来源窗口（比同来源更长），因为用户可能在支付页面停留数分钟，
     * 通知先到、屏幕后触发，2 分钟窗口会导致双重记录。
     *
     * @param amount 金额（元）
     * @param excludeSourceType 排除的来源类型（不检查此来源）
     * @return true 表示其他来源已成功记录过此金额，应该跳过
     */
    fun isDuplicateAcrossSources(
        amount: Double,
        excludeSourceType: String? = null
    ): Boolean {
        val amountCents = (amount * 100).toLong()
        val now = System.currentTimeMillis()

        // 只检查已成功入库的条目，避免营销误判导致的双重拒绝
        for ((_, entry) in dedupCache) {
            if (entry.amount == amountCents &&
                entry.recorded &&
                entry.sourceType != excludeSourceType &&
                now - entry.firstSeen < CROSS_SOURCE_DEDUP_WINDOW_MS) {
                Log.d(TAG, "跨来源去重: amount=$amount, 已由 ${entry.sourceType} 记录入库, " +
                        "age=${now - entry.firstSeen}ms")
                return true
            }
        }
        return false
    }

    /**
     * 标记某条检测已成功记录到数据库
     *
     * 必须在 Record 入库成功后调用，使跨来源去重能识别此条记录。
     * 如果解析被拒绝（营销过滤/金额无效），不要调用此方法，
     * 这样其他来源仍有机会记录。
     *
     * @param amount 金额（元）
     * @param sourceType 来源类型
     * @param appSignature app 签名
     */
    fun markRecorded(
        amount: Double,
        sourceType: String,
        appSignature: String
    ) {
        val amountCents = (amount * 100).toLong()
        val key = "${amountCents}_${sourceType}_${appSignature}"
        val existing = dedupCache[key] ?: return
        dedupCache[key] = existing.copy(recorded = true)
        saveToPrefs()
    }

    /**
     * 清理过期条目
     */
    private fun cleanupExpired(now: Long) {
        if (dedupCache.size < MAX_CACHE_SIZE) return

        // 超过上限时，清理所有过期条目（用跨来源窗口的 2 倍作为清理阈值）
        val expiredKeys = dedupCache.entries
            .filter { now - it.value.firstSeen > CROSS_SOURCE_DEDUP_WINDOW_MS * 2 }
            .map { it.key }

        expiredKeys.forEach { dedupCache.remove(it) }

        // 如果清理后仍超上限，移除最旧的条目
        if (dedupCache.size >= MAX_CACHE_SIZE) {
            dedupCache.entries
                .sortedBy { it.value.firstSeen }
                .take(dedupCache.size - MAX_CACHE_SIZE + 50)
                .forEach { dedupCache.remove(it.key) }
        }
    }

    /**
     * 清空去重缓存（用于测试或用户手动重置）
     */
    fun clear() {
        dedupCache.clear()
        saveToPrefs()
    }

    /**
     * 获取当前缓存大小（用于调试）
     */
    fun cacheSize(): Int = dedupCache.size

    // ==================== 持久化 ====================

    /**
     * 从 SharedPreferences 加载去重缓存
     * 只加载未过期的条目（跨来源窗口内）
     */
    private fun loadFromPrefs() {
        val ctx = appContext ?: return
        val now = System.currentTimeMillis()
        try {
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(PREFS_KEY, null) ?: return
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val firstSeen = obj.getLong("firstSeen")
                // 只恢复跨来源窗口内的条目
                if (now - firstSeen < CROSS_SOURCE_DEDUP_WINDOW_MS) {
                    val key = obj.getString("key")
                    val entry = DedupEntry(
                        amount = obj.getLong("amount"),
                        sourceType = obj.getString("sourceType"),
                        appSignature = obj.getString("appSignature"),
                        firstSeen = firstSeen,
                        recorded = obj.getBoolean("recorded")
                    )
                    dedupCache[key] = entry
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "加载去重缓存失败，将使用空缓存", e)
        }
    }

    /**
     * 保存去重缓存到 SharedPreferences
     * 只保存未过期的条目，避免文件无限增长
     */
    private fun saveToPrefs() {
        val ctx = appContext ?: return
        val now = System.currentTimeMillis()
        try {
            val jsonArray = JSONArray()
            for ((key, entry) in dedupCache) {
                // 只保存跨来源窗口内的条目
                if (now - entry.firstSeen < CROSS_SOURCE_DEDUP_WINDOW_MS) {
                    val obj = JSONObject()
                    obj.put("key", key)
                    obj.put("amount", entry.amount)
                    obj.put("sourceType", entry.sourceType)
                    obj.put("appSignature", entry.appSignature)
                    obj.put("firstSeen", entry.firstSeen)
                    obj.put("recorded", entry.recorded)
                    jsonArray.put(obj)
                }
            }
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(PREFS_KEY, jsonArray.toString()).apply()
        } catch (e: Exception) {
            Log.w(TAG, "保存去重缓存失败", e)
        }
    }
}
