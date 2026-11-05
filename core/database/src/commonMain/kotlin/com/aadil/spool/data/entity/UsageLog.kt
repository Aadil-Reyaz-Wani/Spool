package com.aadil.spool.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Entity(
    tableName = "usage_log",
    indices = [Index(value = ["uuid"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = Filament::class,
            parentColumns = ["id"],
            childColumns = ["spoolId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class UsageLog(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val spoolId: Int,
    val title: String,
    @ColumnInfo(name = "grams_used")
    val gramsUsed: Double,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,
    @ColumnInfo(name = "is_failure")
    val isFailure: Boolean,
    @ColumnInfo(name = "price_per_print", defaultValue = "0.0")
    val pricePerPrint: Double = 0.0,
    val uuid: String = Uuid.random().toString()
)
