package com.aadil.spool.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(tableName = "inventory_upload_batches", primaryKeys = ["owner_id", "spool_uuid"], indices = [Index(value = ["batch_id"], unique = true)])
data class InventoryUploadBatch(
    @ColumnInfo(name = "owner_id") val ownerId: String,
    @ColumnInfo(name = "spool_uuid") val spoolUuid: String,
    @ColumnInfo(name = "batch_id") val batchId: String,
    @ColumnInfo(name = "base_revision") val baseRevision: Long,
    val request: String,
    @ColumnInfo(name = "operation_ids") val operationIds: String,
    @ColumnInfo(name = "conflict_snapshot") val conflictSnapshot: String? = null,
)

@Entity(tableName = "inventory_sync_state", primaryKeys = ["owner_id", "spool_uuid"])
data class InventorySyncState(
    @ColumnInfo(name = "owner_id") val ownerId: String,
    @ColumnInfo(name = "spool_uuid") val spoolUuid: String,
    @ColumnInfo(name = "server_revision") val serverRevision: Long,
)
