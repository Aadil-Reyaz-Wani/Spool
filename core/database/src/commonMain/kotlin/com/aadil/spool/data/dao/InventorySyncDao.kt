package com.aadil.spool.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.aadil.spool.core.model.InventoryPullPage
import com.aadil.spool.data.entity.Filament
import com.aadil.spool.data.entity.UsageLog
import com.aadil.spool.data.entity.InventoryPullState
import com.aadil.spool.data.entity.InventoryRemoteApply
import kotlinx.coroutines.flow.Flow
import com.aadil.spool.data.entity.InventoryUploadQueue
import com.aadil.spool.core.model.currentTimeMillis
import com.aadil.spool.data.entity.InventorySyncState
import com.aadil.spool.data.entity.InventoryUploadBatch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Dao
@OptIn(ExperimentalUuidApi::class)
interface InventorySyncDao {
    @Query("""SELECT COUNT(*) AS pending, COALESCE(MAX(id), 0) AS latest,
        (SELECT COUNT(*) FROM inventory_upload_batches b WHERE b.owner_id = :ownerId AND b.conflict_snapshot IS NOT NULL) AS conflicts,
        (SELECT COUNT(*) FROM pending_inventory_changes p WHERE p.owner_id = :ownerId AND p.acknowledged_at IS NULL
            AND NOT EXISTS (SELECT 1 FROM inventory_upload_batches b WHERE b.owner_id = p.owner_id
                AND b.spool_uuid = p.spool_uuid AND b.conflict_snapshot IS NOT NULL)) AS ready
        FROM pending_inventory_changes WHERE owner_id = :ownerId AND acknowledged_at IS NULL""")
    fun observeQueue(ownerId: String): Flow<InventoryUploadQueue>


    @Query("""SELECT spool_uuid FROM pending_inventory_changes p WHERE owner_id = :ownerId AND acknowledged_at IS NULL
        AND NOT EXISTS (SELECT 1 FROM inventory_upload_batches b WHERE b.owner_id = p.owner_id AND b.spool_uuid = p.spool_uuid AND b.conflict_snapshot IS NOT NULL)
        ORDER BY id LIMIT 1""")
    suspend fun nextSpool(ownerId: String): String?

    @Query("SELECT * FROM inventory_upload_batches WHERE owner_id = :ownerId AND spool_uuid = :spoolUuid")
    suspend fun batch(ownerId: String, spoolUuid: String): InventoryUploadBatch?

    @Query("SELECT server_revision FROM inventory_sync_state WHERE owner_id = :ownerId AND spool_uuid = :spoolUuid")
    suspend fun serverRevision(ownerId: String, spoolUuid: String): Long?

    @Query("SELECT payload FROM pending_inventory_changes WHERE owner_id = :ownerId AND spool_uuid = :spoolUuid AND entity_type = 'spool' AND acknowledged_at IS NULL ORDER BY id DESC LIMIT 1")
    suspend fun queuedSpoolSnapshot(ownerId: String, spoolUuid: String): String?

    @Query("""SELECT json_object('version', 1, 'uuid', uuid, 'brand', brand, 'material', material,
        'color_name', color_name, 'color_hex', color_hex, 'total_weight', total_weight, 'current_weight', current_weight,
        'temp_nozzle', temp_nozzle, 'temp_bed', temp_bed, 'note', note, 'price', price, 'timestamp', timestamp,
        'dry_baseline_weight', dry_baseline_weight, 'dry_baseline_tare_grams', dry_baseline_tare_grams,
        'last_weighed_tare_grams', last_weighed_tare_grams, 'last_dried_at', last_dried_at)
        FROM filaments WHERE owner_id = :ownerId AND uuid = :spoolUuid""")
    suspend fun currentSpoolSnapshot(ownerId: String, spoolUuid: String): String?

    @Query("""SELECT EXISTS (SELECT 1 FROM pending_inventory_changes WHERE owner_id = :ownerId AND spool_uuid = :spoolUuid
        AND entity_type = 'spool' AND operation = 'delete' AND acknowledged_at IS NULL)""")
    suspend fun spoolDeleted(ownerId: String, spoolUuid: String): Boolean

    @Query("""SELECT json_group_array(json_object('uuid', entity_uuid, 'operation', operation, 'payload', json(payload)))
        FROM pending_inventory_changes WHERE id IN (SELECT MAX(id) FROM pending_inventory_changes
        WHERE owner_id = :ownerId AND spool_uuid = :spoolUuid AND entity_type = 'usage_log' AND acknowledged_at IS NULL GROUP BY entity_uuid)""")
    suspend fun logSnapshots(ownerId: String, spoolUuid: String): String

    @Query("SELECT json_group_array(operation_id) FROM pending_inventory_changes WHERE owner_id = :ownerId AND spool_uuid = :spoolUuid AND acknowledged_at IS NULL")
    suspend fun operationIds(ownerId: String, spoolUuid: String): String

    @Query("""SELECT json_object('p_expected_owner', :ownerId, 'p_batch_id', :batchId, 'p_spool_uuid', :spoolUuid,
        'p_base_revision', :revision, 'p_spool', json(:spool), 'p_deleted', json(CASE WHEN :deleted THEN 'true' ELSE 'false' END), 'p_logs', json(:logs))""")
    suspend fun request(ownerId: String, batchId: String, spoolUuid: String, revision: Long, spool: String, deleted: Boolean, logs: String): String

    @Insert
    suspend fun insertBatch(batch: InventoryUploadBatch)

    @Transaction
    suspend fun prepare(ownerId: String): InventoryUploadBatch? {
        require(ownerId.isNotBlank())
        val spoolUuid = nextSpool(ownerId) ?: return null
        return prepareSpool(ownerId, spoolUuid)
    }

    @Transaction
    suspend fun prepareSpool(ownerId: String, spoolUuid: String): InventoryUploadBatch {
        require(ownerId.isNotBlank())
        batch(ownerId, spoolUuid)?.let { return it }
        val spool = checkNotNull(queuedSpoolSnapshot(ownerId, spoolUuid) ?: currentSpoolSnapshot(ownerId, spoolUuid))
        val revision = serverRevision(ownerId, spoolUuid) ?: 0L
        val batchId = Uuid.random().toString()
        val prepared = InventoryUploadBatch(ownerId, spoolUuid, batchId, revision,
            request(ownerId, batchId, spoolUuid, revision, spool, spoolDeleted(ownerId, spoolUuid), logSnapshots(ownerId, spoolUuid)),
            operationIds(ownerId, spoolUuid))
        insertBatch(prepared)
        return prepared
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveState(state: InventorySyncState)

    @Query("""UPDATE pending_inventory_changes SET acknowledged_at = :at WHERE owner_id = :ownerId
        AND operation_id IN (SELECT value FROM json_each(:ids))""")
    suspend fun markAcknowledged(ownerId: String, ids: String, at: Long)

    @Query("DELETE FROM pending_inventory_changes WHERE owner_id = :ownerId AND operation != 'delete' AND acknowledged_at IS NOT NULL")
    suspend fun removeAcknowledgedUpserts(ownerId: String)

    @Query("DELETE FROM inventory_upload_batches WHERE owner_id = :ownerId AND spool_uuid = :spoolUuid AND batch_id = :batchId")
    suspend fun removeBatch(ownerId: String, spoolUuid: String, batchId: String)

    @Transaction
    suspend fun acknowledge(sent: InventoryUploadBatch, revision: Long) {
        require(revision > sent.baseRevision)
        check(batch(sent.ownerId, sent.spoolUuid) == sent)
        saveState(InventorySyncState(sent.ownerId, sent.spoolUuid, revision))
        markAcknowledged(sent.ownerId, sent.operationIds, currentTimeMillis())
        removeAcknowledgedUpserts(sent.ownerId)
        removeBatch(sent.ownerId, sent.spoolUuid, sent.batchId)
    }

    @Query("UPDATE inventory_upload_batches SET conflict_snapshot = :snapshot WHERE owner_id = :ownerId AND spool_uuid = :spoolUuid AND batch_id = :batchId")
    suspend fun saveConflict(ownerId: String, spoolUuid: String, batchId: String, snapshot: String)

    @Query("SELECT COUNT(*) FROM inventory_upload_batches WHERE owner_id = :ownerId AND conflict_snapshot IS NOT NULL")
    suspend fun conflictCount(ownerId: String): Int

    @Query("SELECT cursor FROM inventory_pull_state WHERE owner_id = :ownerId")
    suspend fun pullCursor(ownerId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePullState(state: InventoryPullState)

    @Insert
    suspend fun beginRemoteApply(guard: InventoryRemoteApply)

    @Query("DELETE FROM inventory_remote_apply WHERE owner_id = :ownerId")
    suspend fun endRemoteApply(ownerId: String)

    @Query("SELECT * FROM filaments WHERE uuid = :uuid")
    suspend fun spoolByUuid(uuid: String): Filament?

    @Query("SELECT * FROM usage_log WHERE uuid = :uuid")
    suspend fun logByUuid(uuid: String): UsageLog?

    @Query("SELECT * FROM usage_log WHERE spoolId = :id")
    suspend fun logsForSpool(id: Int): List<UsageLog>

    @Query("SELECT EXISTS (SELECT 1 FROM pending_inventory_changes WHERE owner_id = :ownerId AND spool_uuid = :uuid AND acknowledged_at IS NULL)")
    suspend fun hasPending(ownerId: String, uuid: String): Boolean

    @Insert
    suspend fun insertRemoteSpool(spool: Filament): Long

    @Update
    suspend fun updateRemoteSpool(spool: Filament)

    @Insert
    suspend fun insertRemoteLog(log: UsageLog)

    @Update
    suspend fun updateRemoteLog(log: UsageLog)

    @Query("DELETE FROM filaments WHERE owner_id = :ownerId AND uuid = :uuid")
    suspend fun deleteRemoteSpool(ownerId: String, uuid: String)

    @Query("DELETE FROM usage_log WHERE spoolId = :spoolId AND uuid = :uuid")
    suspend fun deleteRemoteLog(spoolId: Int, uuid: String)

    /** Cursor, conflict preservation and inventory commit together. Never acknowledge local work here. */
    @Transaction
    suspend fun applyPull(page: InventoryPullPage, conflictSnapshot: String) {
        val owner = page.ownerId
        require(owner.isNotBlank() && page.cursor == (pullCursor(owner) ?: 0L))
        require(page.nextCursor >= page.cursor)
        val snapshot = page.snapshot
        if (snapshot != null) {
            val remote = snapshot.spool
            require(remote.ownerId == owner && remote.revision > 0 && page.nextCursor > page.cursor)
            require(snapshot.logs.all { it.ownerId == owner && it.spoolUuid == remote.uuid })
            val base = serverRevision(owner, remote.uuid) ?: 0L
            if (remote.revision > base) {
                if (hasPending(owner, remote.uuid) || batch(owner, remote.uuid) != null) {
                    val frozen = prepareSpool(owner, remote.uuid)
                    saveConflict(owner, remote.uuid, frozen.batchId, conflictSnapshot)
                } else {
                    val existing = spoolByUuid(remote.uuid)
                    // Local UUID indexes are global. A collision must never transfer another account's data.
                    require(existing == null || existing.ownerId == owner)
                    beginRemoteApply(InventoryRemoteApply(owner))
                    try {
                        if (remote.deletedAt != null) {
                            deleteRemoteSpool(owner, remote.uuid)
                        } else {
                            val row = Filament(id = existing?.id ?: 0, brand = remote.brand, material = remote.material,
                                colorName = remote.colorName, colorHex = remote.colorHex, totalWeight = remote.totalWeight,
                                currentWeight = remote.currentWeight, tempNozzle = remote.tempNozzle, tempBed = remote.tempBed,
                                note = remote.note, price = remote.price, timestamp = remote.timestamp,
                                dryBaselineWeight = remote.dryBaselineWeight, dryBaselineTareGrams = remote.dryBaselineTareGrams,
                                lastWeighedTareGrams = remote.lastWeighedTareGrams, lastDriedAt = remote.lastDriedAt,
                                uuid = remote.uuid, ownerId = owner)
                            val id = if (existing == null) insertRemoteSpool(row).toInt() else {
                                updateRemoteSpool(row)
                                existing.id
                            }
                            val live = snapshot.logs.filter { it.deletedAt == null }
                            val liveIds = live.map { it.uuid }.toSet()
                            logsForSpool(id).filter { it.uuid !in liveIds }.forEach { deleteRemoteLog(id, it.uuid) }
                            for (log in live) {
                                val previous = logByUuid(log.uuid)
                                require(previous == null || previous.spoolId == id)
                                val local = UsageLog(previous?.id ?: 0, id, log.title, log.gramsUsed, log.timestamp,
                                    log.isFailure, log.pricePerPrint, log.uuid)
                                if (previous == null) insertRemoteLog(local) else updateRemoteLog(local)
                            }
                        }
                        saveState(InventorySyncState(owner, remote.uuid, remote.revision))
                    } finally { endRemoteApply(owner) }
                }
            }
        } else require(page.nextCursor == page.cursor && !page.hasMore)
        savePullState(InventoryPullState(owner, page.nextCursor))
    }
}
