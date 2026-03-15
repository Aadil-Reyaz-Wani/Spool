package com.aadil.spool.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.Insert
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import com.aadil.spool.data.dao.SpoolDao
import com.aadil.spool.data.entity.Filament
import com.aadil.spool.data.entity.UsageLog
import kotlin.concurrent.Volatile

@Database(
    entities = [Filament::class, UsageLog::class],
    version = 14,
    autoMigrations = [
        AutoMigration(from = 10, to = 11),
        AutoMigration(from = 11, to = 12, spec = SpoolDatabase.DeletePriceSpec::class),
        AutoMigration(from = 12, to = 13),
        AutoMigration(from = 13, to = 14),
    ],
    exportSchema = true
)
abstract class SpoolDatabase : RoomDatabase() {
    abstract fun spoolDao(): SpoolDao

    @DeleteColumn(tableName = "usage_log", columnName = "price_per_print")
    class DeletePriceSpec : AutoMigrationSpec
}