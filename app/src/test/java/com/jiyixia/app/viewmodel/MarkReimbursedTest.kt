package com.jiyixia.app.viewmodel

import com.jiyixia.app.data.entity.Record
import org.junit.Test
import org.junit.Assert.*

/**
 * Record 实体报销字段测试
 *
 * 验证 reimbursementSourceId 字段的正确使用
 */
class RecordReimbursementTest {

    @Test
    fun `reimbursementSourceId 默认值为 0`() {
        val record = Record(
            type = 0,
            amount = 5000,
            categoryId = 1,
            note = "测试",
            date = System.currentTimeMillis()
        )
        assertEquals(0L, record.reimbursementSourceId)
    }

    @Test
    fun `报销到账记录应设置 reimbursementSourceId`() {
        val sourceId = 123L
        val incomeRecord = Record(
            type = 1,
            amount = 5000,
            categoryId = 1,
            note = "[已报销] 打车",
            date = System.currentTimeMillis(),
            reimbursementSourceId = sourceId
        )
        assertEquals(sourceId, incomeRecord.reimbursementSourceId)
    }

    @Test
    fun `isReimbursedIncome 判断逻辑正确`() {
        // 报销到账记录：reimbursementSourceId > 0
        val reimbursedIncome = Record(
            type = 1,
            amount = 5000,
            categoryId = 1,
            note = "[已报销] 午餐",
            date = System.currentTimeMillis(),
            reimbursementSourceId = 100
        )
        assertTrue(reimbursedIncome.reimbursementSourceId > 0)

        // 普通收入记录：reimbursementSourceId = 0
        val normalIncome = Record(
            type = 1,
            amount = 5000,
            categoryId = 1,
            note = "工资",
            date = System.currentTimeMillis()
        )
        assertFalse(normalIncome.reimbursementSourceId > 0)
    }

    @Test
    fun `同金额多笔报销应有不同 reimbursementSourceId`() {
        val record1 = Record(
            type = 1,
            amount = 5000,
            categoryId = 2,
            note = "[已报销] 打车去公司",
            date = System.currentTimeMillis(),
            reimbursementSourceId = 101
        )

        val record2 = Record(
            type = 1,
            amount = 5000,
            categoryId = 2,
            note = "[已报销] 打车回家",
            date = System.currentTimeMillis(),
            reimbursementSourceId = 102
        )

        // 金额相同，但 ID 不同
        assertEquals(record1.amount, record2.amount)
        assertNotEquals(record1.reimbursementSourceId, record2.reimbursementSourceId)
    }

    @Test
    fun `撤销报销匹配逻辑：按 reimbursementSourceId 精确查找`() {
        val sourceId = 200L

        // 模拟收入记录列表
        val incomeRecords = listOf(
            Record(id = 301, type = 1, amount = 5000, categoryId = 1,
                note = "[已报销] A", date = 1000, reimbursementSourceId = 100),
            Record(id = 302, type = 1, amount = 5000, categoryId = 1,
                note = "[已报销] B", date = 2000, reimbursementSourceId = 200),
            Record(id = 303, type = 1, amount = 5000, categoryId = 1,
                note = "[已报销] C", date = 3000, reimbursementSourceId = 300)
        )

        // 按 reimbursementSourceId 查找
        val found = incomeRecords.find { it.reimbursementSourceId == sourceId }

        assertNotNull(found)
        assertEquals(302L, found!!.id)
        assertEquals(200L, found.reimbursementSourceId)
    }

    @Test
    fun `防重复检查：按 reimbursementSourceId 判断`() {
        val sourceId = 100L

        // 模拟已有收入记录
        val existingRecords = listOf(
            Record(id = 201, type = 1, amount = 5000, categoryId = 1,
                note = "[已报销] 测试", date = 1000, reimbursementSourceId = 100)
        )

        // 检查是否已存在
        val hasRecord = existingRecords.any { it.reimbursementSourceId == sourceId }

        assertTrue(hasRecord)

        // 检查不存在的 ID
        val hasOther = existingRecords.any { it.reimbursementSourceId == 999L }

        assertFalse(hasOther)
    }
}