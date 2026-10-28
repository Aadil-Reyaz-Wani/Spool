package com.aadil.spool.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.aadil.spool.core.model.currentTimeMillis
import com.aadil.spool.data.entity.*
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val VISIBLE = "NOT EXISTS (SELECT 1 FROM inventory_recovery_removals m WHERE m.owner_id=s.owner_id AND m.source_id=s.id)"

private const val SOURCES = """SELECT s.*, r.spool_uuid AS restored_uuid FROM (
    SELECT owner_id,id,kind,snapshot,created_at FROM inventory_recovery_versions
    UNION ALL SELECT owner_id,'conflict:'||id||':local','conflict_local',local_snapshot,created_at FROM inventory_conflict_resolutions
    UNION ALL SELECT owner_id,'conflict:'||id||':cloud','conflict_cloud',cloud_snapshot,created_at FROM inventory_conflict_resolutions
    ) s LEFT JOIN inventory_recovery_restores r ON r.owner_id=s.owner_id AND r.source_id=s.id"""

@Dao
@OptIn(ExperimentalUuidApi::class)
interface InventoryRecoveryDao {
    @Query("$SOURCES WHERE s.owner_id=:ownerId AND $VISIBLE ORDER BY s.created_at DESC,s.id")
    fun observeSources(ownerId: String): Flow<List<InventoryRecoverySource>>

    @Query("$SOURCES WHERE s.owner_id=:ownerId AND s.id=:id AND $VISIBLE")
    suspend fun source(ownerId: String, id: String): InventoryRecoverySource?

    @Query("SELECT EXISTS (SELECT 1 FROM inventory_recovery_removals WHERE owner_id=:ownerId AND source_id=:id)")
    suspend fun isRemoved(ownerId: String, id: String): Boolean

    @Insert
    suspend fun insertRemoval(removal: InventoryRecoveryRemoval)

    @Query("DELETE FROM inventory_recovery_versions WHERE owner_id=:ownerId AND id=:id")
    suspend fun deleteVersion(ownerId: String, id: String)

    @Query("UPDATE inventory_conflict_resolutions SET local_snapshot='{}' WHERE owner_id=:ownerId AND 'conflict:'||id||':local'=:sourceId")
    suspend fun clearLocalVersion(ownerId: String, sourceId: String)

    @Query("UPDATE inventory_conflict_resolutions SET cloud_snapshot='{}' WHERE owner_id=:ownerId AND 'conflict:'||id||':cloud'=:sourceId")
    suspend fun clearCloudVersion(ownerId: String, sourceId: String)

    @Transaction
    suspend fun remove(ownerId: String, id: String): Boolean {
        require(ownerId.isNotBlank())
        if (isRemoved(ownerId, id)) return true
        val current = source(ownerId, id) ?: return false
        insertRemoval(InventoryRecoveryRemoval(ownerId, id))
        when (current.kind) {
            "conflict_local" -> clearLocalVersion(ownerId, id)
            "conflict_cloud" -> clearCloudVersion(ownerId, id)
            else -> deleteVersion(ownerId, id)
        }
        // Keep restore receipts and sync metadata: removing a copy never replays a restore or syncs a deletion.
        return true
    }

    @Insert
    suspend fun insertReceipt(receipt: InventoryRecoveryRestore)

    @Insert
    suspend fun insertSpool(spool: Filament): Long

    @Insert
    suspend fun insertLog(log: UsageLog)

    @Transaction
    suspend fun restore(expected: InventoryRecoverySource, spool: Filament, logs: List<UsageLog>): String? {
        require(expected.ownerId.isNotBlank() && spool.ownerId == expected.ownerId)
        val current = source(expected.ownerId, expected.id) ?: return null
        current.restoredUuid?.let { return it }
        if (current != expected) return null
        val copy = spool.copy(id = 0, uuid = Uuid.random().toString())
        insertReceipt(InventoryRecoveryRestore(expected.ownerId, expected.id, copy.uuid, currentTimeMillis()))
        val id = insertSpool(copy).toInt()
        logs.forEach { insertLog(it.copy(id = 0, spoolId = id, uuid = Uuid.random().toString())) }
        // Normal outbox triggers queue the complete new aggregate, even while offline.
        return copy.uuid
    }
}
