package com.aadil.spool.core.data.sync

import com.aadil.spool.core.data.repository.InventoryScope
import com.aadil.spool.core.data.repository.InventorySession
import com.aadil.spool.core.model.*
import com.aadil.spool.data.dao.InventoryRecoveryDao
import com.aadil.spool.data.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.*
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class InventoryRecoveryItem(val id: String, val kind: String, val brand: String?, val savedAt: Long, val restored: Boolean)
data class InventoryRecoveryPreview(val source: InventoryRecoverySource, val version: RemoteInventorySnapshot)
enum class InventoryRecoveryResult { Restored, Changed, Unavailable, StaleScope }

interface InventoryRecoveryRepository {
    fun observe(scope: InventoryScope): Flow<List<InventoryRecoveryItem>>
    suspend fun preview(scope: InventoryScope, id: String): InventoryRecoveryPreview?
    suspend fun restore(scope: InventoryScope, preview: InventoryRecoveryPreview): InventoryRecoveryResult
}

class LocalInventoryRecoveryRepository(private val dao: InventoryRecoveryDao, private val session: InventorySession) : InventoryRecoveryRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override fun observe(scope: InventoryScope): Flow<List<InventoryRecoveryItem>> =
        combine(session.scope, dao.observeSources(scope.ownerId)) { active, sources ->
            if (scope.ownerId.isBlank() || active != scope) emptyList() else sources.map { row ->
                val brand = runCatching { json.parseToJsonElement(row.snapshot).jsonObject["spool"]?.jsonObject?.get("brand")?.jsonPrimitive?.content }.getOrNull()
                InventoryRecoveryItem(row.id, row.kind, brand, row.createdAt, row.restoredUuid != null)
            }
        }.flowOn(Dispatchers.Default)

    override suspend fun preview(scope: InventoryScope, id: String): InventoryRecoveryPreview? = try {
        withContext(Dispatchers.Default) {
            session.withScope(scope, null) {
                if (scope.ownerId.isBlank()) null else dao.source(scope.ownerId, id)?.let { InventoryRecoveryPreview(it, decode(it)) }
            }
        }
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { null }

    override suspend fun restore(scope: InventoryScope, preview: InventoryRecoveryPreview): InventoryRecoveryResult = try {
        if (scope.ownerId.isBlank() || preview.source.ownerId != scope.ownerId) InventoryRecoveryResult.StaleScope
        else withContext(Dispatchers.Default) { session.withScope(scope, InventoryRecoveryResult.StaleScope) {
            // Decode the retained source again; callers cannot substitute another preview's content.
            val snapshot = decode(preview.source)
            require(snapshot == preview.version)
            val s = snapshot.spool
            val spool = Filament(brand = s.brand, material = s.material, colorName = s.colorName, colorHex = s.colorHex,
                totalWeight = s.totalWeight, currentWeight = s.currentWeight, tempNozzle = s.tempNozzle, tempBed = s.tempBed,
                note = s.note, price = s.price, timestamp = s.timestamp, dryBaselineWeight = s.dryBaselineWeight,
                dryBaselineTareGrams = s.dryBaselineTareGrams, lastWeighedTareGrams = s.lastWeighedTareGrams,
                lastDriedAt = s.lastDriedAt, ownerId = scope.ownerId)
            val logs = snapshot.logs.map { UsageLog(spoolId = 0, title = it.title, gramsUsed = it.gramsUsed,
                timestamp = it.timestamp, isFailure = it.isFailure, pricePerPrint = it.pricePerPrint) }
            if (dao.restore(preview.source, spool, logs) != null) InventoryRecoveryResult.Restored else InventoryRecoveryResult.Changed
        } }
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { InventoryRecoveryResult.Unavailable }

    @OptIn(ExperimentalUuidApi::class)
    private fun decode(source: InventoryRecoverySource): RemoteInventorySnapshot {
        require(source.kind in setOf("deleted_device", "deleted_cloud", "conflict_local", "conflict_cloud"))
        val root = json.parseToJsonElement(source.snapshot).jsonObject
        val rawSpool = root.getValue("spool").jsonObject
        val deleted = source.kind.startsWith("deleted_") || root["deleted"]?.jsonPrimitive?.booleanOrNull == true ||
            rawSpool["deleted_at"]?.let { it != JsonNull } == true
        val spool = json.decodeFromJsonElement(RemoteInventorySpool.serializer(), buildJsonObject {
            rawSpool.forEach { (key, value) -> put(key, value) }
            if ("owner_id" !in rawSpool) put("owner_id", source.ownerId)
            if ("revision" !in rawSpool) put("revision", 1)
            if ("deleted_at" !in rawSpool) put("deleted_at", JsonNull)
        })
        require(spool.ownerId == source.ownerId && source.ownerId.isNotBlank())
        Uuid.parse(spool.uuid)
        val rawLogs = root.getValue("logs").jsonArray.toMutableList()
        if (deleted && source.kind == "conflict_local") {
            val pending = root.getValue("pending").jsonArray.map { it.jsonObject }
                .filter { it.getValue("entity_type").jsonPrimitive.content == "usage_log" }
                .associateBy { it.getValue("entity_uuid").jsonPrimitive.content }
            val existing = rawLogs.map { it.jsonObject.getValue("uuid").jsonPrimitive.content }.toSet()
            rawLogs += pending.filterKeys { it !in existing }.values.map { it.getValue("payload") }
        }
        val logs = rawLogs.map { raw ->
            val row = raw.jsonObject
            json.decodeFromJsonElement(RemoteInventoryLog.serializer(), buildJsonObject {
                row.forEach { (key, value) -> put(key, value) }
                if ("owner_id" !in row) put("owner_id", source.ownerId)
                if ("deleted_at" !in row) put("deleted_at", JsonNull)
                val flag = row.getValue("is_failure").jsonPrimitive.content
                require(flag in listOf("true", "false", "0", "1"))
                put("is_failure", flag == "true" || flag == "1")
            })
        }.filter { deleted || it.deletedAt == null }
        require(logs.map { it.uuid }.toSet().size == logs.size)
        fun weight(value: Double) { require(value.isFinite() && value >= 0) }
        listOf(spool.totalWeight, spool.currentWeight, spool.price).forEach(::weight)
        listOfNotNull(spool.dryBaselineWeight, spool.dryBaselineTareGrams, spool.lastWeighedTareGrams).forEach(::weight)
        logs.forEach {
            Uuid.parse(it.uuid)
            require(it.ownerId == source.ownerId && it.spoolUuid == spool.uuid)
            weight(it.gramsUsed); weight(it.pricePerPrint)
        }
        return RemoteInventorySnapshot(spool, logs)
    }
}
