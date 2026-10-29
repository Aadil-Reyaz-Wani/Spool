package com.aadil.spool.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.aadil.spool.core.model.RemoteInventorySnapshot
import com.aadil.spool.data.entity.InventoryConflictRecord
import com.aadil.spool.data.entity.InventoryConflictResolution
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
    /** Only call after confirmed cloud account deletion. Guest inventory is never eligible. */
    @Transaction
    suspend fun purgeDeletedAccount(ownerId: String) {
        require(ownerId.isNotBlank())
        beginRemoteApply(InventoryRemoteApply(ownerId))
        try {
            deleteAccountSpools(ownerId)
            deleteAccountChanges(ownerId)
            deleteAccountSyncStates(ownerId)
            deleteAccountBatches(ownerId)
            deleteAccountPullState(ownerId)
            deleteAccountConflictResolutions(ownerId)
            deleteAccountRecoveryVersions(ownerId)
            deleteAccountRecoveryRestores(ownerId)
            deleteAccountRecoveryRemovals(ownerId)
        } finally { endRemoteApply(ownerId) }
    }

    @Query("DELETE FROM filaments WHERE owner_id = :ownerId")
    suspend fun deleteAccountSpools(ownerId: String)

    @Query("DELETE FROM pending_inventory_changes WHERE owner_id = :ownerId")
    suspend fun deleteAccountChanges(ownerId: String)

    @Query("DELETE FROM inventory_sync_state WHERE owner_id = :ownerId")
    suspend fun deleteAccountSyncStates(ownerId: String)

    @Query("DELETE FROM inventory_upload_batches WHERE owner_id = :ownerId")
    suspend fun deleteAccountBatches(ownerId: String)

    @Query("DELETE FROM inventory_pull_state WHERE owner_id = :ownerId")
    suspend fun deleteAccountPullState(ownerId: String)

    @Query("DELETE FROM inventory_conflict_resolutions WHERE owner_id = :ownerId")
    suspend fun deleteAccountConflictResolutions(ownerId: String)

    @Query("DELETE FROM inventory_recovery_versions WHERE owner_id = :ownerId")
    suspend fun deleteAccountRecoveryVersions(ownerId: String)

    @Query("DELETE FROM inventory_recovery_restores WHERE owner_id = :ownerId")
    suspend fun deleteAccountRecoveryRestores(ownerId: String)

    @Query("DELETE FROM inventory_recovery_removals WHERE owner_id = :ownerId")
    suspend fun deleteAccountRecoveryRemovals(ownerId: String)

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

    @Query("SELECT * FROM usage_log WHERE spoolId = :id ORDER BY id")
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
    suspend fun applyPull(page: InventoryPullPage, conflictSnapshot: String, deletedSnapshot: String? = null) {
        val owner = page.ownerId
        require(owner.isNotBlank() && page.cursor == (pullCursor(owner) ?: 0L))
        require(page.nextCursor >= page.cursor)
        val snapshot = page.snapshot
        if (snapshot != null) {
            val remote = snapshot.spool
            require(remote.ownerId == owner && remote.revision > 0 && page.nextCursor > page.cursor)
            require(snapshot.logs.all { it.ownerId == owner && it.spoolUuid == remote.uuid })
            if (remote.deletedAt != null && deletedSnapshot != null) saveDeletedCloud(owner, remote.uuid, deletedSnapshot, currentTimeMillis())
            val base = serverRevision(owner, remote.uuid) ?: 0L
            if (remote.revision > base) {
                if (hasPending(owner, remote.uuid) || batch(owner, remote.uuid) != null) {
                    val frozen = prepareSpool(owner, remote.uuid)
                    saveConflict(owner, remote.uuid, frozen.batchId, conflictSnapshot)
                } else {
                    applyRemoteSnapshot(owner, snapshot)
                }
            }
        } else require(page.nextCursor == page.cursor && !page.hasMore)
        savePullState(InventoryPullState(owner, page.nextCursor))
    }

    @Query("""INSERT OR IGNORE INTO inventory_recovery_versions
        VALUES (:owner, 'deleted:cloud:'||:uuid, 'deleted_cloud', :snapshot, :at)""")
    suspend fun saveDeletedCloud(owner: String, uuid: String, snapshot: String, at: Long)

    @Transaction
    suspend fun applyRemoteSnapshot(owner: String, snapshot: RemoteInventorySnapshot) {
        val remote = snapshot.spool
        require(owner.isNotBlank() && remote.ownerId == owner && remote.revision > 0)
        require(snapshot.logs.all { it.ownerId == owner && it.spoolUuid == remote.uuid })
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

    @Query("SELECT * FROM inventory_upload_batches WHERE owner_id = :ownerId AND conflict_snapshot IS NOT NULL ORDER BY spool_uuid")
    fun observeConflicts(ownerId: String): Flow<List<InventoryUploadBatch>>

    @Query("""SELECT json_group_array(json_object('version',1,'uuid',l.uuid,'spool_uuid',f.uuid,
        'title',l.title,'grams_used',l.grams_used,'timestamp',l.timestamp,'is_failure',l.is_failure,'price_per_print',l.price_per_print))
        FROM usage_log l JOIN filaments f ON f.id=l.spoolId WHERE f.owner_id=:ownerId AND f.uuid=:uuid ORDER BY l.id""")
    suspend fun currentHistorySnapshots(ownerId: String, uuid: String): String

    @Query("""SELECT json_object('version',1,'uuid',l.uuid,'spool_uuid',f.uuid,'title',l.title,
        'grams_used',l.grams_used,'timestamp',l.timestamp,'is_failure',l.is_failure,'price_per_print',l.price_per_print)
        FROM usage_log l JOIN filaments f ON f.id=l.spoolId WHERE f.owner_id=:ownerId AND l.uuid=:uuid""")
    suspend fun currentLogSnapshot(ownerId: String, uuid: String): String?

    @Query("""SELECT json_group_array(json_object('id',id,'operation_id',operation_id,'entity_type',entity_type,
        'entity_uuid',entity_uuid,'operation',operation,'payload',json(payload),'created_at',created_at))
        FROM (SELECT * FROM pending_inventory_changes WHERE owner_id=:ownerId AND spool_uuid=:uuid AND acknowledged_at IS NULL ORDER BY id)""")
    suspend fun pendingSnapshots(ownerId: String, uuid: String): String

    @Transaction
    suspend fun conflictRecord(ownerId: String, uuid: String): InventoryConflictRecord? {
        val frozen = batch(ownerId, uuid)?.takeIf { it.conflictSnapshot != null } ?: return null
        val local = spoolByUuid(uuid)
        require(local == null || local.ownerId == ownerId)
        return InventoryConflictRecord(frozen, local, local?.let { logsForSpool(it.id) } ?: emptyList(),
            checkNotNull(currentSpoolSnapshot(ownerId, uuid) ?: queuedSpoolSnapshot(ownerId, uuid)),
            currentHistorySnapshots(ownerId, uuid), pendingSnapshots(ownerId, uuid))
    }

    @Insert
    suspend fun archiveResolution(resolution: InventoryConflictResolution)

    @Query("SELECT * FROM inventory_conflict_resolutions WHERE owner_id=:ownerId ORDER BY created_at, id")
    suspend fun resolutions(ownerId: String): List<InventoryConflictResolution>

    @Query("DELETE FROM pending_inventory_changes WHERE owner_id=:ownerId AND spool_uuid=:uuid AND acknowledged_at IS NULL")
    suspend fun removeResolvedPending(ownerId: String, uuid: String)

    @Query("""INSERT INTO pending_inventory_changes(operation_id,owner_id,entity_type,entity_uuid,spool_uuid,operation,payload,created_at)
        VALUES(lower(hex(randomblob(16))),:ownerId,:type,:uuid,:spoolUuid,:operation,:payload,:at)""")
    suspend fun queueResolution(ownerId: String, type: String, uuid: String, spoolUuid: String, operation: String, payload: String, at: Long)

    /** Compare the whole reviewed local version, archive both copies, then commit exactly one choice. */
    @Transaction
    suspend fun resolveConflict(expected: InventoryConflictRecord, cloud: RemoteInventorySnapshot,
        archive: InventoryConflictResolution, remoteDeletePayloads: Map<String, String>): Boolean {
        val owner = expected.batch.ownerId
        val uuid = expected.batch.spoolUuid
        require(archive.ownerId == owner && archive.spoolUuid == uuid)
        require(cloud.spool.ownerId == owner && cloud.spool.uuid == uuid && cloud.spool.revision >= expected.batch.baseRevision)
        require(cloud.logs.all { it.ownerId == owner && it.spoolUuid == uuid })
        if (conflictRecord(owner, uuid) != expected) return false
        require(archive.choice in listOf("local", "cloud"))
        archiveResolution(archive)
        removeResolvedPending(owner, uuid)
        removeBatch(owner, uuid, expected.batch.batchId)
        if (archive.choice == "cloud" || cloud.spool.deletedAt != null) {
            applyRemoteSnapshot(owner, cloud)
            if (archive.choice == "local" && expected.spool != null) {
                // A deleted server UUID cannot be resurrected. Keep this version as a new aggregate.
                val copy = expected.spool.copy(id = 0, uuid = Uuid.random().toString())
                val id = insertRemoteSpool(copy).toInt()
                expected.logs.forEach { insertRemoteLog(it.copy(id = 0, spoolId = id, uuid = Uuid.random().toString())) }
            }
        } else {
            saveState(InventorySyncState(owner, uuid, cloud.spool.revision))
            if (expected.spool == null) {
                queueResolution(owner, "spool", uuid, uuid, "delete", expected.spoolPayload, archive.createdAt)
            } else {
                val deletedLogIds = cloud.logs.filter { it.deletedAt != null }.map { it.uuid }.toSet()
                beginRemoteApply(InventoryRemoteApply(owner))
                try {
                    // Keeping a print deleted elsewhere creates a new print identity, never resurrection.
                    expected.logs.filter { it.uuid in deletedLogIds }.forEach {
                        updateRemoteLog(it.copy(uuid = Uuid.random().toString()))
                    }
                } finally { endRemoteApply(owner) }
                val chosenLogs = logsForSpool(expected.spool.id)
                queueResolution(owner, "spool", uuid, uuid, "upsert", checkNotNull(currentSpoolSnapshot(owner, uuid)), archive.createdAt)
                val remoteById = cloud.logs.associateBy { it.uuid }
                chosenLogs.forEach { log ->
                    val remoteLog = remoteById[log.uuid]
                    if (remoteLog == null || remoteLog.deletedAt != null || remoteLog.title != log.title ||
                        remoteLog.gramsUsed != log.gramsUsed || remoteLog.timestamp != log.timestamp ||
                        remoteLog.isFailure != log.isFailure || remoteLog.pricePerPrint != log.pricePerPrint) {
                        queueResolution(owner, "usage_log", log.uuid, uuid, "upsert", checkNotNull(currentLogSnapshot(owner, log.uuid)), archive.createdAt)
                    }
                }
                val chosenIds = chosenLogs.map { it.uuid }.toSet()
                cloud.logs.filter { it.deletedAt == null && it.uuid !in chosenIds }.forEach {
                    queueResolution(owner, "usage_log", it.uuid, uuid, "delete", remoteDeletePayloads.getValue(it.uuid), archive.createdAt)
                }
            }
            // Freeze the exact confirmed version; future local edits remain separate pending work.
            prepareSpool(owner, uuid)
        }
        return true
    }
}
