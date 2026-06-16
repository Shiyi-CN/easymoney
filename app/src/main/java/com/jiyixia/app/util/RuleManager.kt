package com.jiyixia.app.util

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 规则管理器
 *
 * 从 assets/rules.json 加载识别规则，支持动态更新。
 * 规则包括：
 * - 支付APP包名
 * - 聊天APP包名
 * - 支付确认词
 * - 外卖/出行线索词
 * - 金额提取正则
 */
object RuleManager {

    private const val TAG = "RuleManager"
    private const val RULES_FILE = "rules.json"

    private var rules: JSONObject? = null
    private var isLoaded = false

    /**
     * 加载规则文件
     */
    fun load(context: Context) {
        try {
            val inputStream = context.assets.open(RULES_FILE)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonString = reader.readText()
            reader.close()

            rules = JSONObject(jsonString)
            isLoaded = true
            Log.d(TAG, "规则加载成功，版本: ${rules?.optInt("version", 0)}")
        } catch (e: Exception) {
            Log.e(TAG, "规则加载失败", e)
            isLoaded = false
        }
    }

    /**
     * 获取支付APP包名列表（精确匹配）
     */
    fun getPaymentAppPackages(): Set<String> {
        if (!isLoaded) return emptySet()
        val array = rules?.optJSONObject("paymentApps")?.optJSONArray("exact") ?: return emptySet()
        val result = mutableSetOf<String>()
        for (i in 0 until array.length()) {
            result.add(array.getString(i))
        }
        return result
    }

    /**
     * 获取银行APP包名前缀列表
     */
    fun getBankAppPrefixes(): List<String> {
        if (!isLoaded) return emptyList()
        val array = rules?.optJSONObject("paymentApps")?.optJSONArray("prefixes") ?: return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until array.length()) {
            result.add(array.getString(i))
        }
        return result
    }

    /**
     * 获取聊天APP包名列表
     */
    fun getChatAppPackages(): Set<String> {
        if (!isLoaded) return emptySet()
        val array = rules?.optJSONArray("chatApps") ?: return emptySet()
        val result = mutableSetOf<String>()
        for (i in 0 until array.length()) {
            result.add(array.getString(i))
        }
        return result
    }

    /**
     * 获取聊天APP支付确认词列表
     */
    fun getChatAppPaymentConfirm(): List<String> {
        if (!isLoaded) return emptyList()
        val array = rules?.optJSONArray("chatAppPaymentConfirm") ?: return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until array.length()) {
            result.add(array.getString(i))
        }
        return result
    }

    /**
     * 获取外卖专属APP包名列表
     */
    fun getFoodDeliveryApps(): Set<String> {
        if (!isLoaded) return emptySet()
        val array = rules?.optJSONArray("foodDeliveryApps") ?: return emptySet()
        val result = mutableSetOf<String>()
        for (i in 0 until array.length()) {
            result.add(array.getString(i))
        }
        return result
    }

    /**
     * 获取综合平台APP包名映射
     */
    fun getMultiCategoryApps(): Map<String, String> {
        if (!isLoaded) return emptyMap()
        val obj = rules?.optJSONObject("multiCategoryApps") ?: return emptyMap()
        val result = mutableMapOf<String, String>()
        obj.keys().forEach { key ->
            result[key] = obj.getString(key)
        }
        return result
    }

    /**
     * 获取外卖线索词列表
     */
    fun getFoodDeliveryHints(): List<String> {
        if (!isLoaded) return emptyList()
        val array = rules?.optJSONArray("foodDeliveryHints") ?: return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until array.length()) {
            result.add(array.getString(i))
        }
        return result
    }

    /**
     * 获取出行/打车线索词列表
     */
    fun getRideHailingHints(): List<String> {
        if (!isLoaded) return emptyList()
        val array = rules?.optJSONArray("rideHailingHints") ?: return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until array.length()) {
            result.add(array.getString(i))
        }
        return result
    }

    /**
     * 获取支付关键词列表
     */
    fun getPaymentKeywords(): List<String> {
        if (!isLoaded) return emptyList()
        val array = rules?.optJSONArray("paymentKeywords") ?: return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until array.length()) {
            result.add(array.getString(i))
        }
        return result
    }

    /**
     * 获取支付成功关键词列表
     */
    fun getPaymentSuccessKeywords(): List<String> {
        if (!isLoaded) return emptyList()
        val array = rules?.optJSONArray("paymentSuccessKeywords") ?: return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until array.length()) {
            result.add(array.getString(i))
        }
        return result
    }

    /**
     * 获取转账关键词列表
     */
    fun getTransferKeywords(): List<String> {
        if (!isLoaded) return emptyList()
        val array = rules?.optJSONArray("transferKeywords") ?: return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until array.length()) {
            result.add(array.getString(i))
        }
        return result
    }

    /**
     * 获取金额提取正则表达式列表
     */
    fun getAmountPatterns(): List<String> {
        if (!isLoaded) return emptyList()
        val array = rules?.optJSONArray("amountPatterns") ?: return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until array.length()) {
            result.add(array.getString(i))
        }
        return result
    }

    /**
     * 获取规则版本
     */
    fun getVersion(): Int {
        return rules?.optInt("version", 0) ?: 0
    }

    /**
     * 检查规则是否已加载
     */
    fun isRulesLoaded(): Boolean {
        return isLoaded
    }
}
