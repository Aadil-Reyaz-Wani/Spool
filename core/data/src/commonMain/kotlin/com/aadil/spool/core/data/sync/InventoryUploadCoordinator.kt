package com.aadil.spool.core.data.sync

import com.aadil.spool.core.data.repository.InventoryScope
import com.aadil.spool.core.data.repository.InventorySession
import com.aadil.spool.core.network.AccountRepository
import com.aadil.spool.data.dao.InventorySyncDao
import com.aadil.spool.data.entity.InventoryUploadQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

interface InventoryUploadScheduler {
    val networkAvailable: Flow<Unit>
    fun schedule(scope: InventoryScope)
    fun cancel(scope: InventoryScope)
}

enum class InventoryUploadMode { Checking, Idle, Uploading, Waiting, SignInRequired, NeedsAttention }

data class InventoryUploadStatus(
    val scope: InventoryScope? = null,
    val queue: InventoryUploadQueue = InventoryUploadQueue(),
    val mode: InventoryUploadMode = InventoryUploadMode.Checking,
)

/** Observes durable work at application lifetime. Foreground retries and OS jobs share one drain. */
class InventoryUploadCoordinator(
    private val dao: InventorySyncDao,
    private val session: InventorySession,
    private val uploader: InventoryUploader,
    private val accounts: AccountRepository,
    private val scheduler: InventoryUploadScheduler,
    private val downloader: InventoryDownloader? = null,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val mutableStatus = MutableStateFlow(InventoryUploadStatus())
    val status = mutableStatus.asStateFlow()
    private val foreground = MutableStateFlow(false)
    private val signals = Channel<Unit>(Channel.CONFLATED)
    private val drainMutex = Mutex()
    private val job = coroutineScope.launch(start = CoroutineStart.LAZY) {
        session.scope.combine(session.deletingOwners) { active, deleting ->
            active?.takeUnless { it.ownerId in deleting }
        }.collectLatest { active ->
            mutableStatus.value = InventoryUploadStatus(scope = active)
            if (active == null || active.ownerId.isBlank()) return@collectLatest
            try {
                schedule(active)
                coroutineScope {
                    launch {
                        dao.observeQueue(active.ownerId).distinctUntilChanged().collect { queue ->
                            if (session.scope.value != active) return@collect
                            mutableStatus.update {
                                if (it.scope == active) it.copy(queue = queue,
                                    mode = if (it.mode == InventoryUploadMode.Checking) InventoryUploadMode.Idle else it.mode) else it
                            }
                            if (queue.ready > 0) {
                                // OS work is queued before attempting in-process uploads.
                                schedule(active)
                                signals.trySend(Unit)
                            }
                        }
                    }
                    launch {
                        accounts.sessions.collect { account ->
                            if (account.account?.id == active.ownerId && !account.needsConnection && !account.initializing) {
                                signals.trySend(Unit)
                            }
                        }
                    }
                    launch { scheduler.networkAvailable.collect { signals.trySend(Unit) } }
                    foreground.collectLatest { visible ->
                        if (!visible) return@collectLatest
                        var backoff = 30_000L
                        while (true) {
                            signals.tryReceive()
                            when (runPending(active)) {
                                InventoryUploadResult.RetryLater, InventoryUploadResult.NotAuthenticated -> {
                                    withTimeoutOrNull(backoff) { signals.receive() }
                                    backoff = (backoff * 2).coerceAtMost(300_000)
                                }
                                InventoryUploadResult.StaleScope -> return@collectLatest
                                else -> { backoff = 30_000; withTimeoutOrNull(60_000) { signals.receive() } }
                            }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                setMode(active, InventoryUploadMode.NeedsAttention)
            } finally {
                // Also removes periodic work, which otherwise survives sign-out/process death.
                if (session.scope.value != active || active.ownerId in session.deletingOwners.value) runCatching { scheduler.cancel(active) }
            }
        }
    }

    fun start() { job.start() }

    fun setForeground(visible: Boolean) { foreground.value = visible }

    fun retry() {
        val active = session.scope.value ?: return
        if (active.ownerId.isBlank() || active.ownerId in session.deletingOwners.value) return
        schedule(active)
        signals.trySend(Unit)
    }

    suspend fun runPending(expected: InventoryScope): InventoryUploadResult = drainMutex.withLock {
        if (expected.ownerId.isBlank() || session.scope.value != expected || expected.ownerId in session.deletingOwners.value) return@withLock InventoryUploadResult.StaleScope
        setMode(expected, InventoryUploadMode.Uploading)
        try {
            val result = withTimeoutOrNull(90_000) {
                for (index in 0 until 20) {
                    when (val result = uploader.uploadNext(expected)) {
                        InventoryUploadResult.Applied -> Unit
                        InventoryUploadResult.Conflict -> if (dao.nextSpool(expected.ownerId) == null) break
                        InventoryUploadResult.NoWork -> break
                        else -> return@withTimeoutOrNull result
                    }
                }
                if (dao.nextSpool(expected.ownerId) != null) return@withTimeoutOrNull InventoryUploadResult.RetryLater
                if (downloader == null) return@withTimeoutOrNull InventoryUploadResult.NoWork
                repeat(20) {
                    when (val result = downloader.downloadNext(expected)) {
                        InventoryUploadResult.Applied -> Unit
                        else -> return@withTimeoutOrNull result
                    }
                }
                InventoryUploadResult.RetryLater
            } ?: InventoryUploadResult.RetryLater
            setMode(expected, when (result) {
                InventoryUploadResult.RetryLater -> InventoryUploadMode.Waiting
                InventoryUploadResult.NotAuthenticated -> InventoryUploadMode.SignInRequired
                InventoryUploadResult.Rejected -> InventoryUploadMode.NeedsAttention
                else -> InventoryUploadMode.Idle
            })
            result
        } catch (cancelled: CancellationException) {
            setMode(expected, InventoryUploadMode.Idle)
            throw cancelled
        } catch (_: Exception) {
            setMode(expected, InventoryUploadMode.Waiting)
            InventoryUploadResult.RetryLater
        }
    }

    private fun schedule(active: InventoryScope) {
        try { scheduler.schedule(active) } catch (_: Exception) {
            // Durable queue and foreground retries still work if OS scheduling is temporarily unavailable.
            setMode(active, InventoryUploadMode.NeedsAttention)
        }
    }

    private fun setMode(expected: InventoryScope, mode: InventoryUploadMode) {
        if (session.scope.value == expected && status.value.scope == expected) {
            mutableStatus.update { if (it.scope == expected) it.copy(mode = mode) else it }
        }
    }

    fun close() { coroutineScope.cancel() }
}
