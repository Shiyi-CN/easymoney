package com.jiyixia.app.util

import android.content.Context
import android.content.SharedPreferences

/**
 * 用户学习管理器
 *
 * 记录用户手动修正的分类映射，下次自动匹配。
 * 存储格式：商户名称 → 正确分类
 *
 * 使用方式：
 * 1. 用户手动修正分类时，调用 learn(merchantName, correctCategory)
 * 2. 识别时调用 getLearnedCategory(merchantName) 获取学习到的分类
 */
object UserLearningManager {

    private const val PREFS_NAME = "user_learning"
    private const val KEY_PREFIX = "merchant_"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 学习商户的正确分类
     *
     * @param merchantName 商户名称（如"星巴克"、"滴滴出行"）
     * @param correctCategory 正确的分类名称（如"餐饮"、"交通"）
     */
    fun learn(context: Context, merchantName: String, correctCategory: String) {
        if (merchantName.isBlank() || correctCategory.isBlank()) return
        val key = KEY_PREFIX + merchantName.trim().lowercase()
        getPrefs(context).edit().putString(key, correctCategory).apply()
    }

    /**
     * 获取学习到的分类
     *
     * @param merchantName 商户名称
     * @return 学习到的分类名称，如果没有学习过则返回 null
     */
    fun getLearnedCategory(context: Context, merchantName: String): String? {
        if (merchantName.isBlank()) return null
        val key = KEY_PREFIX + merchantName.trim().lowercase()
        return getPrefs(context).getString(key, null)
    }

    /**
     * 检查是否学习过该商户
     */
    fun hasLearned(context: Context, merchantName: String): Boolean {
        if (merchantName.isBlank()) return false
        val key = KEY_PREFIX + merchantName.trim().lowercase()
        return getPrefs(context).contains(key)
    }

    /**
     * 获取所有学习数据（用于调试）
     */
    fun getAllLearned(context: Context): Map<String, String> {
        val prefs = getPrefs(context)
        val result = mutableMapOf<String, String>()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith(KEY_PREFIX) && value is String) {
                val merchantName = key.removePrefix(KEY_PREFIX)
                result[merchantName] = value
            }
        }
        return result
    }

    /**
     * 清除所有学习数据
     */
    fun clearAll(context: Context) {
        getPrefs(context).edit().clear().apply()
    }

    // ═══════════════════════════════════════════════════════════
    //  报销对象历史记录
    // ═══════════════════════════════════════════════════════════

    private const val REIMBURSE_TARGETS_KEY = "reimburse_targets"

    /**
     * 保存报销对象到历史记录
     */
    fun saveReimburseTarget(context: Context, target: String) {
        if (target.isBlank()) return
        val targets = getReimburseTargets(context).toMutableList()
        // 去重：如果已存在则移到最前面
        targets.remove(target)
        targets.add(0, target)
        // 最多保留 20 个
        if (targets.size > 20) targets.removeLast()
        getPrefs(context).edit().putString(REIMBURSE_TARGETS_KEY, targets.joinToString("||")).apply()
    }

    /**
     * 获取报销对象历史记录
     */
    fun getReimburseTargets(context: Context): List<String> {
        val raw = getPrefs(context).getString(REIMBURSE_TARGETS_KEY, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split("||").filter { it.isNotBlank() }
    }
}
