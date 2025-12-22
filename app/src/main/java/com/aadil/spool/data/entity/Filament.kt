package com.aadil.spool.data.entity

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
    @ColumnInfo(name = "total_weight", defaultValue = "0")
    val totalWeight: Double = 0.0,
    @ColumnInfo(name = "current_weight", defaultValue = "0")
    val currentWeight: Double = 0.0,
    @ColumnInfo(name = "temp_nozzle", defaultValue = "0")
    val tempNozzle: Int = 0,
    @ColumnInfo(name = "temp_bed", defaultValue = "0")
    val tempBed: Int = 0,
    @ColumnInfo(name = "note", defaultValue = "")
    val note: String,
)