package com.jiyixia.app.util

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 规则管理器
 *
 * 从 assets/rules.json 加载识别规则。
 * 规则仅包含支付APP包名（精确+银行前缀），其余关键词已内置于各 Parser。
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
}
