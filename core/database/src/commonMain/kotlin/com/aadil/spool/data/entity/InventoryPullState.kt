package com.aadil.spool.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inventory_pull_state")
data class InventoryPullState(
    @PrimaryKey @ColumnInfo(name = "owner_id") val ownerId: String,
    val cursor: Long,
)

/** Exists only inside a remote-apply transaction; rollback also restores outbox capture. */
@Entity(tableName = "inventory_remote_apply")
data class InventoryRemoteApply(@PrimaryKey @ColumnInfo(name = "owner_id") val ownerId: String)
