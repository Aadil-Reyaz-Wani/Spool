package com.aadil.spool.data

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS `pending_inventory_changes` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `operation_id` TEXT NOT NULL,
                `owner_id` TEXT NOT NULL,
                `entity_type` TEXT NOT NULL,
                `entity_uuid` TEXT NOT NULL,
                `spool_uuid` TEXT NOT NULL,
                `operation` TEXT NOT NULL,
                `payload` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL
            )
        """.trimIndent())
        connection.execSQL("CREATE INDEX `index_pending_inventory_changes_owner_id_id` ON `pending_inventory_changes` (`owner_id`, `id`)")
        connection.execSQL("CREATE UNIQUE INDEX `index_pending_inventory_changes_operation_id` ON `pending_inventory_changes` (`operation_id`)")
        installInventoryOutbox(connection)
        // Previously imported account data has never been uploaded. Guest data needs explicit consent.
        connection.execSQL(enqueue("f.owner_id", "spool", "f.uuid", "f.uuid", "upsert", spoolPayload("f"), "FROM filaments f WHERE f.owner_id != ''"))
        connection.execSQL(enqueue("f.owner_id", "usage_log", "l.uuid", "f.uuid", "upsert", logPayload("l", "f.uuid"), "FROM usage_log l JOIN filaments f ON f.id = l.spoolId WHERE f.owner_id != ''"))
    }
}

internal val inventoryOutboxCallback = object : RoomDatabase.Callback() {
    override fun onCreate(connection: SQLiteConnection) {
        installInventoryOutbox(connection)
        installInventoryRecovery(connection)
        installInventoryRecoveryRemovals(connection)
    }
}

/** Triggers run in the writer's transaction, including bulk updates and foreign-key cascades. */
internal fun installInventoryOutbox(connection: SQLiteConnection) {
    connection.execSQL("CREATE TABLE IF NOT EXISTS inventory_remote_apply (owner_id TEXT NOT NULL PRIMARY KEY)")
    fun trigger(name: String, event: String, table: String, condition: String, body: String) {
        val owner = when (table) {
            "filaments" -> if (event.contains("DELETE")) "OLD.owner_id" else "NEW.owner_id"
            else -> "(SELECT owner_id FROM filaments WHERE id = " + (if (event.contains("DELETE")) "OLD" else "NEW") + ".spoolId)"
        }
        connection.execSQL("CREATE TRIGGER IF NOT EXISTS $name $event ON $table WHEN ($condition) AND NOT EXISTS (SELECT 1 FROM inventory_remote_apply WHERE owner_id = $owner) BEGIN $body END")
    }
    for (event in listOf("INSERT", "UPDATE")) {
        val importedHistory = if (event == "UPDATE") {
            // Queue the parent first when explicit import claims its existing history.
            enqueue("NEW.owner_id", "usage_log", "l.uuid", "NEW.uuid", "upsert", logPayload("l", "NEW.uuid"),
                "FROM usage_log l WHERE l.spoolId = NEW.id AND OLD.owner_id = ''")
        } else ""
        trigger("outbox_spool_${event.lowercase()}", "AFTER $event", "filaments", "NEW.owner_id != ''",
            enqueue("NEW.owner_id", "spool", "NEW.uuid", "NEW.uuid", "upsert", spoolPayload("NEW")) + importedHistory)
        trigger("outbox_log_${event.lowercase()}", "AFTER $event", "usage_log", "EXISTS (SELECT 1 FROM filaments WHERE id = NEW.spoolId AND owner_id != '')",
            enqueue("f.owner_id", "usage_log", "NEW.uuid", "f.uuid", "upsert", logPayload("NEW", "f.uuid"), "FROM filaments f WHERE f.id = NEW.spoolId"))
    }
    // Capture child snapshots before CASCADE removes them and their owner/parent lookup disappears.
    trigger("outbox_spool_delete", "BEFORE DELETE", "filaments", "OLD.owner_id != ''",
        enqueue("OLD.owner_id", "usage_log", "l.uuid", "OLD.uuid", "delete", logPayload("l", "OLD.uuid"), "FROM usage_log l WHERE l.spoolId = OLD.id") +
            enqueue("OLD.owner_id", "spool", "OLD.uuid", "OLD.uuid", "delete", spoolPayload("OLD")))
    trigger("outbox_log_delete", "AFTER DELETE", "usage_log", "EXISTS (SELECT 1 FROM filaments WHERE id = OLD.spoolId AND owner_id != '')",
        enqueue("f.owner_id", "usage_log", "OLD.uuid", "f.uuid", "delete", logPayload("OLD", "f.uuid"), "FROM filaments f WHERE f.id = OLD.spoolId"))
}

private fun enqueue(owner: String, type: String, uuid: String, spoolUuid: String, operation: String, payload: String, source: String = ""): String = """
    INSERT INTO pending_inventory_changes (operation_id, owner_id, entity_type, entity_uuid, spool_uuid, operation, payload, created_at)
    SELECT lower(hex(randomblob(16))), $owner, '$type', $uuid, $spoolUuid, '$operation', $payload,
        CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER) $source;
""".trimIndent()

// Version 1 payloads use stable UUID relationships; device-local integer IDs never leave Room.
internal fun spoolPayload(row: String): String = jsonPayload(row, listOf(
    "uuid", "brand", "material", "color_name", "color_hex", "total_weight", "current_weight",
    "temp_nozzle", "temp_bed", "note", "price", "timestamp", "dry_baseline_weight",
    "dry_baseline_tare_grams", "last_weighed_tare_grams", "last_dried_at",
))

private fun logPayload(row: String, spoolUuid: String): String = jsonPayload(row, listOf(
    "uuid", "title", "grams_used", "timestamp", "is_failure", "price_per_print",
), ", 'spool_uuid', $spoolUuid")

private fun jsonPayload(row: String, columns: List<String>, extra: String = ""): String =
    "json_object('version', 1, " + columns.joinToString { "'$it', $row.$it" } + extra + ")"
