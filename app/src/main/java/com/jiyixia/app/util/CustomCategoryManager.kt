package com.jiyixia.app.util

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 自定义分类管理器
 *
 * 管理用户自定义的分类，支持添加、编辑、删除。
 * 存储格式：JSON 数组，每个元素包含 name、icon、type
 */
object CustomCategoryManager {

    private const val PREFS_NAME = "custom_categories"
    private const val KEY_CATEGORIES = "categories"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 获取所有自定义分类
     */
    fun getAll(context: Context): List<CustomCategory> {
        val json = getPrefs(context).getString(KEY_CATEGORIES, "[]") ?: "[]"
        val array = JSONArray(json)
        val result = mutableListOf<CustomCategory>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            result.add(
                CustomCategory(
                    name = obj.getString("name"),
                    icon = obj.getString("icon"),
                    type = obj.getInt("type")
                )
            )
        }
        return result
    }

    /**
     * 添加自定义分类
     */
    fun add(context: Context, category: CustomCategory): Boolean {
        val categories = getAll(context).toMutableList()

        // 检查是否已存在
        if (categories.any { it.name == category.name && it.type == category.type }) {
            return false
        }

        categories.add(category)
        save(context, categories)
        return true
    }

    /**
     * 删除自定义分类
     */
    fun delete(context: Context, name: String, type: Int): Boolean {
        val categories = getAll(context).toMutableList()
        val removed = categories.removeAll { it.name == name && it.type == type }
        if (removed) {
            save(context, categories)
        }
        return removed
    }

    /**
     * 更新自定义分类
     */
    fun update(context: Context, oldName: String, oldType: Int, newCategory: CustomCategory): Boolean {
        val categories = getAll(context).toMutableList()
        val index = categories.indexOfFirst { it.name == oldName && it.type == oldType }
        if (index >= 0) {
            categories[index] = newCategory
            save(context, categories)
            return true
        }
        return false
    }

    /**
     * 检查是否是自定义分类
     */
    fun isCustomCategory(context: Context, name: String, type: Int): Boolean {
        return getAll(context).any { it.name == name && it.type == type }
    }

    /**
     * 获取指定类型的自定义分类
     */
    fun getByType(context: Context, type: Int): List<CustomCategory> {
        return getAll(context).filter { it.type == type }
    }

    /**
     * 保存自定义分类
     */
    private fun save(context: Context, categories: List<CustomCategory>) {
        val array = JSONArray()
        categories.forEach { category ->
            val obj = JSONObject().apply {
                put("name", category.name)
                put("icon", category.icon)
                put("type", category.type)
            }
            array.put(obj)
        }
        getPrefs(context).edit().putString(KEY_CATEGORIES, array.toString()).apply()
    }

    /**
     * 清除所有自定义分类
     */
    fun clearAll(context: Context) {
        getPrefs(context).edit().remove(KEY_CATEGORIES).apply()
    }
}

/**
 * 自定义分类数据类
 */
data class CustomCategory(
    val name: String,
    val icon: String,
    val type: Int  // 0=支出, 1=收入
)
