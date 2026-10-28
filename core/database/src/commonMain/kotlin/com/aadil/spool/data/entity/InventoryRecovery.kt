package com.aadil.spool.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "inventory_recovery_versions", primaryKeys = ["owner_id", "id"])
data class InventoryRecoveryVersion(
    @ColumnInfo(name = "owner_id") val ownerId: String,
    val id: String,
    val kind: String,
    val snapshot: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** A durable receipt prevents a recreated confirmation from creating another copy. */
@Entity(tableName = "inventory_recovery_restores", primaryKeys = ["owner_id", "source_id"])
data class InventoryRecoveryRestore(
    @ColumnInfo(name = "owner_id") val ownerId: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "spool_uuid") val spoolUuid: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

data class InventoryRecoverySource(
    @ColumnInfo(name = "owner_id") val ownerId: String,
    val id: String,
    val kind: String,
    val snapshot: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "restored_uuid") val restoredUuid: String?,
)
