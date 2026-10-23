package com.jiyixia.app.util

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 学习队列（支柱4：无信号原文存档）
 *
 * 被营销过滤/未匹配模板的支付类通知原文存档到这里，不再静默丢弃：
 * - 用户在设置页「漏单暂存」可见被拦截的通知原文，若确认是漏记的支付可手动补记
 * - 用户手动补记/修正分类时，EditRecordDialog 会调用 UserLearningManager.learn()
 *   记住 商户→分类 映射，识别模板库自动进化
 *
 * 存储：SharedPreferences + JSON 数组，保留最近 20 条，48 小时过期。
 */
object LearningQueue {

    data class Entry(
        val time: Long,            // 存档时间
        val packageName: String,   // 来源包名
        val title: String,         // 通知标题
        val text: String,          // 通知原文（截断至 80 字符）
        val reason: String         // 丢弃原因（如"含营销词被过滤"）
    )

    private const val PREFS = "learning_queue"
    private const val KEY = "entries"
    private const val MAX_ENTRIES = 20
    private const val EXPIRE_MS = 48 * 60 * 60 * 1000L

    /**
     * 存档一条被过滤的支付通知
     *
     * 同一包名+标题+原文 5 分钟内去重（系统可能重投递同一通知）。
     */
    @Synchronized
    fun archive(context: Context, packageName: String, title: String, text: String, reason: String) {
        val entries = getAll(context).toMutableList()
        val now = System.currentTimeMillis()
        val isRecentDuplicate = entries.any {
            it.packageName == packageName && it.title == title && it.text == text &&
                    now - it.time < 5 * 60 * 1000L
        }
        if (isRecentDuplicate) return

        entries.add(0, Entry(now, packageName, title.trim(), text.trim().take(80), reason))
        while (entries.size > MAX_ENTRIES) entries.removeLast()
        save(context, entries)
    }

    /** 获取全部存档（已过滤 48 小时前的过期条目，最新的在前） */
    fun getAll(context: Context): List<Entry> {
        val raw = getPrefs(context).getString(KEY, null) ?: return emptyList()
        val now = System.currentTimeMillis()
        return try {
            val array = JSONArray(raw)
            (0 until array.length())
                .map { array.getJSONObject(it) }
                .filter { now - it.optLong("time", 0) < EXPIRE_MS }
                .map {
                    Entry(
                        time = it.optLong("time", 0),
                        packageName = it.optString("pkg"),
                        title = it.optString("title"),
                        text = it.optString("text"),
                        reason = it.optString("reason")
                    )
                }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 清空全部存档 */
    fun clear(context: Context) {
        getPrefs(context).edit().remove(KEY).apply()
    }

    @Synchronized
    private fun save(context: Context, entries: List<Entry>) {
        val array = JSONArray()
        entries.forEach {
            array.put(
                JSONObject()
                    .put("time", it.time)
                    .put("pkg", it.packageName)
                    .put("title", it.title)
                    .put("text", it.text)
                    .put("reason", it.reason)
            )
        }
        getPrefs(context).edit().putString(KEY, array.toString()).apply()
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }
}
