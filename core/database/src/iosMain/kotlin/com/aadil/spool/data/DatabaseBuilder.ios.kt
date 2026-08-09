package com.aadil.spool.data

import androidx.room.Room
import androidx.room.RoomDatabase
import platform.Foundation.NSHomeDirectory

actual class SpoolDatabaseBuilder {
    actual fun create(): RoomDatabase.Builder<SpoolDatabase> {
        val dbFilePath = NSHomeDirectory() + "/Documents/spool_database"
        return Room.databaseBuilder<SpoolDatabase>(
            name = dbFilePath,
            factory = { SpoolDatabaseConstructor.initialize() }
        )
    }
}
