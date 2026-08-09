package com.aadil.spool.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

actual class SpoolDatabaseBuilder(
    private val context: Context
) {
    actual fun create(): RoomDatabase.Builder<SpoolDatabase> {
        val dbFile = context.applicationContext.getDatabasePath("spool_database")
        return Room.databaseBuilder<SpoolDatabase>(
            context = context.applicationContext,
            name = dbFile.absolutePath,
            factory = { SpoolDatabaseConstructor.initialize() }
        )
    }
}
