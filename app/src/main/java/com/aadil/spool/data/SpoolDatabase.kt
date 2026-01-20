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
import com.aadil.spool.data.entity.UsageLog
import kotlin.concurrent.Volatile

@Database(
    entities = [Filament::class, UsageLog::class],
    version = 11,
    autoMigrations = [
        AutoMigration(from = 10, to = 11)
    ],
    exportSchema = true
)
abstract class SpoolDatabase : RoomDatabase() {
    abstract fun spoolDao(): SpoolDao
}