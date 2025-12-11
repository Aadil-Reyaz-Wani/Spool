package com.kashmir.spool.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity("filaments")
data class Filament(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val brand: String,
    val material: String,
    @ColumnInfo(name = "color_name")
    val colorName: String,
    @ColumnInfo(name = "color_hex")
    val colorHex: Long,
    @ColumnInfo(name = "total_weight")
    val totalWeight: Double?,
    @ColumnInfo(name = "current_weight")
    val currentWeight: Double?,
    @ColumnInfo(name = "temp_nozzle", defaultValue = "0")
    val tempNozzle: Int?,
    @ColumnInfo(name = "temp_bed", defaultValue = "0")
    val tempBed: Int?,
    @ColumnInfo(name = "note", defaultValue = "")
    val note: String,
)