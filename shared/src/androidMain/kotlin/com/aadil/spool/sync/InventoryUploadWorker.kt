package com.aadil.spool.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.aadil.spool.core.data.repository.InventoryScope
import com.aadil.spool.core.data.repository.InventorySession
import com.aadil.spool.core.data.sync.InventoryUploadCoordinator
import com.aadil.spool.core.data.sync.InventoryUploadResult
import com.aadil.spool.core.data.sync.InventoryUploadScheduler
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

actual val platformInventoryUploadModule = module {
    single<InventoryUploadScheduler> { AndroidInventoryUploadScheduler(get()) }
}

private const val OWNER = "inventory_owner"
private const val REVISION = "inventory_scope_revision"

internal fun uploadWorkName(scope: InventoryScope, periodic: Boolean): String =
    "inventory_upload_" + (if (periodic) "periodic_" else "now_") + scope.ownerId + "_" + scope.revision

class AndroidInventoryUploadScheduler(private val context: Context) : InventoryUploadScheduler {
    override val networkAvailable = callbackFlow {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) trySend(Unit)
            }
        }
        try { manager.registerDefaultNetworkCallback(callback) } catch (_: Exception) { close(); return@callbackFlow }
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }

    override fun schedule(scope: InventoryScope) {
        if (scope.ownerId.isBlank()) return
        val data = workDataOf(OWNER to scope.ownerId, REVISION to scope.revision)
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val manager = WorkManager.getInstance(context)
        manager.enqueueUniqueWork(uploadWorkName(scope, false), ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<InventoryUploadWorker>()
                .setInputData(data).setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
        // KEEP can miss a last-moment enqueue while a worker is finishing; periodic work closes that gap.
        manager.enqueueUniquePeriodicWork(uploadWorkName(scope, true), ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<InventoryUploadWorker>(15, TimeUnit.MINUTES)
                .setInputData(data).setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
    }

    override fun cancel(scope: InventoryScope) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(uploadWorkName(scope, false))
            cancelUniqueWork(uploadWorkName(scope, true))
        }
    }
}

class InventoryUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val owner = inputData.getString(OWNER)?.takeIf { it.isNotBlank() } ?: return Result.success()
        val expected = InventoryScope(owner, inputData.getLong(REVISION, -1))
        val koin = GlobalContext.get()
        val active = withTimeoutOrNull(10_000) { koin.get<InventorySession>().awaitReady() } ?: return Result.retry()
        if (active != expected) {
            koin.get<InventoryUploadScheduler>().cancel(expected)
            return Result.success()
        }
        return when (koin.get<InventoryUploadCoordinator>().runPending(expected)) {
            InventoryUploadResult.RetryLater, InventoryUploadResult.NotAuthenticated -> Result.retry()
            else -> Result.success()
        }
    }
}
