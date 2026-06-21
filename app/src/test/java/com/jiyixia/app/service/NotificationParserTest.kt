package com.jiyixia.app.service

import org.junit.Assert.*
import org.junit.Test

/**
 * NotificationParser 单元测试
 *
 * 验证：
 * 1. 金额严格提取（¥/￥/金额：/消费X元）
 * 2. 营销通知过滤
 * 3. 微信支付/支付宝/银行短信模板解析
 * 4. 商户名提取
 *
 * 注：android.util.Log 通过 build.gradle.kts 的
 * testOptions.unitTests.isReturnDefaultValues = true 处理
 */
class NotificationParserTest {

    // 无状态对象，无需 setup/tearDown

    // ========== 营销通知过滤 ==========

    @Test
    fun `营销通知应被拒绝 - 优惠券`() {
        val content = NotificationParser.NotificationContent(
            title = "支付宝",
            subText = "",
            text = "你的优惠券即将过期，立即使用",
            bigText = "",
            summaryText = "",
            textLines = emptyList(),
            packageName = "com.eg.android.AlipayGphone",
            allText = "支付宝 你的优惠券即将过期，立即使用"
        )
        val result = NotificationParser.extractTransaction(content)
        assertNull("含'优惠券'的营销通知应被拒绝", result)
    }

    @Test
    fun `营销通知应被拒绝 - 红包雨`() {
        val content = NotificationParser.NotificationContent(
            title = "支付宝",
            subText = "",
            text = "红包雨来袭，立即抢红包",
            bigText = "",
            summaryText = "",
            textLines = emptyList(),
            packageName = "com.eg.android.AlipayGphone",
            allText = "支付宝 红包雨来袭"
        )
        val result = NotificationParser.extractTransaction(content)
        assertNull("含'红包雨'的营销通知应被拒绝", result)
    }

    @Test
    fun `营销通知应被拒绝 - 积分兑换`() {
        val content = NotificationParser.NotificationContent(
            title = "支付宝",
            subText = "",
            text = "积分兑换：你的1000积分可兑换礼品",
            bigText = "",
            summaryText = "",
            textLines = emptyList(),
            packageName = "com.eg.android.AlipayGphone",
            allText = "积分兑换"
        )
        val result = NotificationParser.extractTransaction(content)
        assertNull("含'积分兑换'的营销通知应被拒绝", result)
    }

    @Test
    fun `退款通知含营销词但应保留`() {
        // 退款通知可能含"返现"等词，但应保留
        val content = NotificationParser.NotificationContent(
            title = "支付宝",
            subText = "",
            text = "退款到账 ¥38.00",
            bigText = "退款金额：¥38.00",
            summaryText = "",
            textLines = emptyList(),
            packageName = "com.eg.android.AlipayGphone",
            allText = "支付宝 退款到账 ¥38.00 退款金额：¥38.00"
        )
        val result = NotificationParser.extractTransaction(content)
        assertNotNull("退款通知含'返现'类词但应保留", result)
        assertEquals(38.00, result!!.amount, 0.001)
    }

    // ========== 微信支付模板 ==========

    @Test
    fun `微信支付通知应正确解析金额`() {
        val content = NotificationParser.NotificationContent(
            title = "微信支付",
            subText = "",
            text = "¥38.00",
            bigText = "商户：星巴克\n金额：¥38.00\n时间：2026-06-21 12:30",
            summaryText = "",
            textLines = emptyList(),
            packageName = "com.tencent.mm",
            allText = "微信支付 ¥38.00 商户：星巴克 金额：¥38.00 时间：2026-06-21 12:30"
        )
        val result = NotificationParser.extractTransaction(content)
        assertNotNull(result)
        assertEquals(38.00, result!!.amount, 0.001)
        assertEquals("星巴克", result.merchantName)
        assertEquals("微信支付", result.scene)
        assertFalse(result.isMarketing)
    }

    @Test
    fun `微信支付通知应解析转账场景`() {
        val content = NotificationParser.NotificationContent(
            title = "微信支付",
            subText = "",
            text = "¥100.00",
            bigText = "转账给张三\n金额：¥100.00",
            summaryText = "",
            textLines = emptyList(),
            packageName = "com.tencent.mm",
            allText = "微信支付 ¥100.00 转账给张三 金额：¥100.00"
        )
        val result = NotificationParser.extractTransaction(content)
        assertNotNull(result)
        assertEquals(100.00, result!!.amount, 0.001)
        assertEquals("微信转账", result.scene)
        assertEquals("张三", result.merchantName)
    }

    @Test
    fun `微信支付通知应解析退款场景`() {
        val content = NotificationParser.NotificationContent(
            title = "微信支付",
            subText = "",
            text = "退款到账 ¥50.00",
            bigText = "退款金额：¥50.00",
            summaryText = "",
            textLines = emptyList(),
            packageName = "com.tencent.mm",
            allText = "微信支付 退款到账 ¥50.00 退款金额：¥50.00"
        )
        val result = NotificationParser.extractTransaction(content)
        assertNotNull(result)
        assertEquals(50.00, result!!.amount, 0.001)
        assertEquals("微信退款", result.scene)
    }

    @Test
    fun `微信非官方账号通知应被拒绝`() {
        val content = NotificationParser.NotificationContent(
            title = "张三",  // 非官方账号
            subText = "",
            text = "¥38.00",
            bigText = "",
            summaryText = "",
            textLines = emptyList(),
            packageName = "com.tencent.mm",
            allText = "张三 ¥38.00"
        )
        val result = NotificationParser.extractTransaction(content)
        assertNull("微信非官方账号通知应被拒绝", result)
    }

    // ========== 支付宝模板 ==========

    @Test
    fun `支付宝通知应正确解析`() {
        val content = NotificationParser.NotificationContent(
            title = "支付宝",
            subText = "",
            text = "在星巴克消费¥38.00",
            bigText = "",
            summaryText = "",
            textLines = emptyList(),
            packageName = "com.eg.android.AlipayGphone",
            allText = "支付宝 在星巴克消费¥38.00"
        )
        val result = NotificationParser.extractTransaction(content)
        assertNotNull(result)
        assertEquals(38.00, result!!.amount, 0.001)
        assertEquals("星巴克", result.merchantName)
        assertEquals("支付宝支付", result.scene)
    }

    @Test
    fun `支付宝通知应解析收款场景`() {
        val content = NotificationParser.NotificationContent(
            title = "支付宝",
            subText = "",
            text = "收款到账 ¥200.00",
            bigText = "",
            summaryText = "",
            textLines = emptyList(),
            packageName = "com.eg.android.AlipayGphone",
            allText = "支付宝 收款到账 ¥200.00"
        )
        val result = NotificationParser.extractTransaction(content)
        assertNotNull(result)
        assertEquals(200.00, result!!.amount, 0.001)
        assertEquals("支付宝收款", result.scene)
    }

    // ========== 银行短信模板 ==========

    @Test
    fun `银行短信应正确解析消费`() {
        val content = NotificationParser.parseSms(
            sender = "95588",
            body = "【工商银行】您尾号1234信用卡于06月21日12:30在星巴克消费人民币38.00元"
        )
        val result = NotificationParser.extractTransaction(content)
        assertNotNull(result)
        assertEquals(38.00, result!!.amount, 0.001)
        assertEquals("银行消费", result.scene)
        assertEquals("星巴克", result.merchantName)
    }

    @Test
    fun `银行短信应解析到账场景为收入`() {
        val content = NotificationParser.parseSms(
            sender = "95588",
            body = "【工商银行】您账户于06月21日到账人民币5000.00元"
        )
        val result = NotificationParser.extractTransaction(content)
        assertNotNull(result)
        assertEquals(5000.00, result!!.amount, 0.001)
        assertEquals("银行到账", result.scene)
    }

    @Test
    fun `银行短信无消费词应被拒绝`() {
        val content = NotificationParser.parseSms(
            sender = "95588",
            body = "【工商银行】您的信用卡账单已生成，请按时还款"
        )
        val result = NotificationParser.extractTransaction(content)
        assertNull("无消费/扣款/到账词的短信应被拒绝", result)
    }

    // ========== 金额提取严格性 ==========

    @Test
    fun `纯数字不应被识别为金额`() {
        // 验证不会把订单号、时间等数字误识别为金额
        val content = NotificationParser.NotificationContent(
            title = "微信支付",
            subText = "",
            text = "订单号 20260621123045678",
            bigText = "",
            summaryText = "",
            textLines = emptyList(),
            packageName = "com.tencent.mm",
            allText = "微信支付 订单号 20260621123045678"
        )
        val result = NotificationParser.extractTransaction(content)
        assertNull("纯订单号不应被识别为金额", result)
    }

    @Test
    fun `金额带角分应正确解析`() {
        val content = NotificationParser.NotificationContent(
            title = "微信支付",
            subText = "",
            text = "¥38.50",
            bigText = "金额：¥38.50",
            summaryText = "",
            textLines = emptyList(),
            packageName = "com.tencent.mm",
            allText = "微信支付 ¥38.50 金额：¥38.50"
        )
        val result = NotificationParser.extractTransaction(content)
        assertNotNull(result)
        assertEquals(38.50, result!!.amount, 0.001)
    }

    @Test
    fun `金额为零应被拒绝`() {
        val content = NotificationParser.NotificationContent(
            title = "微信支付",
            subText = "",
            text = "¥0.00",
            bigText = "金额：¥0.00",
            summaryText = "",
            textLines = emptyList(),
            packageName = "com.tencent.mm",
            allText = "微信支付 ¥0.00 金额：¥0.00"
        )
        val result = NotificationParser.extractTransaction(content)
        assertNull("金额为0应被拒绝", result)
    }

    @Test
    fun `金额过大应被拒绝`() {
        val content = NotificationParser.NotificationContent(
            title = "微信支付",
            subText = "",
            text = "¥1000000.00",  // 100万，超过上限
            bigText = "金额：¥1000000.00",
            summaryText = "",
            textLines = emptyList(),
            packageName = "com.tencent.mm",
            allText = "微信支付 ¥1000000.00"
        )
        val result = NotificationParser.extractTransaction(content)
        assertNull("金额超过100万应被拒绝（防误识别）", result)
    }

    // ========== 通用模板 ==========

    @Test
    fun `通用app通知含支付成功词应解析`() {
        val content = NotificationParser.NotificationContent(
            title = "美团外卖",
            subText = "",
            text = "支付成功 ¥25.50",
            bigText = "",
            summaryText = "",
            textLines = emptyList(),
            packageName = "com.sankuai.meituan.takeoutnew",
            allText = "美团外卖 支付成功 ¥25.50"
        )
        val result = NotificationParser.extractTransaction(content)
        assertNotNull(result)
        assertEquals(25.50, result!!.amount, 0.001)
    }

    @Test
    fun `通用app通知无支付成功词应被拒绝`() {
        val content = NotificationParser.NotificationContent(
            title = "美团外卖",
            subText = "",
            text = "您的订单已发货",
            bigText = "",
            summaryText = "",
            textLines = emptyList(),
            packageName = "com.sankuai.meituan.takeoutnew",
            allText = "美团外卖 您的订单已发货"
        )
        val result = NotificationParser.extractTransaction(content)
        assertNull("无支付成功词的通知应被拒绝", result)
    }
}
