package com.jiyixia.app.service

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * DedupManager 单元测试
 *
 * 验证去重逻辑：
 * 1. 同来源同金额 5 分钟内重复 → 应去重
 * 2. 不同来源同金额 → 跨来源去重
 * 3. 不同金额 → 不去重
 * 4. 超过 5 分钟 → 不去重
 *
 * 注：android.util.Log 通过 build.gradle.kts 的
 * testOptions.unitTests.isReturnDefaultValues = true 处理
 */
class DedupManagerTest {

    @Before
    fun setup() {
        // 每个测试前清空缓存
        DedupManager.clear()
    }

    @After
    fun tearDown() {
        DedupManager.clear()
    }

    @Test
    fun `首次检测应不重复`() {
        val isDup = DedupManager.isDuplicate(
            amount = 38.50,
            sourceType = "notification",
            appSignature = "com.tencent.mm"
        )
        assertFalse("首次检测不应判重", isDup)
    }

    @Test
    fun `同来源同金额5分钟内应判重`() {
        // 第一次
        val first = DedupManager.isDuplicate(38.50, "notification", "com.tencent.mm")
        assertFalse(first)

        // 第二次（同来源同金额）
        val second = DedupManager.isDuplicate(38.50, "notification", "com.tencent.mm")
        assertTrue("同来源同金额5分钟内应判重", second)
    }

    @Test
    fun `不同金额不应判重`() {
        DedupManager.isDuplicate(38.50, "notification", "com.tencent.mm")
        val isDup = DedupManager.isDuplicate(50.00, "notification", "com.tencent.mm")
        assertFalse("不同金额不应判重", isDup)
    }

    @Test
    fun `同金额不同app签名不应判重`() {
        // 微信支付 38.50
        DedupManager.isDuplicate(38.50, "notification", "com.tencent.mm")

        // 支付宝也支付 38.50（不同 app，不应去重）
        val isDup = DedupManager.isDuplicate(38.50, "notification", "com.eg.android.AlipayGphone")
        assertFalse("不同app签名不应判重", isDup)
    }

    @Test
    fun `跨来源去重 - 通知已记录则屏幕应跳过`() {
        // 通知先记录了 38.50 并成功入库
        DedupManager.isDuplicate(38.50, "notification", "com.tencent.mm")
        DedupManager.markRecorded(38.50, "notification", "com.tencent.mm")

        // 屏幕也检测到 38.50（同 app，不同来源）
        val isDup = DedupManager.isDuplicateAcrossSources(
            amount = 38.50,
            excludeSourceType = "screen"
        )
        assertTrue("跨来源去重：通知已入库，屏幕应跳过", isDup)
    }

    @Test
    fun `跨来源去重 - 排除自身来源`() {
        // 通知记录了 38.50 并入库
        DedupManager.isDuplicate(38.50, "notification", "com.tencent.mm")
        DedupManager.markRecorded(38.50, "notification", "com.tencent.mm")

        // 检查通知来源本身（应不重复，因为排除了 notification）
        val isDup = DedupManager.isDuplicateAcrossSources(
            amount = 38.50,
            excludeSourceType = "notification"
        )
        assertFalse("排除自身来源后不应判重", isDup)
    }

    @Test
    fun `跨来源去重 - 不同金额不判重`() {
        DedupManager.isDuplicate(38.50, "notification", "com.tencent.mm")
        DedupManager.markRecorded(38.50, "notification", "com.tencent.mm")

        val isDup = DedupManager.isDuplicateAcrossSources(
            amount = 50.00,
            excludeSourceType = "screen"
        )
        assertFalse("不同金额不应跨来源判重", isDup)
    }

    @Test
    fun `浮点精度 - 38元5角应稳定去重`() {
        // 验证 38.5 和 38.50 在内部转换为分后一致
        DedupManager.isDuplicate(38.5, "notification", "com.tencent.mm")
        val isDup = DedupManager.isDuplicate(38.50, "notification", "com.tencent.mm")
        assertTrue("38.5 和 38.50 应判重（分单位一致）", isDup)
    }

    @Test
    fun `清空缓存后应不判重`() {
        DedupManager.isDuplicate(38.50, "notification", "com.tencent.mm")
        assertTrue(DedupManager.isDuplicate(38.50, "notification", "com.tencent.mm"))

        // 清空后再检测
        DedupManager.clear()
        val isDup = DedupManager.isDuplicate(38.50, "notification", "com.tencent.mm")
        assertFalse("清空缓存后不应判重", isDup)
    }

    @Test
    fun `缓存大小应正确`() {
        assertEquals(0, DedupManager.cacheSize())

        DedupManager.isDuplicate(38.50, "notification", "com.tencent.mm")
        assertEquals(1, DedupManager.cacheSize())

        DedupManager.isDuplicate(50.00, "notification", "com.tencent.mm")
        assertEquals(2, DedupManager.cacheSize())

        // 重复的不应增加缓存
        DedupManager.isDuplicate(38.50, "notification", "com.tencent.mm")
        assertEquals(2, DedupManager.cacheSize())
    }

    // ========== 回归测试：修复双重拒绝 bug ==========

    /**
     * 回归：通知被拒绝时，屏幕检测不应被跨来源去重跳过
     *
     * 场景：通知先到，被营销过滤拒绝（未调用 markRecorded）。
     * 屏幕后到，跨来源去重检查时，通知的条目 recorded=false，
     * 不应阻止屏幕记录。
     *
     * 修复前：isDuplicateAcrossSources 不检查 recorded，会误判为重复，
     * 导致"通知被拒绝 → 屏幕也被跳过"的双重拒绝问题。
     * 修复后：只对 recorded=true 的条目判重。
     */
    @Test
    fun `回归 - 未入库的检测不应阻止其他来源记录`() {
        // 通知检测到 38.50，但被营销过滤拒绝（未调用 markRecorded）
        DedupManager.isDuplicate(38.50, "notification", "com.tencent.mm")
        // 注意：不调用 markRecorded，模拟通知被拒绝的场景

        // 屏幕也检测到 38.50
        val crossDup = DedupManager.isDuplicateAcrossSources(
            amount = 38.50,
            excludeSourceType = "screen"
        )
        assertFalse("通知未入库，屏幕不应被跨来源去重跳过", crossDup)
    }

    /**
     * 回归：已入库的检测应阻止其他来源记录
     *
     * 场景：通知成功记录到数据库（调用了 markRecorded）。
     * 屏幕后到，应被跨来源去重跳过。
     */
    @Test
    fun `回归 - 已入库的检测应阻止其他来源记录`() {
        // 通知检测到 38.50 并成功入库
        DedupManager.isDuplicate(38.50, "notification", "com.tencent.mm")
        DedupManager.markRecorded(38.50, "notification", "com.tencent.mm")

        // 屏幕也检测到 38.50
        val crossDup = DedupManager.isDuplicateAcrossSources(
            amount = 38.50,
            excludeSourceType = "screen"
        )
        assertTrue("通知已入库，屏幕应被跨来源去重跳过", crossDup)
    }

    /**
     * 回归：markRecorded 应正确标记条目
     */
    @Test
    fun `回归 - markRecorded 应正确标记条目`() {
        // 检测到但未入库
        DedupManager.isDuplicate(38.50, "notification", "com.tencent.mm")
        assertFalse(DedupManager.isDuplicateAcrossSources(38.50, "screen"))

        // 标记为已入库
        DedupManager.markRecorded(38.50, "notification", "com.tencent.mm")
        assertTrue(DedupManager.isDuplicateAcrossSources(38.50, "screen"))
    }

    @Test
    fun `短信来源与通知来源应独立去重`() {
        // 短信记录 38.50 并入库
        DedupManager.isDuplicate(38.50, "sms", "95588")
        DedupManager.markRecorded(38.50, "sms", "95588")

        // 通知也检测到 38.50（不同来源类型，应不判重）
        val isDup = DedupManager.isDuplicate(38.50, "notification", "com.tencent.mm")
        assertFalse("短信和通知是不同来源，应独立记录", isDup)

        // 但跨来源检查应发现重复（短信已入库）
        val crossDup = DedupManager.isDuplicateAcrossSources(
            amount = 38.50,
            excludeSourceType = "notification"
        )
        assertTrue("跨来源检查应发现短信已入库", crossDup)
    }
}
