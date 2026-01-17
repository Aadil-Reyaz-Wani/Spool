package com.aadil.spool.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import com.aadil.spool.data.dao.SpoolDao
import com.aadil.spool.data.entity.Filament
import kotlin.concurrent.Volatile

@Database(
    entities = [Filament::class],
    version = 10,
    exportSchema = false
)
abstract class SpoolDatabase : RoomDatabase() {
    abstract fun spoolDao(): SpoolDao
}