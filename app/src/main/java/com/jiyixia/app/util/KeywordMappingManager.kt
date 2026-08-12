package com.jiyixia.app.util

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * 自定义关键词映射管理器
 *
 * 参考一木记账的核心差异化功能：用户可将个人习惯用语绑定至分类，
 * 建立高容错率的录入环境。
 *
 * 例：
 * - "过早"     → "餐饮"（武汉方言，吃早餐）
 * - "打车费"   → "交通"
 * - "猫粮"     → "宠物"
 * - "搓一顿"   → "餐饮"
 *
 * 数据持久化：SharedPreferences + JSON 序列化
 * 优先级：自定义关键词 > 内置关键词（在 SmartParseUseCase 中体现）
 */
object KeywordMappingManager {

    private const val TAG = "KeywordMappingManager"
    private const val PREFS_NAME = "keyword_mappings_prefs"
    private const val KEY_MAPPINGS = "mappings_json"

    private lateinit var prefs: android.content.SharedPreferences
    private var mappings: MutableMap<String, String> = mutableMapOf()
    private var isInitialized = false

    /**
     * 初始化（在 Application.onCreate 中调用）
     */
    fun init(context: Context) {
        if (isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadFromPrefs()
        isInitialized = true
        Log.d(TAG, "关键词映射初始化完成，共 ${mappings.size} 条自定义关键词")
    }

    /**
     * 获取所有自定义关键词映射（给 SmartParseUseCase 用）
     * @return 关键词 → 分类名 的映射
     */
    fun getAll(): Map<String, String> {
        if (!isInitialized) return emptyMap()
        return mappings.toMap()
    }

    /**
     * 添加或更新一条关键词映射
     * @param keyword 关键词（如 "过早"）
     * @param categoryName 分类名（如 "餐饮"）
     * @return true 表示添加/更新成功
     */
    fun add(keyword: String, categoryName: String): Boolean {
        if (!isInitialized) return false
        val kw = keyword.trim()
        val cat = categoryName.trim()
        if (kw.isEmpty() || cat.isEmpty()) return false
        mappings[kw] = cat
        saveToPrefs()
        Log.d(TAG, "添加关键词映射: \"$kw\" → \"$cat\"")
        return true
    }

    /**
     * 删除一条关键词映射
     * @param keyword 要删除的关键词
     * @return true 表示删除成功
     */
    fun remove(keyword: String): Boolean {
        if (!isInitialized) return false
        val removed = mappings.remove(keyword.trim()) != null
        if (removed) saveToPrefs()
        return removed
    }

    /**
     * 清空所有自定义关键词
     */
    fun clear() {
        if (!isInitialized) return
        mappings.clear()
        saveToPrefs()
    }

    /**
     * 获取所有关键词（用于 UI 展示）
     * @return 按关键词排序的列表
     */
    fun getAllSorted(): List<Pair<String, String>> {
        if (!isInitialized) return emptyList()
        return mappings.entries
            .sortedBy { it.key }
            .map { it.key to it.value }
    }

    // ═══════════════════════════════════════════════════════════
    //  持久化
    // ═══════════════════════════════════════════════════════════

    private fun loadFromPrefs() {
        try {
            val json = prefs.getString(KEY_MAPPINGS, null) ?: return
            if (json.isBlank()) return
            val jsonArray = JSONArray(json)
            mappings.clear()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val keyword = obj.getString("keyword")
                val category = obj.getString("category")
                if (keyword.isNotBlank() && category.isNotBlank()) {
                    mappings[keyword] = category
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载关键词映射失败", e)
        }
    }

    private fun saveToPrefs() {
        try {
            val jsonArray = JSONArray()
            for ((keyword, category) in mappings) {
                val obj = JSONObject()
                obj.put("keyword", keyword)
                obj.put("category", category)
                jsonArray.put(obj)
            }
            prefs.edit().putString(KEY_MAPPINGS, jsonArray.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "保存关键词映射失败", e)
        }
    }
}
