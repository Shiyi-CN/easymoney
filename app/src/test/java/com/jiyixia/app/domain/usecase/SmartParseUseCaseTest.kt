package com.jiyixia.app.domain.usecase

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * SmartParseUseCase 回归测试
 *
 * 覆盖所有输入格式，确保后续重构（如 Double→Long 迁移）不会破坏解析逻辑。
 */
class SmartParseUseCaseTest {

    // 模拟分类名称 → ID 映射
    private lateinit var categoryNameToId: Map<String, Long>

    @Before
    fun setup() {
        categoryNameToId = mapOf(
            // 支出分类
            "餐饮" to 1L,
            "交通" to 2L,
            "购物" to 3L,
            "居住" to 4L,
            "娱乐" to 5L,
            "医疗" to 6L,
            "教育" to 7L,
            "通讯" to 8L,
            "社交" to 9L,
            "美容" to 10L,
            "宠物" to 11L,
            "办公" to 12L,
            "维修" to 13L,
            "捐赠" to 14L,
            "其他" to 15L,
            // 收入分类
            "工资" to 101L,
            "奖金" to 102L,
            "理财" to 103L,
            "兼职" to 104L,
            "红包" to 105L,
            "报销" to 106L,
            "租金" to 107L,
            "退款" to 108L,
            "中奖" to 109L
        )
    }

    // ========== 基本金额解析 ==========

    @Test
    fun `parse pure number`() {
        val result = SmartParseUseCase.parse("38", categoryNameToId)
        assertNotNull(result)
        assertEquals(38.0, result!!.amount, 0.01)
        assertEquals("38.00", result.amountText)
    }

    @Test
    fun `parse decimal number`() {
        val result = SmartParseUseCase.parse("38.5", categoryNameToId)
        assertNotNull(result)
        assertEquals(38.5, result!!.amount, 0.01)
    }

    @Test
    fun `parse number with yuan suffix`() {
        val result = SmartParseUseCase.parse("38元", categoryNameToId)
        assertNotNull(result)
        assertEquals(38.0, result!!.amount, 0.01)
    }

    @Test
    fun `parse number with kuai suffix`() {
        val result = SmartParseUseCase.parse("25块5", categoryNameToId)
        assertNotNull(result)
        assertEquals(25.5, result!!.amount, 0.01)
    }

    @Test
    fun `parse number with kuai and mao`() {
        val result = SmartParseUseCase.parse("25块5毛", categoryNameToId)
        assertNotNull(result)
        assertEquals(25.5, result!!.amount, 0.01)
    }

    // ========== 中文数字解析 ==========

    @Test
    fun `parse chinese number - simple`() {
        val result = SmartParseUseCase.parse("三十八", categoryNameToId)
        assertNotNull(result)
        assertEquals(38.0, result!!.amount, 0.01)
    }

    @Test
    fun `parse chinese number - tens`() {
        val result = SmartParseUseCase.parse("四十", categoryNameToId)
        assertNotNull(result)
        assertEquals(40.0, result!!.amount, 0.01)
    }

    @Test
    fun `parse chinese number - hundreds`() {
        // 注意：当前实现对"二百"解析为200，这是正确的
        val result = SmartParseUseCase.parse("二百", categoryNameToId)
        assertNotNull(result)
        assertEquals(200.0, result!!.amount, 0.01)
    }

    @Test
    fun `parse chinese number - simple hundreds`() {
        // 测试简单的百位数
        val result = SmartParseUseCase.parse("一百", categoryNameToId)
        assertNotNull(result)
        assertEquals(100.0, result!!.amount, 0.01)
    }

    @Test
    fun `parse chinese number - wan`() {
        val result = SmartParseUseCase.parse("一万", categoryNameToId)
        assertNotNull(result)
        assertEquals(10000.0, result!!.amount, 0.01)
    }

    // ========== 阿拉伯数字+万/千 ==========

    @Test
    fun `parse arabic number with wan`() {
        val result = SmartParseUseCase.parse("1万元", categoryNameToId)
        assertNotNull(result)
        assertEquals(10000.0, result!!.amount, 0.01)
    }

    @Test
    fun `parse arabic number with qian`() {
        val result = SmartParseUseCase.parse("2千元", categoryNameToId)
        assertNotNull(result)
        assertEquals(2000.0, result!!.amount, 0.01)
    }

    @Test
    fun `parse arabic number with wan no suffix`() {
        val result = SmartParseUseCase.parse("川南分公司1万元", categoryNameToId)
        assertNotNull(result)
        assertEquals(10000.0, result!!.amount, 0.01)
    }

    @Test
    fun `parse arabic decimal with wan`() {
        val result = SmartParseUseCase.parse("1.5万元", categoryNameToId)
        assertNotNull(result)
        assertEquals(15000.0, result!!.amount, 0.01)
    }

    // 注意：复杂中文数字（如"三千五百二十一"）解析有bug，留到P1修复

    // ========== 分类识别 ==========

    @Test
    fun `parse category - food keywords`() {
        val keywords = listOf("午餐", "早餐", "晚餐", "外卖", "火锅", "奶茶", "咖啡")
        for (keyword in keywords) {
            val result = SmartParseUseCase.parse("${keyword}38", categoryNameToId)
            assertNotNull("Failed for keyword: $keyword", result)
            assertEquals("Failed for keyword: $keyword", 1L, result!!.categoryId) // 餐饮
            assertTrue("Failed for keyword: $keyword", result.isExpense)
        }
    }

    @Test
    fun `parse category - transport keywords`() {
        val keywords = listOf("打车", "地铁", "公交", "加油", "停车")
        for (keyword in keywords) {
            val result = SmartParseUseCase.parse("${keyword}25", categoryNameToId)
            assertNotNull("Failed for keyword: $keyword", result)
            assertEquals("Failed for keyword: $keyword", 2L, result!!.categoryId) // 交通
        }
    }

    @Test
    fun `parse category - shopping keywords`() {
        val result = SmartParseUseCase.parse("超市100", categoryNameToId)
        assertNotNull(result)
        assertEquals(3L, result!!.categoryId) // 购物
    }

    // ========== 收入识别 ==========

    @Test
    fun `parse income - salary`() {
        val result = SmartParseUseCase.parse("工资8000", categoryNameToId)
        assertNotNull(result)
        assertEquals(8000.0, result!!.amount, 0.01)
        assertEquals(101L, result.categoryId) // 工资
        assertFalse(result.isExpense)
    }

    @Test
    fun `parse income - bonus`() {
        val result = SmartParseUseCase.parse("奖金5000", categoryNameToId)
        assertNotNull(result)
        assertEquals(102L, result!!.categoryId) // 奖金
        assertFalse(result.isExpense)
    }

    @Test
    fun `parse income - reimbursement received`() {
        val result = SmartParseUseCase.parse("报销到账50", categoryNameToId)
        assertNotNull(result)
        assertEquals(106L, result!!.categoryId) // 报销
        assertFalse(result.isExpense)
        assertFalse(result.isReimbursable) // 收入不标记为可报销
    }

    // ========== 报销识别 ==========

    @Test
    fun `parse reimbursement - with company`() {
        val result = SmartParseUseCase.parse("川南分公司打车50报销", categoryNameToId)
        assertNotNull(result)
        assertEquals(50.0, result!!.amount, 0.01)
        assertEquals(2L, result.categoryId) // 交通
        assertTrue(result.isExpense)
        assertTrue(result.isReimbursable)
        assertEquals("川南分公司", result.reimbursementTarget)
    }

    @Test
    fun `parse reimbursement - without company`() {
        val result = SmartParseUseCase.parse("打车50报销", categoryNameToId)
        assertNotNull(result)
        assertTrue(result!!.isReimbursable)
        assertTrue(result.reimbursementTarget.isBlank())
    }

    @Test
    fun `parse reimbursement - company format`() {
        val result = SmartParseUseCase.parse("腾讯公司午餐38报销", categoryNameToId)
        assertNotNull(result)
        assertTrue(result!!.isReimbursable)
        assertEquals("腾讯公司", result.reimbursementTarget)
    }

    // ========== 公司名自动识别为可报销 ==========

    @Test
    fun `parse company pattern - without reimbursement keyword`() {
        val result = SmartParseUseCase.parse("川南分公司购买打印纸500元", categoryNameToId)
        assertNotNull(result)
        assertTrue(result!!.isReimbursable)
        assertEquals("川南分公司", result.reimbursementTarget)
        assertEquals(500.0, result.amount, 0.01)
    }

    @Test
    fun `parse company pattern - with amount`() {
        val result = SmartParseUseCase.parse("坐飞机去广东，川南分公司1万元", categoryNameToId)
        assertNotNull(result)
        assertTrue(result!!.isReimbursable)
        assertEquals("川南分公司", result.reimbursementTarget)
    }

    @Test
    fun `parse company pattern - tencent`() {
        val result = SmartParseUseCase.parse("腾讯公司买咖啡38元", categoryNameToId)
        assertNotNull(result)
        assertTrue(result!!.isReimbursable)
        assertEquals("腾讯公司", result.reimbursementTarget)
    }

    // ========== 综合场景 ==========

    @Test
    fun `parse complex - lunch 38`() {
        val result = SmartParseUseCase.parse("午餐38", categoryNameToId)
        assertNotNull(result)
        assertEquals(38.0, result!!.amount, 0.01)
        assertEquals(1L, result.categoryId) // 餐饮
        assertTrue(result.isExpense)
        assertFalse(result.isReimbursable)
    }

    @Test
    fun `parse complex - taxi 25 kuai 5`() {
        val result = SmartParseUseCase.parse("打车25块5", categoryNameToId)
        assertNotNull(result)
        assertEquals(25.5, result!!.amount, 0.01)
        assertEquals(2L, result.categoryId) // 交通
    }

    @Test
    fun `parse complex - breakfast 10 yuan`() {
        val result = SmartParseUseCase.parse("早餐10元", categoryNameToId)
        assertNotNull(result)
        assertEquals(10.0, result!!.amount, 0.01)
        assertEquals(1L, result.categoryId) // 餐饮
    }

    // ========== 边界情况 ==========

    @Test
    fun `parse empty string returns null`() {
        val result = SmartParseUseCase.parse("", categoryNameToId)
        assertNull(result)
    }

    @Test
    fun `parse blank string returns null`() {
        val result = SmartParseUseCase.parse("   ", categoryNameToId)
        assertNull(result)
    }

    @Test
    fun `parse text without amount returns null`() {
        val result = SmartParseUseCase.parse("午餐", categoryNameToId)
        assertNull(result)
    }

    @Test
    fun `parse unknown category defaults to other`() {
        val result = SmartParseUseCase.parse("38", categoryNameToId)
        assertNotNull(result)
        assertEquals(15L, result!!.categoryId) // 其他
    }

    // ========== 备注生成 ==========

    @Test
    fun `note keeps original input`() {
        val result = SmartParseUseCase.parse("午餐38", categoryNameToId)
        assertNotNull(result)
        // 备注现在直接使用原始输入
        assertEquals("午餐38", result!!.note)
    }

    @Test
    fun `note keeps original input with reimbursement`() {
        val result = SmartParseUseCase.parse("川南分公司打车50报销", categoryNameToId)
        assertNotNull(result)
        // 备注现在直接使用原始输入
        assertEquals("川南分公司打车50报销", result!!.note)
    }

    @Test
    fun `note keeps original input with company`() {
        val result = SmartParseUseCase.parse("川南分公司购买打印纸500元", categoryNameToId)
        assertNotNull(result)
        // 备注现在直接使用原始输入
        assertEquals("川南分公司购买打印纸500元", result!!.note)
    }

    @Test
    fun `note generation keeps extra text`() {
        val result = SmartParseUseCase.parse("午餐38和同事聚餐", categoryNameToId)
        assertNotNull(result)
        // 备注现在直接使用原始输入
        assertEquals("午餐38和同事聚餐", result!!.note)
    }

    // ========== cleanNote 方法 ==========

    @Test
    fun `cleanNote removes amount`() {
        val cleaned = SmartParseUseCase.cleanNote("38元")
        assertFalse(cleaned.contains("38"))
    }

    @Test
    fun `cleanNote removes category keywords`() {
        val cleaned = SmartParseUseCase.cleanNote("午餐")
        assertFalse(cleaned.contains("午餐"))
    }

    @Test
    fun `cleanNote removes reimbursement`() {
        val cleaned = SmartParseUseCase.cleanNote("报销")
        assertFalse(cleaned.contains("报销"))
    }
}
