package com.aadil.spool.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Immutable local snapshot. No foreign key: deletion must not erase pending work or recovery data. */
@Entity(tableName = "pending_inventory_changes", indices = [Index(value = ["owner_id", "id"]), Index(value = ["operation_id"], unique = true)])
data class PendingInventoryChange(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "operation_id") val operationId: String,
    @ColumnInfo(name = "owner_id") val ownerId: String,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_uuid") val entityUuid: String,
    @ColumnInfo(name = "spool_uuid") val spoolUuid: String,
    val operation: String,
    val payload: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "acknowledged_at") val acknowledgedAt: Long? = null,
)
