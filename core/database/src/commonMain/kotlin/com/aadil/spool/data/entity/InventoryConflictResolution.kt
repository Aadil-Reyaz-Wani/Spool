package com.aadil.spool.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Retained copies of both reviewed versions. Recovery/retention is a separate feature. */
@Entity(tableName = "inventory_conflict_resolutions", indices = [Index(value = ["owner_id", "spool_uuid"])])
data class InventoryConflictResolution(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "owner_id") val ownerId: String,
    @ColumnInfo(name = "spool_uuid") val spoolUuid: String,
    val choice: String,
    @ColumnInfo(name = "local_snapshot") val localSnapshot: String,
    @ColumnInfo(name = "cloud_snapshot") val cloudSnapshot: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

data class InventoryConflictRecord(
    val batch: InventoryUploadBatch,
    val spool: Filament?,
    val logs: List<UsageLog>,
    val spoolPayload: String,
    val historyPayloads: String,
    val pendingSnapshots: String,
)
