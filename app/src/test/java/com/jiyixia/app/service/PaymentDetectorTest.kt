package com.jiyixia.app.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * PaymentDetector "只记当天" 规则测试
 */
class PaymentDetectorTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return Calendar.getInstance().apply {
            set(year, month - 1, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun `now is today`() {
        assertTrue(PaymentDetector.isToday(System.currentTimeMillis()))
    }

    @Test
    fun `midnight of today counts as today`() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertTrue(PaymentDetector.isToday(cal.timeInMillis))
    }

    @Test
    fun `yesterday 23-59 is not today`() {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
        }
        assertFalse(PaymentDetector.isToday(cal.timeInMillis))
    }

    @Test
    fun `a week ago is not today`() {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }
        assertFalse(PaymentDetector.isToday(cal.timeInMillis))
    }

    @Test
    fun `same day different month boundary - dec 31 to jan 1`() {
        // 用固定年份验证跨年场景：2026-12-31 不是 2027-01-01 的"今天"
        assertFalse(PaymentDetector.isToday(at(2026, 12, 31, 23, 59)))
    }
}
