package com.aadil.spool.data

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(connection: SQLiteConnection) {
        // Room runs this in a transaction. Rebuild to require UUIDs without a blank SQL default.
        val spoolSequence = connection.sequence("filaments")
        val logSequence = connection.sequence("usage_log")
        connection.execSQL("""
            CREATE TABLE `_uuid_filaments` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `brand` TEXT NOT NULL,
                `material` TEXT NOT NULL,
                `color_name` TEXT NOT NULL,
                `color_hex` INTEGER NOT NULL,
                `total_weight` REAL NOT NULL DEFAULT 0,
                `current_weight` REAL NOT NULL DEFAULT 0,
                `temp_nozzle` INTEGER NOT NULL DEFAULT 0,
                `temp_bed` INTEGER NOT NULL DEFAULT 0,
                `note` TEXT NOT NULL DEFAULT '',
                `price` REAL NOT NULL DEFAULT 0,
                `timestamp` INTEGER NOT NULL DEFAULT 0,
                `dry_baseline_weight` REAL,
                `dry_baseline_tare_grams` REAL,
                `last_weighed_tare_grams` REAL,
                `last_dried_at` INTEGER,
                `uuid` TEXT NOT NULL
            )
        """.trimIndent())
        connection.execSQL("""
            CREATE TABLE `_uuid_usage_log` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `spoolId` INTEGER NOT NULL,
                `title` TEXT NOT NULL,
                `grams_used` REAL NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `is_failure` INTEGER NOT NULL,
                `price_per_print` REAL NOT NULL DEFAULT 0.0,
                `uuid` TEXT NOT NULL,
                FOREIGN KEY(`spoolId`) REFERENCES `_uuid_filaments`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        connection.execSQL("CREATE UNIQUE INDEX `index_filaments_uuid` ON `_uuid_filaments` (`uuid`)")
        connection.execSQL("CREATE UNIQUE INDEX `index_usage_log_uuid` ON `_uuid_usage_log` (`uuid`)")
        connection.execSQL("""
            INSERT INTO `_uuid_filaments`
            SELECT `id`, `brand`, `material`, `color_name`, `color_hex`, `total_weight`,
                `current_weight`, `temp_nozzle`, `temp_bed`, `note`, `price`, `timestamp`,
                `dry_baseline_weight`, `dry_baseline_tare_grams`, `last_weighed_tare_grams`,
                `last_dried_at`, $RANDOM_UUID_SQL
            FROM `filaments`
        """.trimIndent())
        connection.execSQL("""
            INSERT INTO `_uuid_usage_log`
            SELECT `id`, `spoolId`, `title`, `grams_used`, `timestamp`, `is_failure`,
                `price_per_print`, $RANDOM_UUID_SQL
            FROM `usage_log`
        """.trimIndent())
        // The new child references the new parent, so dropping the old parent cannot cascade to it.
        connection.execSQL("DROP TABLE `usage_log`")
        connection.execSQL("DROP TABLE `filaments`")
        connection.execSQL("ALTER TABLE `_uuid_filaments` RENAME TO `filaments`")
        connection.execSQL("ALTER TABLE `_uuid_usage_log` RENAME TO `usage_log`")
        // Retain high-water marks even when the highest numbered rows were previously deleted.
        connection.execSQL("UPDATE sqlite_sequence SET seq = $spoolSequence WHERE name = 'filaments'")
        connection.execSQL("UPDATE sqlite_sequence SET seq = $logSequence WHERE name = 'usage_log'")
    }
}

private fun SQLiteConnection.sequence(table: String): Long =
    prepare("SELECT seq FROM sqlite_sequence WHERE name = ?").use {
        it.bindText(1, table)
        if (it.step()) it.getLong(0) else 0L
    }

// A fresh RFC 4122 version 4 UUID per selected row, using SQLite bundled on both platforms.
private val RANDOM_UUID_SQL = """
    (lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' ||
    substr(lower(hex(randomblob(2))), 2) || '-' ||
    substr('89ab', (random() & 3) + 1, 1) || substr(lower(hex(randomblob(2))), 2) || '-' ||
    lower(hex(randomblob(6))))
""".trimIndent()
