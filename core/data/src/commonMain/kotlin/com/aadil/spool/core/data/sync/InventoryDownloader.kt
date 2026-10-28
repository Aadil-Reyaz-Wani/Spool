package com.aadil.spool.core.data.sync

import com.aadil.spool.core.data.repository.InventoryScope
import com.aadil.spool.core.data.repository.InventorySession
import com.aadil.spool.core.model.RemoteInventorySnapshot
import com.aadil.spool.core.network.InventoryPullRemote
import com.aadil.spool.core.network.InventoryPullReply
import com.aadil.spool.data.dao.InventorySyncDao
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One cursor page, applied only while the captured account scope is still active. */
class InventoryDownloader(
    private val dao: InventorySyncDao,
    private val session: InventorySession,
    private val remote: InventoryPullRemote,
) {
    private val mutex = Mutex()

    suspend fun downloadNext(expected: InventoryScope): InventoryUploadResult = mutex.withLock {
        if (expected.ownerId.isBlank() || session.scope.value != expected) return@withLock InventoryUploadResult.StaleScope
        val cursor = dao.pullCursor(expected.ownerId) ?: 0L
        val reply = coroutineScope {
            val receiving = async { remote.pull(expected.ownerId, cursor) }
            val switched = async { session.scope.first { it != expected } }
            try {
                select<InventoryPullReply?> {
                    receiving.onAwait { it }
                    switched.onAwait { null }
                }
            } finally { receiving.cancel(); switched.cancel() }
        } ?: return@withLock InventoryUploadResult.StaleScope
        session.withScope(expected, InventoryUploadResult.StaleScope) {
            when (reply) {
                is InventoryPullReply.Page -> {
                    val page = reply.value
                    require(page.ownerId == expected.ownerId && page.cursor == cursor)
                    val conflict = buildJsonObject {
                        put("status", "conflict")
                        page.snapshot?.let {
                            put("spool_uuid", it.spool.uuid)
                            put("revision", it.spool.revision)
                            put("server", Json.encodeToJsonElement(RemoteInventorySnapshot.serializer(), it))
                        }
                    }.toString()
                    dao.applyPull(page, conflict, page.snapshot?.takeIf { it.spool.deletedAt != null }?.let {
                        Json.encodeToString(RemoteInventorySnapshot.serializer(), it)
                    })
                    if (page.hasMore) InventoryUploadResult.Applied else InventoryUploadResult.NoWork
                }
                InventoryPullReply.NotAuthenticated -> InventoryUploadResult.NotAuthenticated
                InventoryPullReply.RetryLater -> InventoryUploadResult.RetryLater
                InventoryPullReply.Rejected -> InventoryUploadResult.Rejected
            }
        }
    }
}
