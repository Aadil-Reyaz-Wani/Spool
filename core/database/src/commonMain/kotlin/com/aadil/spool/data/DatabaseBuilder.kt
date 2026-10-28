package com.aadil.spool.data

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

expect class SpoolDatabaseBuilder {
    fun create(): RoomDatabase.Builder<SpoolDatabase>
}

fun getSpoolDatabase(builder: RoomDatabase.Builder<SpoolDatabase>): SpoolDatabase {
    return builder
        .addMigrations(MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22)
        .addCallback(inventoryOutboxCallback)
        .setDriver(BundledSQLiteDriver())
        .build()
}
