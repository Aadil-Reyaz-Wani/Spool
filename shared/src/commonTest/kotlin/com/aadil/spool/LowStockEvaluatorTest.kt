package com.aadil.spool

import com.aadil.spool.core.model.preferences.LowStockEvaluator
import com.aadil.spool.core.model.preferences.NotificationPrefs
import com.aadil.spool.core.model.preferences.SpoolAlertConfig
import com.aadil.spool.core.model.preferences.SpoolStock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LowStockEvaluatorTest {

    private val day = 86_400_000L

    @Test
    fun `threshold - smart default by spool size`() {
        assertEquals(100.0, LowStockEvaluator.thresholdGrams(250.0, null))
        assertEquals(200.0, LowStockEvaluator.thresholdGrams(1000.0, null))
    }

    @Test
    fun `threshold - explicit override wins`() {
        assertEquals(50.0, LowStockEvaluator.thresholdGrams(1000.0, 50.0))
    }

    @Test
    fun `isLow - boundary and empty spool`() {
        assertTrue(LowStockEvaluator.isLow(100.0, 250.0, null))
        assertFalse(LowStockEvaluator.isLow(101.0, 250.0, null))
        assertFalse(LowStockEvaluator.isLow(0.0, 250.0, null))
    }

    @Test
    fun `quiet hours wrap around midnight`() {
        val start = 22 * 60
        val end = 8 * 60
        assertTrue(LowStockEvaluator.isInQuietHours(23 * 60 + 30, start, end))
        assertTrue(LowStockEvaluator.isInQuietHours(6 * 60, start, end))
        assertTrue(LowStockEvaluator.isInQuietHours(8 * 60, start, end))
        assertFalse(LowStockEvaluator.isInQuietHours(12 * 60, start, end))
    }

    @Test
    fun `quiet hours same-day window`() {
        val start = 9 * 60
        val end = 18 * 60
        assertTrue(LowStockEvaluator.isInQuietHours(12 * 60, start, end))
        assertFalse(LowStockEvaluator.isInQuietHours(20 * 60, start, end))
    }

    @Test
    fun `cooldown suppresses within window only`() {
        assertFalse(LowStockEvaluator.isInCooldown(0L, 7, nowMillis = day * 10))
        assertTrue(LowStockEvaluator.isInCooldown(day * 10 - 3 * day, 7, nowMillis = day * 10))
        assertFalse(LowStockEvaluator.isInCooldown(day * 10 - 8 * day, 7, nowMillis = day * 10))
    }

    @Test
    fun `quota resets on new day`() {
        val prefs = NotificationPrefs(maxPerDay = 3, todayCount = 2, todayEpochDay = 5)
        assertEquals(1, LowStockEvaluator.remainingQuota(prefs, nowEpochDay = 5))
        assertEquals(3, LowStockEvaluator.remainingQuota(prefs, nowEpochDay = 6))
    }

    @Test
    fun `eligibleSpools applies all gates`() {
        val now = day * 100
        val stocks = listOf(
            SpoolStock(1, "A", 80.0, 250.0),
            SpoolStock(2, "B", 80.0, 250.0),
            SpoolStock(3, "C", 500.0, 1000.0),
            SpoolStock(4, "D", 0.0, 250.0),
            SpoolStock(5, "E", 80.0, 250.0),
        )
        val configs = mapOf(
            2 to SpoolAlertConfig(2, enabled = false),
            5 to SpoolAlertConfig(5, lastNotifiedAt = now - day),
        )
        val prefs = NotificationPrefs()

        val eligible = LowStockEvaluator.eligibleSpools(
            stocks, configs, prefs,
            nowMillis = now,
            minuteOfDay = 12 * 60,
        )
        assertEquals(listOf(1), eligible.map { it.id })
    }

    @Test
    fun `eligibleSpools - already-alerted spool suppressed until re-arm`() {
        val now = day * 100
        val stocks = listOf(SpoolStock(1, "A", 80.0, 250.0))
        val configs = mapOf(1 to SpoolAlertConfig(1, alertedWhileLow = true))
        val eligible = LowStockEvaluator.eligibleSpools(
            stocks, configs, NotificationPrefs(),
            nowMillis = now, minuteOfDay = 600,
        )
        assertTrue(eligible.isEmpty())
    }

    @Test
    fun `eligibleSpools - global switch off means nothing`() {
        val stocks = listOf(SpoolStock(1, "A", 80.0, 250.0))
        val eligible = LowStockEvaluator.eligibleSpools(
            stocks, emptyMap(), NotificationPrefs(enabled = false),
            nowMillis = 0L, minuteOfDay = 600,
        )
        assertTrue(eligible.isEmpty())
    }
}
