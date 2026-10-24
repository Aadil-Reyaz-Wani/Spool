package com.aadil.spool.core.model.preferences

import kotlinx.serialization.Serializable

const val MILLIS_PER_DAY: Long = 86_400_000L

@Serializable
data class NotificationPrefs(
    val enabled: Boolean = true,
    val quietStartMinutes: Int = 22 * 60,
    val quietEndMinutes: Int = 8 * 60,
    val cooldownDays: Int = 7,
    val maxPerDay: Int = 3,
    val todayCount: Int = 0,
    val todayEpochDay: Long = 0L,
)

@Serializable
data class SpoolAlertConfig(
    val spoolId: Int,
    val enabled: Boolean = true,
    val thresholdGrams: Double? = null,
    val reorderUrl: String? = null,
    val lastNotifiedAt: Long = 0L,

    /** Edge-trigger: set when we alert, cleared once the spool goes back above threshold. */
    val alertedWhileLow: Boolean = false,
)

data class SpoolStock(
    val id: Int,
    val label: String,
    val currentWeight: Double,
    val totalWeight: Double,
)

object LowStockEvaluator {

    private const val SMALL_SPOOL_GRAMS = 500.0
    private const val SMALL_SPOOL_THRESHOLD = 100.0
    private const val LARGE_SPOOL_FRACTION = 0.20

    fun thresholdGrams(totalWeight: Double, overrideGrams: Double?): Double =
        overrideGrams
            ?: (if (totalWeight < SMALL_SPOOL_GRAMS) SMALL_SPOOL_THRESHOLD else totalWeight * LARGE_SPOOL_FRACTION)

    fun isLow(currentWeight: Double, totalWeight: Double, overrideGrams: Double?): Boolean {
        if (currentWeight <= 0.0) return false
        return currentWeight <= thresholdGrams(totalWeight, overrideGrams)
    }

    fun isInCooldown(lastNotifiedAt: Long, cooldownDays: Int, nowMillis: Long): Boolean =
        lastNotifiedAt > 0 && nowMillis - lastNotifiedAt < cooldownDays * MILLIS_PER_DAY

    /** Quiet hours wrap midnight when start > end, e.g. 22:00 -> 08:00. */
    fun isInQuietHours(minuteOfDay: Int, startMinute: Int, endMinute: Int): Boolean =
        if (startMinute <= endMinute) {
            minuteOfDay in startMinute..endMinute
        } else {
            minuteOfDay >= startMinute || minuteOfDay <= endMinute
        }

    fun remainingQuota(prefs: NotificationPrefs, nowEpochDay: Long): Int =
        if (prefs.todayEpochDay == nowEpochDay) {
            (prefs.maxPerDay - prefs.todayCount).coerceAtLeast(0)
        } else {
            prefs.maxPerDay
        }

    /**
     * Spools that should alert right now, after all gates (global switch, quiet hours,
     * per-spool enable, threshold, cooldown). Daily quota is applied by the caller so a
     * batch counts as one slot.
     */
    fun eligibleSpools(
        spools: List<SpoolStock>,
        configs: Map<Int, SpoolAlertConfig>,
        prefs: NotificationPrefs,
        nowMillis: Long,
        minuteOfDay: Int,
    ): List<SpoolStock> {
        if (!prefs.enabled) return emptyList()
        if (isInQuietHours(minuteOfDay, prefs.quietStartMinutes, prefs.quietEndMinutes)) return emptyList()
        return spools.filter { stock ->
            val config = configs[stock.id] ?: SpoolAlertConfig(stock.id)
            config.enabled &&
                !config.alertedWhileLow &&
                !isInCooldown(config.lastNotifiedAt, prefs.cooldownDays, nowMillis) &&
                isLow(stock.currentWeight, stock.totalWeight, config.thresholdGrams)
        }
    }
}
