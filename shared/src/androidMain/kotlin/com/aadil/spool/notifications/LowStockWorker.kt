package com.aadil.spool.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aadil.spool.core.data.repository.NotificationPreferencesRepository
import com.aadil.spool.core.data.repository.SpoolRepository
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

val androidNotificationsModule = module {
    single<NotificationPoster> { AndroidNotificationPoster(get()) }
}

class LowStockWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        AndroidNotificationPoster.ensureChannel(applicationContext)
        val koin = GlobalContext.get()
        runLowStockCheck(
            spoolRepository = koin.get<SpoolRepository>(),
            alertRepository = koin.get<NotificationPreferencesRepository>(),
            poster = koin.get(),
        )
        return Result.success()
    }
}

/** Periodic 15-min check plus one immediate pass. Called from MainActivity.onCreate. */
fun scheduleLowStockChecks(context: Context) {
    AndroidNotificationPoster.ensureChannel(context)
    val manager = WorkManager.getInstance(context)
    manager.enqueueUniquePeriodicWork(
        "low_stock_periodic",
        ExistingPeriodicWorkPolicy.KEEP,
        PeriodicWorkRequestBuilder<LowStockWorker>(15, TimeUnit.MINUTES).build(),
    )
    manager.enqueueUniqueWork(
        "low_stock_now",
        ExistingWorkPolicy.REPLACE,
        OneTimeWorkRequestBuilder<LowStockWorker>().build(),
    )
}
