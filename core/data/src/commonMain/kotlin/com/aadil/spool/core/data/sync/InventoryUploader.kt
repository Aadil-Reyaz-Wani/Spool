package com.aadil.spool.core.data.sync

import com.aadil.spool.core.data.repository.InventoryScope
import com.aadil.spool.core.data.repository.InventorySession
import com.aadil.spool.core.network.InventoryUploadRemote
import com.aadil.spool.core.network.InventoryUploadReply
import com.aadil.spool.data.dao.InventorySyncDao
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface InventoryUploadResult {
    data object NoWork : InventoryUploadResult
    data object Applied : InventoryUploadResult
    data object Conflict : InventoryUploadResult
    data object StaleScope : InventoryUploadResult
    data object NotAuthenticated : InventoryUploadResult
    data object RetryLater : InventoryUploadResult
    data object Rejected : InventoryUploadResult
}

/** Explicit one-aggregate upload. Scheduling and conflict resolution are separate steps. */
class InventoryUploader(
    private val dao: InventorySyncDao,
    private val session: InventorySession,
    private val remote: InventoryUploadRemote,
) {
    private val mutex = Mutex()

    suspend fun uploadNext(expected: InventoryScope): InventoryUploadResult = mutex.withLock {
        if (expected.ownerId.isBlank() || session.scope.value != expected || expected.ownerId in session.deletingOwners.value) return@withLock InventoryUploadResult.StaleScope
        val batch = session.withScope(expected, null) { dao.prepare(expected.ownerId) }
            ?: return@withLock if (session.scope.value == expected) {
                if (dao.conflictCount(expected.ownerId) > 0) InventoryUploadResult.Conflict else InventoryUploadResult.NoWork
            } else InventoryUploadResult.StaleScope
        // Never hold the inventory lock during a network request: local saves remain available offline.
        val reply = coroutineScope {
            val sending = async { remote.upload(expected.ownerId, batch.request) }
            val switched = async { session.scope.first { it != expected } }
            try {
                select<InventoryUploadReply?> {
                    sending.onAwait { it }
                    switched.onAwait { null }
                }
            } finally {
                sending.cancel()
                switched.cancel()
            }
        } ?: return@withLock InventoryUploadResult.StaleScope
        session.withScope(expected, InventoryUploadResult.StaleScope) {
            when (reply) {
                is InventoryUploadReply.Applied -> {
                    dao.acknowledge(batch, reply.revision)
                    InventoryUploadResult.Applied
                }
                is InventoryUploadReply.Conflict -> {
                    dao.saveConflict(batch.ownerId, batch.spoolUuid, batch.batchId, reply.snapshot)
                    InventoryUploadResult.Conflict
                }
                InventoryUploadReply.NotAuthenticated -> InventoryUploadResult.NotAuthenticated
                InventoryUploadReply.RetryLater -> InventoryUploadResult.RetryLater
                InventoryUploadReply.Rejected -> InventoryUploadResult.Rejected
            }
        }
    }
}
