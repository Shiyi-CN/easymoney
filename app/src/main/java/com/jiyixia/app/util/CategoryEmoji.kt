package com.jiyixia.app.util

/**
 * 分类 Emoji 映射工具类
 *
 * 统一管理所有分类的 Emoji 映射，避免在多个文件中重复定义。
 * 使用轻量级 Emoji 替代图片资源，减少 APK 体积。
 */
object CategoryEmoji {

    /**
     * 分类名称 → Emoji 的完整映射
     */
    private val emojiMap = mapOf(
        // 支出分类（15个）
        "餐饮" to "🍜",
        "交通" to "🚇",
        "购物" to "🛒",
        "居住" to "🏠",
        "娱乐" to "🎮",
        "医疗" to "🏥",
        "教育" to "📚",
        "通讯" to "📱",
        "社交" to "🤝",
        "美容" to "💄",
        "宠物" to "🐱",
        "办公" to "💼",
        "维修" to "🔧",
        "捐赠" to "❤️",
        "其他" to "📋",

        // 收入分类（10个）
        "工资" to "💰",
        "奖金" to "🏆",
        "理财" to "📈",
        "兼职" to "💼",
        "红包" to "🧧",
        "报销" to "🧾",
        "租金" to "🏠",
        "退款" to "↩️",
        "中奖" to "🎰"
    )

    /**
     * 获取分类对应的 Emoji
     * @param name 分类名称
     * @return 对应的 Emoji，未找到时返回 "💸"
     */
    fun get(name: String?): String = emojiMap[name] ?: "💸"

    /**
     * 获取完整映射（只读）
     */
    fun getAll(): Map<String, String> = emojiMap
}
