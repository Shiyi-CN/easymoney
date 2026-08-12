package com.jiyixia.app.util

/**
 * 中文数字 → 阿拉伯数字 转换工具
 *
 * 用于语音识别结果后处理：将讯飞识别出的中文数字金额自动转为阿拉伯数字。
 * 例："午餐二十九块五毛一分" → "午餐29.51"
 *     "打车二十五块" → "打车25"
 *     "三十八" → "38"
 */
object ChineseNumberConverter {

    private val digitMap = mapOf(
        '零' to 0, '一' to 1, '二' to 2, '三' to 3, '四' to 4,
        '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
        '两' to 2,
        '伍' to 5, '陆' to 6, '柒' to 7, '捌' to 8, '玖' to 9,
        '壹' to 1, '贰' to 2, '叁' to 3, '肆' to 4
    )

    private val unitMap = mapOf(
        '十' to 10L, '百' to 100L, '千' to 1000L, '万' to 10000L,
        '拾' to 10L, '佰' to 100L, '仟' to 1000L
    )

    private val allDigitChars = digitMap.keys.joinToString("")
    private val allUnitChars = unitMap.keys.joinToString("")
    private val allCnChars = allDigitChars + allUnitChars

    /**
     * 将文本中的中文数字金额转换为阿拉伯数字
     * 保留非数字部分不变
     */
    fun convert(text: String): String {
        var result = text

        // 1. 完整角分模式："二十九块五毛一分" / "二十九元五角一分" → 29.51
        val fullJiaoFen = Regex(
            """([${allCnChars}]+)\s*[块元]\s*([${allDigitChars}])\s*[毛角]\s*([${allDigitChars}])\s*分?"""
        )
        result = fullJiaoFen.replace(result) { match ->
            val yuan = chineseToArabic(match.groupValues[1]) ?: return@replace match.value
            val jiao = digitToInt(match.groupValues[2])
            val fen = digitToInt(match.groupValues[3])
            val value = yuan.toDouble() + jiao / 10.0 + fen / 100.0
            "%.2f".format(value)
        }

        // 2. 只有角："二十九块五毛" / "二十九元五角" → 29.5
        val jiaoOnly = Regex(
            """([${allCnChars}]+)\s*[块元]\s*([${allDigitChars}])\s*[毛角]"""
        )
        result = jiaoOnly.replace(result) { match ->
            val yuan = chineseToArabic(match.groupValues[1]) ?: return@replace match.value
            val jiao = digitToInt(match.groupValues[2])
            val value = yuan.toDouble() + jiao / 10.0
            "%.2f".format(value).trimEnd('0').trimEnd('.')
        }

        // 3. "点"模式："二十九点五一" → 29.51
        val dotPattern = Regex(
            """([${allCnChars}]+)\s*点\s*([${allDigitChars}]+)"""
        )
        result = dotPattern.replace(result) { match ->
            val intPart = chineseToArabic(match.groupValues[1]) ?: return@replace match.value
            val fracStr = match.groupValues[2]
            val fracPart = fracStr.map { digitMap[it]?.toString() ?: "0" }.joinToString("")
            "$intPart.$fracPart"
        }

        // 4. 纯中文数字（不含上述模式）："三十八" → 38
        val pureCn = Regex("""[${allCnChars}]{2,}""")
        result = pureCn.replace(result) { match ->
            val value = chineseToArabic(match.value) ?: return@replace match.value
            value.toString()
        }

        return result
    }

    private fun digitToInt(ch: String): Int {
        return if (ch.length == 1) digitMap[ch[0]] ?: 0 else 0
    }

    private fun chineseToArabic(cn: String): Long? {
        if (cn.isEmpty()) return null

        var result = 0L
        var current = 0L

        for (ch in cn) {
            when {
                ch in digitMap -> {
                    current = digitMap[ch]!!.toLong()
                }
                ch in unitMap -> {
                    val unit = unitMap[ch]!!
                    if (ch == '万') {
                        result = (result + current) * unit
                        current = 0
                    } else {
                        if (current == 0L) current = 1L
                        result += current * unit
                        current = 0
                    }
                }
            }
        }

        result += current
        return if (result > 0) result else null
    }
}
