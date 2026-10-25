package com.aadil.spool.notifications

import com.aadil.spool.core.data.repository.NotificationPreferencesRepository
import com.aadil.spool.core.data.repository.SpoolRepository
import com.aadil.spool.core.model.preferences.LowStockEvaluator
import com.aadil.spool.core.model.preferences.SpoolStock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import spool.shared.generated.resources.Res
import spool.shared.generated.resources.action_reorder
import spool.shared.generated.resources.low_stock_notif_batch_body
import spool.shared.generated.resources.low_stock_notif_batch_title
import spool.shared.generated.resources.low_stock_notif_body
import spool.shared.generated.resources.low_stock_notif_title

data class LowStockItem(
    val spoolId: Int,
    val title: String,
    val body: String,
    val reorderUrl: String?,
)

/** Platform seam for displaying low-stock notifications (Android NotificationManager / iOS UNUserNotificationCenter). */
interface NotificationPoster {
    fun post(items: List<LowStockItem>, batched: Boolean, actionLabel: String)
}

// Serializes checks: app-open and WorkManager can fire concurrently; quota/cooldown state must be read-modify-write.
private val checkMutex = Mutex()

/**
 * Evaluates all spools against alert configs and posts notifications via [poster].
 * Called from the Android WorkManager worker and on app open.
 */
suspend fun runLowStockCheck(
    spoolRepository: SpoolRepository,
    alertRepository: NotificationPreferencesRepository,
    poster: NotificationPoster,
) = checkMutex.withLock {
    val prefs = alertRepository.loadPrefs()
    if (!prefs.enabled) return

    val now = Clock.System.now()
    val local = now.toLocalDateTime(TimeZone.currentSystemDefault())
    val nowMillis = now.toEpochMilliseconds()
    val minuteOfDay = local.hour * 60 + local.minute
    val epochDay = local.date.toEpochDays().toLong()

    val configs = alertRepository.spoolConfigs().first()
    val stocks = spoolRepository.getAllSpools().first().map {
        SpoolStock(it.id, "${it.brand} ${it.colorName}", it.currentWeight, it.totalWeight)
    }

    // Re-arm: any spool that alerted and is now back above threshold becomes eligible again.
    // Runs before the enabled/quiet-hours gates so refills always clear the flag.
    configs.values.filter { it.alertedWhileLow }.forEach { config ->
        val stock = stocks.find { it.id == config.spoolId }
        if (stock != null && !LowStockEvaluator.isLow(stock.currentWeight, stock.totalWeight, config.thresholdGrams)) {
            alertRepository.saveSpoolConfig(config.copy(alertedWhileLow = false))
        }
    }

    val eligible = LowStockEvaluator.eligibleSpools(stocks, configs, prefs, nowMillis, minuteOfDay)
    if (eligible.isEmpty()) return
    val quota = LowStockEvaluator.remainingQuota(prefs, epochDay)
    if (quota <= 0) return

    // A batched summary covers ALL eligible spools and consumes a single slot; never
    // truncate it by quota or the leftover spools fire as extra notifications on the
    // next check. Only individual notifications respect the remaining quota.
    val batched = eligible.size >= 2
    val toNotify = if (batched) eligible else eligible.take(quota)

    val items = if (batched) {
        listOf(
            LowStockItem(
                spoolId = -1,
                title = getString(Res.string.low_stock_notif_batch_title),
                body = getString(Res.string.low_stock_notif_batch_body, toNotify.size),
                reorderUrl = null,
            )
        )
    } else {
        toNotify.map { stock ->
            val config = configs[stock.id]
            LowStockItem(
                spoolId = stock.id,
                title = getString(Res.string.low_stock_notif_title),
                body = getString(Res.string.low_stock_notif_body, stock.label, stock.currentWeight.toInt()),
                reorderUrl = config?.reorderUrl?.takeIf { it.isNotBlank() },
            )
        }
    }

    poster.post(items, batched, getString(Res.string.action_reorder))
    alertRepository.markNotified(toNotify.map { it.id }, nowMillis, epochDay, items.size)
}