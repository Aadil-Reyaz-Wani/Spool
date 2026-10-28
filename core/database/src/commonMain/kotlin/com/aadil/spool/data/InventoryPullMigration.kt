package com.aadil.spool.data

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS inventory_pull_state (owner_id TEXT NOT NULL PRIMARY KEY, cursor INTEGER NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS inventory_remote_apply (owner_id TEXT NOT NULL PRIMARY KEY)")
        for (table in listOf("spool", "log")) for (event in listOf("insert", "update", "delete")) {
            connection.execSQL("DROP TRIGGER IF EXISTS outbox_${table}_$event")
        }
        installInventoryOutbox(connection)
    }
}
