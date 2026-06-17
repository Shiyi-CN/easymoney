package com.jiyixia.app.util

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/**
 * 细分标签管理器
 *
 * 管理每个大类下的细分标签。
 * 存储格式：JSON 对象，key 为分类名称，value 为标签数组
 *
 * 例如：
 * {
 *   "餐饮": ["星巴克", "肯德基", "海底捞"],
 *   "交通": ["滴滴", "地铁", "加油"]
 * }
 */
object SubCategoryManager {

    private const val PREFS_NAME = "sub_categories"
    private const val KEY_SUB_CATEGORIES = "sub_categories_map"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 获取指定分类的细分标签
     */
    fun getSubCategories(context: Context, categoryName: String): List<String> {
        val json = getPrefs(context).getString(KEY_SUB_CATEGORIES, "{}") ?: "{}"
        val obj = org.json.JSONObject(json)
        val array = obj.optJSONArray(categoryName) ?: return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until array.length()) {
            result.add(array.getString(i))
        }
        return result
    }

    /**
     * 添加细分标签
     */
    fun addSubCategory(context: Context, categoryName: String, subCategory: String): Boolean {
        if (categoryName.isBlank() || subCategory.isBlank()) return false

        val json = getPrefs(context).getString(KEY_SUB_CATEGORIES, "{}") ?: "{}"
        val obj = org.json.JSONObject(json)
        val array = obj.optJSONArray(categoryName) ?: JSONArray()

        // 检查是否已存在
        for (i in 0 until array.length()) {
            if (array.getString(i).equals(subCategory, ignoreCase = true)) {
                return false
            }
        }

        array.put(subCategory)
        obj.put(categoryName, array)
        getPrefs(context).edit().putString(KEY_SUB_CATEGORIES, obj.toString()).apply()
        return true
    }

    /**
     * 删除细分标签
     */
    fun removeSubCategory(context: Context, categoryName: String, subCategory: String): Boolean {
        val json = getPrefs(context).getString(KEY_SUB_CATEGORIES, "{}") ?: "{}"
        val obj = org.json.JSONObject(json)
        val array = obj.optJSONArray(categoryName) ?: return false

        val newArray = JSONArray()
        var found = false
        for (i in 0 until array.length()) {
            if (!array.getString(i).equals(subCategory, ignoreCase = true)) {
                newArray.put(array.getString(i))
            } else {
                found = true
            }
        }

        if (found) {
            obj.put(categoryName, newArray)
            getPrefs(context).edit().putString(KEY_SUB_CATEGORIES, obj.toString()).apply()
        }
        return found
    }

    /**
     * 获取所有细分标签
     */
    fun getAllSubCategories(context: Context): Map<String, List<String>> {
        val json = getPrefs(context).getString(KEY_SUB_CATEGORIES, "{}") ?: "{}"
        val obj = org.json.JSONObject(json)
        val result = mutableMapOf<String, List<String>>()

        obj.keys().forEach { key ->
            val array = obj.optJSONArray(key) ?: return@forEach
            val list = mutableListOf<String>()
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
            result[key] = list
        }
        return result
    }

    /**
     * 智能建议：从备注中提取可能的细分标签
     */
    fun suggestSubCategory(note: String, categoryName: String): String? {
        if (note.isBlank()) return null

        // 移除数字和常见单位
        val cleaned = note
            .replace(Regex("""\d+\.?\d*\s*[元块钱￥¥]?"""), "")
            .replace(Regex("""[零一二两三四五六七八九十百千万]+"""), "")
            .trim()

        if (cleaned.length < 2 || cleaned.length > 10) return null

        // 检查是否包含有意义的关键词
        val words = cleaned.split(Regex("""\s+""")).filter { it.length >= 2 }
        return words.firstOrNull()
    }

    /**
     * 清除所有细分标签
     */
    fun clearAll(context: Context) {
        getPrefs(context).edit().remove(KEY_SUB_CATEGORIES).apply()
    }
}
