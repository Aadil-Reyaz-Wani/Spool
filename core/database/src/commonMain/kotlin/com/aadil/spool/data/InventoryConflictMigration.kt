package com.aadil.spool.data

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("""CREATE TABLE IF NOT EXISTS inventory_conflict_resolutions (
            id TEXT NOT NULL PRIMARY KEY, owner_id TEXT NOT NULL, spool_uuid TEXT NOT NULL,
            choice TEXT NOT NULL, local_snapshot TEXT NOT NULL, cloud_snapshot TEXT NOT NULL, created_at INTEGER NOT NULL)""")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_conflict_resolutions_owner_id_spool_uuid ON inventory_conflict_resolutions (owner_id, spool_uuid)")
    }
}
