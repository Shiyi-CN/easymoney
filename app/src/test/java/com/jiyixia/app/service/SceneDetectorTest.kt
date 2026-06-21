package com.jiyixia.app.service

import org.junit.Assert.*
import org.junit.Test

/**
 * SceneDetector 单元测试
 *
 * 验证场景识别：
 * 1. 退款 → 收入
 * 2. 工资/报销 → 收入
 * 3. 还款 → 支出（特殊类型）
 * 4. 主动转账 → 支出
 * 5. 被动收款 → 收入
 * 6. 出行 app → 交通支出
 * 7. 外卖 app → 餐饮支出
 * 8. 购物 app → 购物支出
 * 9. 银行短信到账 → 收入
 *
 * 注：android.util.Log 通过 build.gradle.kts 的
 * testOptions.unitTests.isReturnDefaultValues = true 处理
 */
class SceneDetectorTest {

    private fun makeParsed(
        title: String = "微信支付",
        text: String = "¥38.00",
        bigText: String = "",
        packageName: String = "com.tencent.mm",
        amount: Double = 38.00,
        merchantName: String = "",
        scene: String = "微信支付",
        isMarketing: Boolean = false
    ): NotificationParser.ParsedNotification {
        val allText = listOf(title, text, bigText).filter { it.isNotBlank() }.joinToString(" ")
        val content = NotificationParser.NotificationContent(
            title = title,
            subText = "",
            text = text,
            bigText = bigText,
            summaryText = "",
            textLines = emptyList(),
            packageName = packageName,
            allText = allText
        )
        return NotificationParser.ParsedNotification(
            amount = amount,
            merchantName = merchantName,
            scene = scene,
            isMarketing = isMarketing,
            content = content
        )
    }

    // ========== 退款场景 ==========

    @Test
    fun `退款应识别为收入`() {
        val parsed = makeParsed(
            text = "退款到账 ¥50.00",
            scene = "微信退款"
        )
        val result = SceneDetector.detect(parsed)
        assertEquals(SceneDetector.SceneType.REFUND, result.sceneType)
        assertFalse("退款应为收入", result.isExpense)
        assertEquals("退款", result.categoryName)
        assertTrue("置信度应>=85", result.confidence >= 85)
    }

    // ========== 工资/收入场景 ==========

    @Test
    fun `工资应识别为收入`() {
        val parsed = makeParsed(
            title = "工商银行",
            text = "工资到账 ¥5000.00",
            packageName = "sms:95588",
            scene = "银行到账"
        )
        val result = SceneDetector.detect(parsed)
        assertEquals(SceneDetector.SceneType.SALARY_INCOME, result.sceneType)
        assertFalse("工资应为收入", result.isExpense)
        assertEquals("工资", result.categoryName)
    }

    @Test
    fun `报销到账应识别为收入`() {
        val parsed = makeParsed(
            text = "报销到账 ¥200.00",
            scene = "微信支付"
        )
        val result = SceneDetector.detect(parsed)
        assertEquals(SceneDetector.SceneType.REIMBURSE_INCOME, result.sceneType)
        assertFalse("报销应为收入", result.isExpense)
        assertEquals("报销", result.categoryName)
    }

    @Test
    fun `理财收益应识别为收入`() {
        val parsed = makeParsed(
            title = "支付宝",
            text = "理财收益到账 ¥12.50",
            packageName = "com.eg.android.AlipayGphone",
            scene = "支付宝收款"
        )
        val result = SceneDetector.detect(parsed)
        assertFalse("理财收益应为收入", result.isExpense)
        assertEquals("理财", result.categoryName)
    }

    // ========== 还款场景 ==========

    @Test
    fun `信用卡还款应识别为支出`() {
        val parsed = makeParsed(
            text = "信用卡还款 ¥2000.00",
            scene = "微信支付"
        )
        val result = SceneDetector.detect(parsed)
        assertEquals(SceneDetector.SceneType.LOAN_REPAYMENT, result.sceneType)
        assertTrue("还款应为支出", result.isExpense)
        assertEquals("还款", result.categoryName)
    }

    // ========== 转账场景 ==========

    @Test
    fun `主动转账给他人应识别为支出`() {
        val parsed = makeParsed(
            text = "转账给张三 ¥100.00",
            scene = "微信转账"
        )
        val result = SceneDetector.detect(parsed)
        assertEquals(SceneDetector.SceneType.TRANSFER_OUTGOING, result.sceneType)
        assertTrue("主动转账应为支出", result.isExpense)
        assertEquals("转账", result.categoryName)
    }

    @Test
    fun `被动收款应识别为收入`() {
        val parsed = makeParsed(
            text = "收款到账 ¥200.00",
            scene = "微信支付"
        )
        val result = SceneDetector.detect(parsed)
        assertEquals(SceneDetector.SceneType.INCOME_RECEIVE, result.sceneType)
        assertFalse("被动收款应为收入", result.isExpense)
    }

    // ========== 基于包名的场景识别 ==========

    @Test
    fun `滴滴出行应识别为交通支出`() {
        val parsed = makeParsed(
            title = "滴滴出行",
            text = "支付成功 ¥25.50",
            packageName = "com.sdu.didi.psnger",
            merchantName = "滴滴出行",
            scene = "支付"
        )
        val result = SceneDetector.detect(parsed)
        assertEquals(SceneDetector.SceneType.EXPENSE_PAYMENT, result.sceneType)
        assertTrue("打车应为支出", result.isExpense)
        assertEquals("交通", result.categoryName)
    }

    @Test
    fun `高德地图应识别为交通支出`() {
        val parsed = makeParsed(
            title = "高德地图",
            text = "支付成功 ¥18.00",
            packageName = "com.autonavi.minimap",
            scene = "支付"
        )
        val result = SceneDetector.detect(parsed)
        assertEquals("交通", result.categoryName)
        assertTrue(result.isExpense)
    }

    @Test
    fun `美团外卖应识别为餐饮支出`() {
        val parsed = makeParsed(
            title = "美团外卖",
            text = "支付成功 ¥38.00",
            packageName = "com.sankuai.meituan.takeoutnew",
            scene = "支付"
        )
        val result = SceneDetector.detect(parsed)
        assertEquals(SceneDetector.SceneType.EXPENSE_PAYMENT, result.sceneType)
        assertTrue("外卖应为支出", result.isExpense)
        assertEquals("餐饮", result.categoryName)
    }

    @Test
    fun `饿了么应识别为餐饮支出`() {
        val parsed = makeParsed(
            title = "饿了么",
            text = "支付成功 ¥45.00",
            packageName = "me.ele",
            scene = "支付"
        )
        val result = SceneDetector.detect(parsed)
        assertEquals("餐饮", result.categoryName)
    }

    @Test
    fun `淘宝应识别为购物支出`() {
        val parsed = makeParsed(
            title = "淘宝",
            text = "支付成功 ¥199.00",
            packageName = "com.taobao.taobao",
            scene = "支付"
        )
        val result = SceneDetector.detect(parsed)
        assertEquals(SceneDetector.SceneType.EXPENSE_PAYMENT, result.sceneType)
        assertTrue("购物应为支出", result.isExpense)
        assertEquals("购物", result.categoryName)
    }

    @Test
    fun `京东应识别为购物支出`() {
        val parsed = makeParsed(
            title = "京东",
            text = "支付成功 ¥299.00",
            packageName = "com.jingdong.app.mall",
            scene = "支付"
        )
        val result = SceneDetector.detect(parsed)
        assertEquals("购物", result.categoryName)
    }

    // ========== 综合平台场景识别 ==========

    @Test
    fun `美团综合平台含外卖线索应识别为餐饮`() {
        val parsed = makeParsed(
            title = "美团",
            text = "外卖支付成功 ¥38.00",
            packageName = "com.sankuai.meituan",
            scene = "支付"
        )
        val result = SceneDetector.detect(parsed)
        assertEquals("餐饮", result.categoryName)
    }

    @Test
    fun `美团综合平台含打车线索应识别为交通`() {
        val parsed = makeParsed(
            title = "美团",
            text = "打车行程支付 ¥25.00",
            packageName = "com.sankuai.meituan",
            scene = "支付"
        )
        val result = SceneDetector.detect(parsed)
        assertEquals("交通", result.categoryName)
    }

    // ========== 商户名推断分类 ==========

    @Test
    fun `微信支付星巴克应识别为餐饮`() {
        val parsed = makeParsed(
            text = "¥38.00",
            merchantName = "星巴克",
            scene = "微信支付"
        )
        val result = SceneDetector.detect(parsed)
        assertEquals("餐饮", result.categoryName)
    }

    @Test
    fun `微信支付麦当劳应识别为餐饮`() {
        val parsed = makeParsed(
            text = "¥28.00",
            merchantName = "麦当劳",
            scene = "微信支付"
        )
        val result = SceneDetector.detect(parsed)
        assertEquals("餐饮", result.categoryName)
    }

    @Test
    fun `微信支付滴滴应识别为交通`() {
        val parsed = makeParsed(
            text = "¥25.00",
            merchantName = "滴滴",
            scene = "微信支付"
        )
        val result = SceneDetector.detect(parsed)
        assertEquals("交通", result.categoryName)
    }

    @Test
    fun `微信支付医院应识别为医疗`() {
        val parsed = makeParsed(
            text = "¥200.00",
            merchantName = "协和医院",
            scene = "微信支付"
        )
        val result = SceneDetector.detect(parsed)
        assertEquals("医疗", result.categoryName)
    }

    // ========== 银行短信场景 ==========

    @Test
    fun `银行短信到账应识别为收入`() {
        val parsed = makeParsed(
            title = "95588",
            text = "【工商银行】您账户到账人民币5000.00元",
            packageName = "sms:95588",
            scene = "银行到账"
        )
        val result = SceneDetector.detect(parsed)
        assertEquals(SceneDetector.SceneType.INCOME_RECEIVE, result.sceneType)
        assertFalse("银行到账应为收入", result.isExpense)
    }

    @Test
    fun `银行短信消费应识别为支出`() {
        val parsed = makeParsed(
            title = "95588",
            text = "【工商银行】您尾号1234信用卡消费人民币38.00元",
            packageName = "sms:95588",
            scene = "银行消费"
        )
        val result = SceneDetector.detect(parsed)
        assertEquals(SceneDetector.SceneType.EXPENSE_PAYMENT, result.sceneType)
        assertTrue("银行消费应为支出", result.isExpense)
    }

    // ========== 营销场景 ==========

    @Test
    fun `营销通知应识别为MARKETING类型`() {
        val parsed = makeParsed(
            text = "¥0.01",
            isMarketing = true
        )
        val result = SceneDetector.detect(parsed)
        assertEquals(SceneDetector.SceneType.MARKETING, result.sceneType)
        assertEquals(0, result.confidence)
    }
}
