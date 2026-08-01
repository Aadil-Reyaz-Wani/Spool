package com.aadil.spool.data

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

expect class SpoolDatabaseBuilder {
    fun create(): RoomDatabase.Builder<SpoolDatabase>
}

fun getSpoolDatabase(builder: RoomDatabase.Builder<SpoolDatabase>): SpoolDatabase {
    return builder
        .setDriver(BundledSQLiteDriver())
        .build()
}
