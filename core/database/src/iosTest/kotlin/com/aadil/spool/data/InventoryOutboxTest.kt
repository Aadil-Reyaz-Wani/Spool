package com.aadil.spool.data

import androidx.room.Room
import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.aadil.spool.data.entity.Filament
import com.aadil.spool.data.entity.PendingInventoryChange
import com.aadil.spool.data.entity.UsageLog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)
class InventoryOutboxTest {
    private fun spool(owner: String = "a") = Filament(
        brand = "Quoted \"brand\"", material = "PLA", colorName = "Black", colorHex = 123,
        totalWeight = 1000.0, currentWeight = 900.0, note = "Line one\nLine two", price = 20.0,
        ownerId = owner,
    )
    private fun log(id: Int) = UsageLog(spoolId = id, title = "Print", gramsUsed = 10.0, timestamp = 123, isFailure = false, pricePerPrint = .2)
    private fun open(path: String) = getSpoolDatabase(Room.databaseBuilder<SpoolDatabase>(name = path, factory = { SpoolDatabaseConstructor.initialize() }))
    private suspend fun withFile(block: suspend (String) -> Unit) {
        val path = NSTemporaryDirectory() + "spool-outbox-${Uuid.random()}.db"
        try { block(path) } finally {
            listOf("", "-wal", "-shm").forEach { NSFileManager.defaultManager.removeItemAtPath(path + it, null) }
        }
    }
    private fun PendingInventoryChange.value(key: String) = Json.parseToJsonElement(payload).jsonObject.getValue(key).jsonPrimitive.content

    @Test fun guestEditsStayLocalUntilExplicitImportQueuesParentAndHistoryOnce() = runBlocking {
        withFile { path ->
            val db = open(path)
            try {
                val dao = db.spoolDao()
                dao.insertSpool(spool(""))
                val parent = dao.getAllSpools().first().single()
                dao.insertUsageLog(log(parent.id))
                val history = dao.getSpoolUsage(parent.id).first().single()
                dao.updateOwnedWeight("", parent.id, 880.0)
                assertTrue(dao.pendingInventoryChanges("").isEmpty())
                assertTrue(dao.pendingInventoryChanges("a").isEmpty())
                assertEquals(1, dao.importGuestInventory("a"))
                val changes = dao.pendingInventoryChanges("a")
                assertEquals(2, changes.size)
                assertEquals(listOf("spool", "usage_log"), changes.map { it.entityType })
                assertEquals(setOf(parent.uuid, history.uuid), changes.map { it.entityUuid }.toSet())
                assertTrue(changes.all { it.operation == "upsert" && it.spoolUuid == parent.uuid })
                assertEquals("880.0", changes.single { it.entityType == "spool" }.value("current_weight"))
                assertEquals(parent.uuid, changes.single { it.entityType == "usage_log" }.value("spool_uuid"))
                assertEquals(0, dao.importGuestInventory("a"))
                assertEquals(changes, dao.pendingInventoryChanges("a"))
            } finally { db.close() }
        }
    }

    @Test fun snapshotsAreImmutablePortableOrderedAndAccountScopedAcrossReopen() = runBlocking {
        withFile { path ->
            var db = open(path)
            try {
                val dao = db.spoolDao()
                val original = spool()
                dao.insertOwnedSpool("a", original)
                val parent = dao.getOwnedSpools("a").first().single()
                dao.updateOwnedWeight("a", parent.id, 800.0)
                dao.updateOwnedWeightWithTare("a", parent.id, 790.0, 140.0)
                dao.markOwnedAsDried("a", parent.id, 780.0, 141.0, 987)
                dao.updateOwnedSpool("a", dao.ownedSpool("a", parent.id)!!.copy(note = "Edited"), false)
                dao.insertOwnedSpool("b", spool("b"))
                val changes = dao.pendingInventoryChanges("a")
                assertEquals(5, changes.size)
                assertEquals(changes.map { it.id }.sorted(), changes.map { it.id })
                assertEquals(5, changes.map { it.operationId }.toSet().size)
                assertTrue(changes.all { it.createdAt > 0 && it.entityUuid == original.uuid })
                assertEquals("900.0", changes.first().value("current_weight"))
                assertEquals(original.brand, changes.first().value("brand"))
                assertEquals(original.note, changes.first().value("note"))
                assertEquals("null", changes.first().value("dry_baseline_weight"))
                assertEquals("780.0", changes.last().value("dry_baseline_weight"))
                assertEquals("141.0", changes.last().value("dry_baseline_tare_grams"))
                assertEquals("987", changes.last().value("last_dried_at"))
                assertTrue(changes.all { "id" !in Json.parseToJsonElement(it.payload).jsonObject })
                assertEquals(changes.take(2), dao.pendingInventoryChanges("a", 2))
                assertEquals(1, dao.pendingInventoryChanges("b").size)
                assertTrue(dao.pendingInventoryChanges("").isEmpty())
                db.close()
                db = open(path)
                assertEquals(changes, db.spoolDao().pendingInventoryChanges("a"))
            } finally { db.close() }
        }
    }

    @Test fun historyMutationsQueueWeightCostsAndCascadeDeletionSnapshots() = runBlocking {
        withFile { path ->
            val db = open(path)
            try {
                val dao = db.spoolDao()
                dao.insertOwnedSpool("a", spool())
                val parent = dao.getOwnedSpools("a").first().single()
                assertTrue(dao.insertOwnedLogAndDeductWeight("a", log(parent.id)))
                var history = dao.getOwnedUsage("a", parent.id).first().single()
                assertEquals(890.0, dao.ownedSpool("a", parent.id)!!.currentWeight)
                assertEquals(3, dao.pendingInventoryChanges("a").size)
                assertTrue(dao.updateOwnedLog("a", history.copy(gramsUsed = 20.0), true))
                assertEquals(880.0, dao.ownedSpool("a", parent.id)!!.currentWeight)
                history = dao.ownedLog("a", history.id)!!
                val beforeRejected = dao.pendingInventoryChanges("a")
                assertFalse(dao.updateOwnedLog("a", history.copy(gramsUsed = 9999.0), true))
                assertFalse(dao.insertOwnedLogAndDeductWeight("a", log(parent.id).copy(gramsUsed = 9999.0)))
                assertEquals(beforeRejected, dao.pendingInventoryChanges("a"))
                dao.updateOwnedLog("a", history.copy(title = "Renamed"), false)
                dao.updateOwnedSpool("a", dao.ownedSpool("a", parent.id)!!.copy(price = 30.0), true)
                assertEquals("0.6", dao.pendingInventoryChanges("a").last { it.entityType == "usage_log" }.value("price_per_print"))
                dao.updateOwnedUsageCosts("a", parent.id, .04)
                assertEquals("0.8", dao.pendingInventoryChanges("a").last().value("price_per_print"))
                dao.deleteOwnedLog("a", history, true)
                assertEquals(900.0, dao.ownedSpool("a", parent.id)!!.currentWeight)
                assertEquals("delete", dao.pendingInventoryChanges("a").last().operation)
                dao.insertOwnedLog("a", log(parent.id))
                val lastLog = dao.getOwnedUsage("a", parent.id).first().single()
                val beforeDelete = dao.pendingInventoryChanges("a").size
                dao.deleteOwnedSpool("a", parent)
                val tombstones = dao.pendingInventoryChanges("a").drop(beforeDelete)
                assertEquals(2, tombstones.size, "One child and one parent tombstone, without cascade duplicates")
                assertEquals(listOf(lastLog.uuid, parent.uuid), tombstones.map { it.entityUuid })
                assertTrue(tombstones.all { it.operation == "delete" })
                assertEquals("10.0", tombstones.first().value("grams_used"))
                assertEquals("900.0", tombstones.last().value("current_weight"))
                assertTrue(dao.getAllSpools().first().isEmpty())
                assertTrue(dao.getSpoolUsage(parent.id).first().isEmpty())
                val retained = dao.pendingInventoryChanges("a")
                db.close()
                val reopened = open(path)
                try { assertEquals(retained, reopened.spoolDao().pendingInventoryChanges("a")) } finally { reopened.close() }
            } finally { db.close() }
        }
    }

    @Test fun duplicateLogRollsBackWithoutDeductingWeightOrAddingQueueEntries() = runBlocking {
        withFile { path ->
            val db = open(path)
            try {
                val dao = db.spoolDao()
                dao.insertOwnedSpool("a", spool())
                val parent = dao.getOwnedSpools("a").first().single()
                val history = log(parent.id)
                assertTrue(dao.insertOwnedLogAndDeductWeight("a", history))
                val before = dao.pendingInventoryChanges("a")
                assertFailsWith<SQLiteException> { dao.insertOwnedLogAndDeductWeight("a", history) }
                assertEquals(890.0, dao.ownedSpool("a", parent.id)!!.currentWeight)
                assertEquals(1, dao.getSpoolUsage(parent.id).first().size)
                assertEquals(before, dao.pendingInventoryChanges("a"))
                dao.insertOwnedSpool("a", parent.copy(id = 0)) // IGNORE duplicate must not enqueue.
                assertEquals(before, dao.pendingInventoryChanges("a"))
            } finally { db.close() }
        }
    }

    @Test fun queueFailureRollsBackInventoryBulkCostsImportAndCascadeDelete() = runBlocking {
        withFile { path ->
            var db = open(path)
            db.spoolDao().insertSpool(spool())
            val parent = db.spoolDao().getAllSpools().first().single()
            db.spoolDao().insertUsageLog(log(parent.id))
            db.spoolDao().insertSpool(spool(""))
            val guestParent = db.spoolDao().getOwnedSpools("").first().single()
            db.spoolDao().insertUsageLog(log(guestParent.id))
            val before = db.spoolDao().pendingInventoryChanges("a")
            db.close()
            BundledSQLiteDriver().open(path).use {
                it.execSQL("CREATE TRIGGER reject_queue BEFORE INSERT ON pending_inventory_changes BEGIN SELECT RAISE(ABORT, 'Queue unavailable'); END")
            }
            db = open(path)
            try {
                val dao = db.spoolDao()
                assertFailsWith<SQLiteException> { dao.updateOwnedWeight("a", parent.id, 1.0) }
                assertFailsWith<SQLiteException> { dao.updateOwnedSpool("a", parent.copy(price = 50.0), true) }
                assertFailsWith<SQLiteException> { dao.insertOwnedLogAndDeductWeight("a", log(parent.id)) }
                assertFailsWith<SQLiteException> { dao.deleteOwnedSpool("a", parent) }
                assertFailsWith<SQLiteException> { dao.importGuestInventory("a") }
                assertEquals(parent, dao.ownedSpool("a", parent.id))
                assertEquals(1, dao.getOwnedUsage("a", parent.id).first().size)
                assertEquals(1, dao.guestSpoolCount().first())
                assertEquals(before, dao.pendingInventoryChanges("a"))
            } finally { db.close() }
            // Fail after the parent snapshot was inserted, proving the whole transaction rolls back.
            BundledSQLiteDriver().open(path).use {
                it.execSQL("DROP TRIGGER reject_queue")
                it.execSQL("CREATE TRIGGER reject_queue BEFORE INSERT ON pending_inventory_changes WHEN NEW.entity_type = 'usage_log' BEGIN SELECT RAISE(ABORT, 'History queue unavailable'); END")
            }
            db = open(path)
            try {
                val dao = db.spoolDao()
                assertFailsWith<SQLiteException> { dao.updateOwnedSpool("a", parent.copy(price = 50.0), true) }
                assertFailsWith<SQLiteException> { dao.importGuestInventory("a") }
                assertEquals(parent, dao.ownedSpool("a", parent.id))
                assertEquals(.2, dao.getOwnedUsage("a", parent.id).first().single().pricePerPrint)
                assertEquals(1, dao.guestSpoolCount().first())
                assertEquals(before, dao.pendingInventoryChanges("a"))
            } finally { db.close() }
        }
    }
}
