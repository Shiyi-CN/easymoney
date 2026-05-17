package com.jiyixia.app.data

import com.jiyixia.app.data.entity.Category

object PresetCategories {
    val expense = listOf(
        Category(name = "餐饮", icon = "Restaurant", type = 0, isPreset = true, sortOrder = 1),
        Category(name = "交通", icon = "DirectionsCar", type = 0, isPreset = true, sortOrder = 2),
        Category(name = "购物", icon = "ShoppingBag", type = 0, isPreset = true, sortOrder = 3),
        Category(name = "娱乐", icon = "SportsEsports", type = 0, isPreset = true, sortOrder = 4),
        Category(name = "居住", icon = "Home", type = 0, isPreset = true, sortOrder = 5),
        Category(name = "医疗", icon = "LocalHospital", type = 0, isPreset = true, sortOrder = 6),
        Category(name = "教育", icon = "School", type = 0, isPreset = true, sortOrder = 7),
        Category(name = "其他", icon = "MoreHoriz", type = 0, isPreset = true, sortOrder = 8),
    )

    val income = listOf(
        Category(name = "工资", icon = "AccountBalance", type = 1, isPreset = true, sortOrder = 1),
        Category(name = "奖金", icon = "EmojiEvents", type = 1, isPreset = true, sortOrder = 2),
        Category(name = "理财", icon = "TrendingUp", type = 1, isPreset = true, sortOrder = 3),
        Category(name = "兼职", icon = "Work", type = 1, isPreset = true, sortOrder = 4),
        Category(name = "红包", icon = "CardGiftcard", type = 1, isPreset = true, sortOrder = 5),
    )

    val all: List<Category> = expense + income
}
