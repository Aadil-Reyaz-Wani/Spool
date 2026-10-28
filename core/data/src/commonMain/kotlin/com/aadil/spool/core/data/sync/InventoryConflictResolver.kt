package com.aadil.spool.core.data.sync

import com.aadil.spool.core.data.repository.InventoryScope
import com.aadil.spool.core.data.repository.InventorySession
import com.aadil.spool.core.model.RemoteInventorySnapshot
import com.aadil.spool.core.model.RemoteInventorySpool
import com.aadil.spool.core.model.RemoteInventoryLog
import com.aadil.spool.core.model.currentTimeMillis
import com.aadil.spool.core.network.InventoryConflictRemote
import com.aadil.spool.core.network.InventorySnapshotReply
import com.aadil.spool.data.dao.InventorySyncDao
import com.aadil.spool.data.entity.InventoryConflictRecord
import com.aadil.spool.data.entity.InventoryConflictResolution
import com.aadil.spool.data.entity.InventoryUploadBatch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select
import kotlinx.serialization.json.*
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

enum class InventoryConflictChoice { Local, Cloud }

data class InventoryConflictReview(
    val scope: InventoryScope,
    val record: InventoryConflictRecord,
    val local: RemoteInventorySnapshot,
    val cloud: RemoteInventorySnapshot,
)

sealed interface InventoryConflictReviewResult {
    data class Ready(val review: InventoryConflictReview) : InventoryConflictReviewResult
    data object Changed : InventoryConflictReviewResult
    data object Unavailable : InventoryConflictReviewResult
    data object StaleScope : InventoryConflictReviewResult
}
enum class InventoryConflictResolveResult { Resolved, Changed, Unavailable, StaleScope }

interface InventoryConflictRepository {
    fun observeConflicts(scope: InventoryScope): Flow<List<InventoryUploadBatch>>
    suspend fun review(scope: InventoryScope, spoolUuid: String): InventoryConflictReviewResult
    suspend fun resolve(review: InventoryConflictReview, choice: InventoryConflictChoice): InventoryConflictResolveResult
}

@OptIn(ExperimentalUuidApi::class)
class InventoryConflictResolver(
    private val dao: InventorySyncDao,
    private val session: InventorySession,
    private val remote: InventoryConflictRemote,
) : InventoryConflictRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override fun observeConflicts(scope: InventoryScope): Flow<List<InventoryUploadBatch>> =
        session.scope.combine(dao.observeConflicts(scope.ownerId)) { active, rows ->
            if (active == scope && scope.ownerId.isNotBlank()) rows else emptyList()
        }

    override suspend fun review(scope: InventoryScope, spoolUuid: String): InventoryConflictReviewResult {
        if (!active(scope)) return InventoryConflictReviewResult.StaleScope
        return try {
            val record = session.withScope(scope, null) { dao.conflictRecord(scope.ownerId, spoolUuid) }
                ?: return if (active(scope)) InventoryConflictReviewResult.Changed else InventoryConflictReviewResult.StaleScope
            val response = fetch(scope, spoolUuid) ?: return InventoryConflictReviewResult.StaleScope
            val cloud = (response as? InventorySnapshotReply.Snapshot)?.value ?: return InventoryConflictReviewResult.Unavailable
            validate(cloud, scope, record)
            val unchanged = session.withScope(scope, false) { dao.conflictRecord(scope.ownerId, spoolUuid) == record }
            if (!active(scope)) InventoryConflictReviewResult.StaleScope
            else if (!unchanged) InventoryConflictReviewResult.Changed
            else InventoryConflictReviewResult.Ready(InventoryConflictReview(scope, record, localVersion(record), cloud))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { InventoryConflictReviewResult.Unavailable }
    }

    override suspend fun resolve(review: InventoryConflictReview, choice: InventoryConflictChoice): InventoryConflictResolveResult {
        val scope = review.scope
        if (!active(scope)) return InventoryConflictResolveResult.StaleScope
        return try {
            // Recheck the reviewed cloud version without locking offline local writes.
            val response = fetch(scope, review.record.batch.spoolUuid) ?: return InventoryConflictResolveResult.StaleScope
            val cloud = (response as? InventorySnapshotReply.Snapshot)?.value ?: return InventoryConflictResolveResult.Unavailable
            validate(cloud, scope, review.record)
            if (cloud != review.cloud) return InventoryConflictResolveResult.Changed
            val saved = InventoryConflictResolution(Uuid.random().toString(), scope.ownerId, review.record.batch.spoolUuid,
                if (choice == InventoryConflictChoice.Local) "local" else "cloud", archive(review.record),
                json.encodeToString(RemoteInventorySnapshot.serializer(), cloud), currentTimeMillis())
            session.withScope(scope, InventoryConflictResolveResult.StaleScope) {
                if (dao.resolveConflict(review.record, cloud, saved, cloud.logs.associate { it.uuid to logPayload(it) })) {
                    InventoryConflictResolveResult.Resolved
                } else InventoryConflictResolveResult.Changed
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { InventoryConflictResolveResult.Unavailable }
    }

    private fun active(scope: InventoryScope) = scope.ownerId.isNotBlank() && session.scope.value == scope

    private suspend fun fetch(scope: InventoryScope, uuid: String): InventorySnapshotReply? = coroutineScope {
        val receiving = async { remote.snapshot(scope.ownerId, uuid) }
        val switched = async { session.scope.first { it != scope } }
        try { select { receiving.onAwait { it }; switched.onAwait { null } } }
        finally { receiving.cancel(); switched.cancel() }
    }

    private fun validate(cloud: RemoteInventorySnapshot, scope: InventoryScope, record: InventoryConflictRecord) {
        require(cloud.spool.ownerId == scope.ownerId && cloud.spool.uuid == record.batch.spoolUuid)
        require(cloud.spool.revision > 0 && cloud.spool.revision >= record.batch.baseRevision)
        require(cloud.logs.all { it.ownerId == scope.ownerId && it.spoolUuid == record.batch.spoolUuid })
        require(cloud.logs.map { it.uuid }.toSet().size == cloud.logs.size)
    }

    private fun archive(record: InventoryConflictRecord): String = buildJsonObject {
        put("spool", json.parseToJsonElement(record.spoolPayload))
        put("deleted", record.spool == null)
        put("logs", json.parseToJsonElement(record.historyPayloads))
        put("pending", json.parseToJsonElement(record.pendingSnapshots))
        put("batch", buildJsonObject {
            put("id", record.batch.batchId); put("base_revision", record.batch.baseRevision)
            put("request", record.batch.request); put("operation_ids", record.batch.operationIds)
            put("conflict_snapshot", record.batch.conflictSnapshot)
        })
    }.toString()

    private fun localVersion(record: InventoryConflictRecord): RemoteInventorySnapshot {
        val deleted = record.spool == null
        val spool = json.decodeFromJsonElement(RemoteInventorySpool.serializer(), buildJsonObject {
            json.parseToJsonElement(record.spoolPayload).jsonObject.forEach { (key, value) -> put(key, value) }
            put("owner_id", record.batch.ownerId); put("revision", record.batch.baseRevision)
            put("deleted_at", if (deleted) JsonPrimitive("local") else JsonNull)
        })
        val live = json.parseToJsonElement(record.historyPayloads).jsonArray
        val pending = json.parseToJsonElement(record.pendingSnapshots).jsonArray.map { it.jsonObject }
            .filter { it.getValue("entity_type").jsonPrimitive.content == "usage_log" }
            .associateBy { it.getValue("entity_uuid").jsonPrimitive.content }
        val liveIds = live.map { it.jsonObject.getValue("uuid").jsonPrimitive.content }.toSet()
        val removed = pending.values.filter { it.getValue("entity_uuid").jsonPrimitive.content !in liveIds &&
            (deleted || it.getValue("operation").jsonPrimitive.content == "delete") }.map { it.getValue("payload") }
        val logs = (live + removed).map { value ->
            json.decodeFromJsonElement(RemoteInventoryLog.serializer(), buildJsonObject {
                value.jsonObject.forEach { (key, item) -> put(key, item) }
                val id = value.jsonObject.getValue("uuid").jsonPrimitive.content
                put("owner_id", record.batch.ownerId); put("spool_uuid", record.batch.spoolUuid)
                put("deleted_at", if (deleted || id !in liveIds) JsonPrimitive("local") else JsonNull)
                put("is_failure", value.jsonObject.getValue("is_failure").jsonPrimitive.content in listOf("1", "true"))
            })
        }
        return RemoteInventorySnapshot(spool, logs)
    }

    private fun logPayload(log: RemoteInventoryLog): String = buildJsonObject {
        put("version", 1); put("uuid", log.uuid); put("spool_uuid", log.spoolUuid)
        put("title", log.title); put("grams_used", log.gramsUsed); put("timestamp", log.timestamp)
        put("is_failure", log.isFailure); put("price_per_print", log.pricePerPrint)
    }.toString()
}
