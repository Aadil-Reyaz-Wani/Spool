package com.aadil.spool.data

import androidx.room.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.aadil.spool.data.entity.Filament
import com.aadil.spool.data.entity.UsageLog
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile
import platform.posix.getenv
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalForeignApi::class, ExperimentalUuidApi::class)
class MigrationTest {
    private val driver = BundledSQLiteDriver()

    @Test fun everySupportedVersionPreservesInventoryAndReopenKeepsIdentities() = runBlocking {
        for (version in 10..19) withDatabaseFile { path ->
            createLegacy(path, version, populated = true)
            val before = snapshot(path, version)
            val database = open(path)
            val spools: List<Filament>
            val logs: List<UsageLog>
            try {
                spools = database.spoolDao().getAllSpools().first().sortedBy { it.id }
                logs = spools.flatMap { database.spoolDao().getSpoolUsage(it.id).first() }.sortedBy { it.id }
                assertEquals(listOf(7, 19), spools.map { it.id }, "Version $version")
                assertEquals(if (version == 10) emptyList() else listOf(31, 32, 33), logs.map { it.id })
                spools.forEach { assertEquals("", it.ownerId, "Existing inventory remains guest-owned") }
                val uuids = spools.map { it.uuid } + logs.map { it.uuid }
                uuids.forEach(::assertUuid)
                assertEquals(uuids.size, uuids.toSet().size)
                if (version < 15) spools.forEach {
                    assertNull(it.dryBaselineWeight)
                    assertNull(it.dryBaselineTareGrams)
                    assertNull(it.lastWeighedTareGrams)
                    assertNull(it.lastDriedAt)
                }
                if (version < 14) spools.forEach { assertEquals(0L, it.timestamp) }
                // The existing 11 -> 12 -> 13 chain intentionally removes and re-adds cost.
                if (version == 11 || version == 12) logs.forEach { assertEquals(0.0, it.pricePerPrint) }
            } finally {
                database.close()
            }
            assertEquals(before, snapshot(path, version), "All pre-existing fields from version $version")
            driver.open(path).use {
                assertEquals(listOf(listOf("20")), it.rows("PRAGMA user_version"))
                assertTrue(it.rows("PRAGMA foreign_key_check").isEmpty())
                assertEquals(listOf(listOf("ok")), it.rows("PRAGMA integrity_check"))
            }
            val reopened = open(path)
            try {
                assertEquals(spools, reopened.spoolDao().getAllSpools().first().sortedBy { it.id })
                assertEquals(logs, spools.flatMap { reopened.spoolDao().getSpoolUsage(it.id).first() }.sortedBy { it.id })
            } finally {
                reopened.close()
            }
        }
    }

    @Test fun emptyMigrationAndFreshDatabasePersistGeneratedIdentities() = runBlocking {
        for (migrate in listOf(true, false)) withDatabaseFile { path ->
            if (migrate) createLegacy(path, 15, populated = false)
            val database = open(path)
            val spool = newSpool()
            val storedSpool: Filament
            val storedLog: UsageLog
            try {
                val dao = database.spoolDao()
                assertTrue(dao.getAllSpools().first().isEmpty())
                dao.insertSpool(spool)
                storedSpool = dao.getAllSpools().first().single()
                assertEquals(spool.copy(id = storedSpool.id), storedSpool)
                val log = UsageLog(spoolId = storedSpool.id, title = "New print", gramsUsed = 20.5, timestamp = 456, isFailure = false)
                dao.insertUsageLog(log)
                storedLog = dao.getSpoolUsage(storedSpool.id).first().single()
                assertEquals(log.copy(id = storedLog.id), storedLog)
                assertUuid(storedSpool.uuid)
                assertUuid(storedLog.uuid)
                assertTrue(storedSpool.uuid != storedLog.uuid)
            } finally {
                database.close()
            }
            val reopened = open(path)
            try {
                assertEquals(storedSpool, reopened.spoolDao().getSpool(storedSpool.id).first())
                assertEquals(storedLog, reopened.spoolDao().getUsageLogById(storedLog.id).first())
            } finally {
                reopened.close()
            }
        }
    }

    @Test fun migratedAndFreshSchemasEnforceUuidConstraintsAndKeepSqlDefaults() = runBlocking {
        for (migrate in listOf(true, false)) withDatabaseFile { path ->
            if (migrate) createLegacy(path, 15, populated = false)
            val database = open(path)
            try {
                database.spoolDao().getAllSpools().first()
            } finally {
                database.close()
            }
            driver.open(path).use { connection ->
                connection.execSQL("PRAGMA foreign_keys = ON")
                val spoolUuid = Uuid.random().toString()
                val logUuid = Uuid.random().toString()
                connection.execSQL("INSERT INTO filaments (id, brand, material, color_name, color_hex, uuid) VALUES (1, 'Defaults', 'PLA', 'Black', 0, '$spoolUuid')")
                connection.execSQL("INSERT INTO usage_log (id, spoolId, title, grams_used, timestamp, is_failure, uuid) VALUES (1, 1, 'Print', 1, 1, 0, '$logUuid')")
                for (table in listOf("filaments", "usage_log")) {
                    val columns = schema(16)["entities"]!!.jsonArray
                        .single { it.jsonObject["tableName"]!!.jsonPrimitive.content == table }
                        .jsonObject["fields"]!!.jsonArray.map { it.jsonObject["columnName"]!!.jsonPrimitive.content }
                    // INSERT and UPDATE both reject duplicate UUIDs; omission and NULL cannot create unidentified rows.
                    fun insertWith(uuidExpression: String?) {
                        val included = columns.filter { it != "id" && (uuidExpression != null || it != "uuid") }
                        val selected = included.map { if (it == "uuid") uuidExpression!! else "`$it`" }
                        connection.execSQL("INSERT INTO `$table` (${included.joinToString { "`$it`" }}) SELECT ${selected.joinToString()} FROM `$table` WHERE id = 1")
                    }
                    assertFailsWith<SQLiteException> { insertWith("uuid") }
                    assertFailsWith<SQLiteException> { insertWith("NULL") }
                    assertFailsWith<SQLiteException> { insertWith(null) }
                    insertWith("'${Uuid.random()}'")
                    assertFailsWith<SQLiteException> {
                        connection.execSQL("UPDATE `$table` SET uuid = (SELECT uuid FROM `$table` WHERE id = 1) WHERE id != 1")
                    }
                    assertEquals(2, connection.rows("SELECT * FROM `$table`").size)
                }
                assertFailsWith<SQLiteException> {
                    connection.execSQL("UPDATE usage_log SET spoolId = 999 WHERE id = 1")
                }
            }
            val reopened = open(path)
            try {
                val spool = assertNotNull(reopened.spoolDao().getSpool(1).first())
                assertEquals(0.0, spool.totalWeight)
                assertEquals(0.0, spool.currentWeight)
                assertEquals(0, spool.tempBed)
                assertEquals(0, spool.tempNozzle)
                assertEquals("", spool.note)
                assertEquals(0.0, spool.price)
                assertEquals(0L, spool.timestamp)
                assertNull(spool.dryBaselineWeight)
                assertNull(spool.dryBaselineTareGrams)
                assertNull(spool.lastWeighedTareGrams)
                assertNull(spool.lastDriedAt)
                assertEquals(0.0, reopened.spoolDao().getUsageLogById(1).first()!!.pricePerPrint)
            } finally {
                reopened.close()
            }
        }
    }

    @Test fun migratedEntitiesKeepIdentitiesThroughWritesAndCascadeStillWorks() = runBlocking {
        withDatabaseFile { path ->
            createLegacy(path, 15, populated = true)
            val database = open(path)
            try {
                val dao = database.spoolDao()
                val original = assertNotNull(dao.getSpool(7).first())
                val log = assertNotNull(dao.getUsageLogById(31).first())
                assertFailsWith<SQLiteException> { dao.insertUsageLog(log.copy(id = 0, title = "Duplicate UUID")) }
                assertEquals(log, dao.getUsageLogById(31).first())
                dao.updateSpoolAndUsageCosts(original.copy(note = "Edited", price = 40.0))
                dao.updateCurrentWeightWithTare(7, 730.0, 142.0)
                dao.markAsDried(7, 725.0, 143.0, 789)
                assertEquals(original.uuid, dao.getSpool(7).first()!!.uuid)
                assertEquals(log.uuid, dao.getUsageLogById(31).first()!!.uuid)
                assertTrue(dao.editLogAndRestoreCurrentWeight(log.copy(title = "Edited print", gramsUsed = 12.5)))
                assertEquals(log.copy(title = "Edited print", gramsUsed = 12.5), dao.getUsageLogById(31).first())

                dao.insertSpool(newSpool())
                val added = dao.getAllSpools().first().single { it.id !in listOf(7, 19) }
                assertTrue(added.id > 900, "Deleted spool IDs must not be reused")
                dao.insertUsageLog(UsageLog(spoolId = added.id, title = "New", gramsUsed = 1.0, timestamp = 1, isFailure = false))
                assertTrue(dao.getSpoolUsage(added.id).first().single().id > 1200, "Deleted log IDs must not be reused")

                dao.deleteSpool(original)
                assertTrue(dao.getSpoolUsage(7).first().isEmpty())
                assertNull(dao.getUsageLogById(31).first())
                assertNull(dao.getUsageLogById(32).first())
                assertNotNull(dao.getUsageLogById(33).first())
                assertNotNull(dao.getSpool(19).first())
            } finally {
                database.close()
            }
        }
    }

    @Test fun accountInventoryIsQueuedOnceOnUpgradeWithoutChangingStoredValues() = runBlocking {
        withDatabaseFile { path ->
            createLegacy(path, 17, populated = true)
            driver.open(path).use { it.execSQL("UPDATE filaments SET owner_id = 'account-a' WHERE id = 7") }
            val before = snapshot(path, 17)
            val db = open(path)
            val queued = try {
                val changes = db.spoolDao().pendingInventoryChanges("account-a")
                assertEquals(3, changes.size)
                assertEquals(listOf("spool", "usage_log", "usage_log"), changes.map { it.entityType })
                assertTrue(changes.all { it.operation == "upsert" })
                assertTrue(db.spoolDao().pendingInventoryChanges("").isEmpty())
                changes
            } finally { db.close() }
            assertEquals(before, snapshot(path, 17))
            val reopened = open(path)
            try { assertEquals(queued, reopened.spoolDao().pendingInventoryChanges("account-a")) }
            finally { reopened.close() }
        }
    }

    @Test fun version18PendingOperationsSurviveUpgradeWithoutRequeueingOrChangingIds() = runBlocking {
        withDatabaseFile { path ->
            createLegacy(path, 18, populated = true)
            driver.open(path).use { it.execSQL("UPDATE filaments SET owner_id = 'account-a' WHERE id = 7") }
            val before = snapshot(path, 18)
            val db = open(path)
            try {
                val pending = db.spoolDao().pendingInventoryChanges("account-a")
                assertEquals(3, pending.size)
                assertTrue(pending.all { it.acknowledgedAt == null })
                assertNull(db.inventorySyncDao().serverRevision("account-a", pending.first().spoolUuid))
            } finally { db.close() }
            assertEquals(before, snapshot(path, 18), "Inventory, UUIDs and all existing operation IDs/snapshots are preserved")
            driver.open(path).use {
                assertEquals(listOf(listOf("900")), it.rows("SELECT seq FROM sqlite_sequence WHERE name = 'filaments'"))
                assertEquals(listOf(listOf("1200")), it.rows("SELECT seq FROM sqlite_sequence WHERE name = 'usage_log'"))
                assertTrue(it.rows("PRAGMA foreign_key_check").isEmpty())
            }
        }
    }

    @Test fun version19PreservesFrozenBatchesRevisionsAndPendingOperations() = runBlocking {
        withDatabaseFile { path ->
            createLegacy(path, 19, populated = true)
            driver.open(path).use {
                it.execSQL("UPDATE filaments SET owner_id = 'account-a' WHERE id = 7")
                it.execSQL("INSERT INTO inventory_sync_state SELECT owner_id, uuid, 9 FROM filaments WHERE id = 7")
                it.execSQL("INSERT INTO inventory_upload_batches SELECT owner_id, uuid, 'saved-batch', 9, '{}', '[]', NULL FROM filaments WHERE id = 7")
                it.execSQL("DROP TABLE inventory_remote_apply")
            }
            val before = snapshot(path, 19)
            val db = open(path)
            try {
                assertEquals(3, db.spoolDao().pendingInventoryChanges("account-a").size)
                val spool = db.spoolDao().getAllSpools().first().single { it.id == 7 }
                assertEquals(9L, db.inventorySyncDao().serverRevision("account-a", spool.uuid))
                assertEquals("saved-batch", db.inventorySyncDao().batch("account-a", spool.uuid)?.batchId)
            } finally { db.close() }
            assertEquals(before, snapshot(path, 19))
        }
    }

    private fun open(path: String) = getSpoolDatabase(Room.databaseBuilder<SpoolDatabase>(
        name = path,
        factory = { SpoolDatabaseConstructor.initialize() },
    ))

    private fun schema(version: Int): JsonObject {
        val directory = checkNotNull(getenv("SPOOL_SCHEMA_DIR")?.toKString()) { "Run via :core:database:iosSimulatorArm64Test" }
        val text = checkNotNull(NSString.stringWithContentsOfFile(
            "$directory/com.aadil.spool.data.SpoolDatabase/$version.json", NSUTF8StringEncoding, null,
        ))
        return Json.parseToJsonElement(text).jsonObject["database"]!!.jsonObject
    }

    private fun createLegacy(path: String, version: Int, populated: Boolean) {
        val schema = schema(version)
        driver.open(path).use { connection ->
            connection.execSQL("PRAGMA foreign_keys = ON")
            for (entity in schema["entities"]!!.jsonArray) {
                val table = entity.jsonObject["tableName"]!!.jsonPrimitive.content
                connection.execSQL(entity.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                if (populated && table in listOf("filaments", "usage_log")) {
                    val columns = entity.jsonObject["fields"]!!.jsonArray.map { it.jsonObject["columnName"]!!.jsonPrimitive.content }
                    val fixtures = if (table == "filaments") spoolRows else logRows
                    fixtures.forEach { fixture ->
                        val row = mapOf("owner_id" to "''") + fixture + ("uuid" to "'00000000-0000-4000-8000-${fixture.getValue("id").padStart(12, '0')}'")
                        connection.execSQL("INSERT INTO `$table` (${columns.joinToString { "`$it`" }}) VALUES (${columns.joinToString { row.getValue(it) }})")
                    }
                    connection.execSQL("UPDATE sqlite_sequence SET seq = ${if (table == "filaments") 900 else 1200} WHERE name = '$table'")
                }
            }
            if (version >= 16) {
                for (entity in schema["entities"]!!.jsonArray) {
                    val table = entity.jsonObject["tableName"]!!.jsonPrimitive.content
                    entity.jsonObject["indices"]?.jsonArray?.forEach { index ->
                        connection.execSQL(index.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                    }
                }
            }
            if (version >= 18) installInventoryOutbox(connection)
            schema["setupQueries"]!!.jsonArray.forEach { connection.execSQL(it.jsonPrimitive.content) }
            connection.execSQL("PRAGMA user_version = $version")
        }
    }

    private fun snapshot(path: String, version: Int): Map<String, List<List<String?>>> =
        driver.open(path).use { connection ->
            schema(version)["entities"]!!.jsonArray.associate { entity ->
                val table = entity.jsonObject["tableName"]!!.jsonPrimitive.content
                val columns = entity.jsonObject["fields"]!!.jsonArray.map { it.jsonObject["columnName"]!!.jsonPrimitive.content }
                    .filterNot { version == 11 && table == "usage_log" && it == "price_per_print" }
                table to connection.rows("SELECT ${columns.joinToString { "`$it`" }} FROM `$table` ORDER BY `${columns.first()}`")
            }
        }

    private fun SQLiteConnection.rows(sql: String): List<List<String?>> = prepare(sql).use { statement ->
        buildList {
            while (statement.step()) add(List(statement.getColumnCount()) { index ->
                if (statement.isNull(index)) null else statement.getText(index)
            })
        }
    }

    private suspend fun withDatabaseFile(block: suspend (String) -> Unit) {
        val path = NSTemporaryDirectory() + "spool-migration-${Uuid.random()}.db"
        try {
            block(path)
        } finally {
            listOf("", "-wal", "-shm").forEach { NSFileManager.defaultManager.removeItemAtPath(path + it, null) }
        }
    }

    private fun assertUuid(value: String) {
        assertEquals(value, Uuid.parse(value).toString())
        assertTrue(Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}").matches(value))
    }

    private fun newSpool() = Filament(brand = "New", material = "PLA", colorName = "Black", colorHex = 0, note = "")

    private val spoolRows = listOf(
        mapOf(
            "id" to "7", "brand" to "'Prusament'", "material" to "'PLA'", "color_name" to "'Galaxy Black'",
            "color_hex" to "4280821800", "total_weight" to "1000.0", "current_weight" to "750.25",
            "temp_nozzle" to "215", "temp_bed" to "60", "note" to "'Production spool: it''s dry\n第二行'",
            "price" to "29.99", "timestamp" to "1700000000000", "dry_baseline_weight" to "745.5",
            "dry_baseline_tare_grams" to "140.25", "last_weighed_tare_grams" to "141.5", "last_dried_at" to "1700000002000",
        ),
        mapOf(
            "id" to "19", "brand" to "'Other'", "material" to "'PETG'", "color_name" to "'White'",
            "color_hex" to "4294967295", "total_weight" to "500.5", "current_weight" to "0.0",
            "temp_nozzle" to "0", "temp_bed" to "0", "note" to "''", "price" to "0.0", "timestamp" to "1",
            "dry_baseline_weight" to "NULL", "dry_baseline_tare_grams" to "NULL", "last_weighed_tare_grams" to "NULL", "last_dried_at" to "NULL",
        ),
    )
    private val logRows = listOf(
        mapOf("id" to "31", "spoolId" to "7", "title" to "'Benchy Test'", "grams_used" to "15.5", "timestamp" to "1700000001000", "is_failure" to "0", "price_per_print" to "0.46"),
        mapOf("id" to "32", "spoolId" to "7", "title" to "'Failed print'", "grams_used" to "234.25", "timestamp" to "1700000002000", "is_failure" to "1", "price_per_print" to "7.0251575"),
        mapOf("id" to "33", "spoolId" to "19", "title" to "'Other spool'", "grams_used" to "500.5", "timestamp" to "1700000003000", "is_failure" to "0", "price_per_print" to "0.0"),
    )
}
