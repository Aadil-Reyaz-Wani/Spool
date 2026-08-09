package com.aadil.spool.data

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.AutoMigrationSpec
import com.aadil.spool.data.dao.SpoolDao
import com.aadil.spool.data.entity.Filament
import com.aadil.spool.data.entity.UsageLog

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
@ConstructedBy(SpoolDatabaseConstructor::class)
abstract class SpoolDatabase : RoomDatabase() {
    abstract fun spoolDao(): SpoolDao

    @DeleteColumn(tableName = "usage_log", columnName = "price_per_print")
    class DeletePriceSpec : AutoMigrationSpec
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object SpoolDatabaseConstructor : RoomDatabaseConstructor<SpoolDatabase> {
    override fun initialize(): SpoolDatabase
}
