package com.aadil.spool.data

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("""CREATE TABLE IF NOT EXISTS inventory_recovery_versions (
            owner_id TEXT NOT NULL,id TEXT NOT NULL,kind TEXT NOT NULL,snapshot TEXT NOT NULL,
            created_at INTEGER NOT NULL,PRIMARY KEY(owner_id,id))""")
        connection.execSQL("""CREATE TABLE IF NOT EXISTS inventory_recovery_restores (
            owner_id TEXT NOT NULL,source_id TEXT NOT NULL,spool_uuid TEXT NOT NULL,
            created_at INTEGER NOT NULL,PRIMARY KEY(owner_id,source_id))""")
        // Previously acknowledged deletion payloads are retained by the upload protocol.
        connection.execSQL("""INSERT OR IGNORE INTO inventory_recovery_versions
            SELECT p.owner_id,'deleted:device:'||p.spool_uuid,'deleted_device',
              json_object('spool',json(p.payload),'logs',json((SELECT json_group_array(json(payload)) FROM
                (SELECT payload FROM pending_inventory_changes l WHERE l.owner_id=p.owner_id AND l.spool_uuid=p.spool_uuid
                 AND l.entity_type='usage_log' AND l.operation='delete' AND l.id IN
                 (SELECT MAX(h.id) FROM pending_inventory_changes h WHERE h.owner_id=p.owner_id AND h.spool_uuid=p.spool_uuid
                  AND h.entity_type='usage_log' AND h.id<=p.id GROUP BY h.entity_uuid) ORDER BY l.id)))),p.created_at
            FROM pending_inventory_changes p WHERE p.entity_type='spool' AND p.operation='delete' AND p.owner_id!=''
            ORDER BY p.id""")
        installInventoryRecovery(connection)
    }
}

internal fun installInventoryRecovery(connection: SQLiteConnection) {
    // Capture the full live version before either cascade or outbox trigger can remove children.
    connection.execSQL("""CREATE TRIGGER IF NOT EXISTS recovery_spool_delete BEFORE DELETE ON filaments
        WHEN OLD.owner_id!='' AND NOT EXISTS (SELECT 1 FROM inventory_remote_apply WHERE owner_id=OLD.owner_id)
        BEGIN INSERT OR IGNORE INTO inventory_recovery_versions VALUES (
            OLD.owner_id,'deleted:device:'||OLD.uuid,'deleted_device',
            json_object('spool',${spoolPayload("OLD")},'logs',json((SELECT json_group_array(json_object(
                'uuid',uuid,'spool_uuid',OLD.uuid,'title',title,'grams_used',grams_used,'timestamp',timestamp,
                'is_failure',is_failure,'price_per_print',price_per_print)) FROM usage_log WHERE spoolId=OLD.id))),
            CAST((julianday('now')-2440587.5)*86400000 AS INTEGER)); END""")
}

val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("""CREATE TABLE IF NOT EXISTS inventory_recovery_removals (
            owner_id TEXT NOT NULL,source_id TEXT NOT NULL,PRIMARY KEY(owner_id,source_id))""")
        installInventoryRecoveryRemovals(connection)
    }
}

internal fun installInventoryRecoveryRemovals(connection: SQLiteConnection) {
    // A repeated cloud page must not recreate a copy removed on this installation.
    connection.execSQL("""CREATE TRIGGER IF NOT EXISTS recovery_skip_removed BEFORE INSERT ON inventory_recovery_versions
        WHEN EXISTS (SELECT 1 FROM inventory_recovery_removals
            WHERE owner_id=NEW.owner_id AND source_id=NEW.id)
        BEGIN SELECT RAISE(IGNORE); END""")
}
