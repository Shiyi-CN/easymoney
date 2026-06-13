package com.jiyixia.app.util

/**
 * 金额扩展函数
 *
 * 内部统一使用 Long（分），显示层使用 String（元）
 * 解决 Double 浮点精度问题
 */

/**
 * Long（分）→ 显示字符串
 * 示例：3810L → "¥38.10"
 */
fun Long.toAmountString(): String = "¥%.2f".format(this / 100.0)

/**
 * Long（分）→ 纯数字字符串（不含 ¥ 符号）
 * 示例：3810L → "38.10"
 */
fun Long.toAmountNumber(): String = "%.2f".format(this / 100.0)

/**
 * 字符串（元）→ Long（分）
 * 示例："38.10" → 3810L
 */
fun String.toAmountCents(): Long {
    val cleaned = this.replace("[^0-9.]".toRegex(), "")
    if (cleaned.isEmpty()) return 0L
    return try {
        Math.round(cleaned.toDouble() * 100)
    } catch (e: NumberFormatException) {
        0L
    }
}

/**
 * Double（元）→ Long（分）- 仅用于迁移
 * 示例：38.10 → 3810L
 */
fun Double.toCents(): Long = Math.round(this * 100)

/**
 * Long（分）→ Double（元）- 仅用于图表等场景
 * 示例：3810L → 38.10
 */
fun Long.toYuanDouble(): Double = this / 100.0
