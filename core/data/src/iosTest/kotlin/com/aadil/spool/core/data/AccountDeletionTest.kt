package com.aadil.spool.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.aadil.spool.core.data.sync.*
import com.aadil.spool.core.data.repository.InventoryScope
import com.aadil.spool.core.data.repository.InventorySession
import com.aadil.spool.core.network.*
import com.aadil.spool.data.*
import com.aadil.spool.data.entity.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import kotlin.test.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)
class AccountDeletionTest {
    private class Store : DataStore<Preferences> {
        val values = MutableStateFlow<Preferences>(emptyPreferences())
        private val lock = Mutex()
        override val data = values
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = lock.withLock {
            transform(values.value).also { values.value = it }
        }
    }
    private class Accounts : AccountRepository {
        override val googleWebClientId = ""
        override val sessions = MutableStateFlow(AccountSession(SpoolAccount("a", "Name", "a@example.com")))
        var deletions = 0
        var clearances = 0
        var fail = false
        var onDelete: suspend () -> Unit = {}
        override suspend fun signIn(credentials: GoogleCredentials) = Unit
        override suspend fun signOut() { sessions.value = AccountSession() }
        override suspend fun updateDisplayName(accountId: String, name: String) = Unit
        override suspend fun deleteAccount(accountId: String) {
            check(accountId == sessions.value.account?.id)
            deletions++; onDelete(); if (fail) error("Unconfirmed server response")
        }
        override suspend fun clearDeletedSession(accountId: String) {
            clearances++
            if (sessions.value.account?.id == accountId) sessions.value = AccountSession()
        }
    }
    private inner class Fixture(val path: String) {
        val db = getSpoolDatabase(Room.databaseBuilder<SpoolDatabase>(name = path, factory = { SpoolDatabaseConstructor.initialize() }))
        val accounts = Accounts()
        val store = Store()
        val dao = db.spoolDao()
        var session = newSession()
        fun newSession() = InventorySession(accounts, dao, preferences = store, syncDao = db.inventorySyncDao())
        suspend fun ready(owner: String = "a") = withTimeout(5_000) { session.scope.first { it?.ownerId == owner }!! }
        suspend fun seed() {
            ready()
            for (owner in listOf("", "a", "b")) {
                dao.insertOwnedSpool(owner, Filament(brand = owner, material = "PLA", colorName = "Black", colorHex = 1,
                    note = "Private notes", totalWeight = 1000.0, currentWeight = 900.0, price = 25.0))
                val spool = dao.getOwnedSpools(owner).first().single()
                dao.insertUsageLog(UsageLog(spoolId = spool.id, title = "Private print", gramsUsed = 10.0, timestamp = 1, isFailure = false, pricePerPrint = 1.0))
                if (owner.isBlank()) continue
                val sync = db.inventorySyncDao()
                sync.saveState(InventorySyncState(owner, spool.uuid, 1))
                sync.savePullState(InventoryPullState(owner, 1))
                sync.prepareSpool(owner, spool.uuid)
                db.inventoryRecoveryDao().insertReceipt(InventoryRecoveryRestore(owner, "source", spool.uuid, 1))
                db.inventoryRecoveryDao().insertRemoval(InventoryRecoveryRemoval(owner, "removed"))
                sql("INSERT INTO inventory_recovery_versions VALUES ('$owner','copy','deleted_cloud','{}',1)")
                sql("INSERT INTO inventory_conflict_resolutions VALUES ('$owner','$owner','${spool.uuid}','keep_local','{}','{}',1)")
            }
        }
        fun sql(query: String) { BundledSQLiteDriver().open(path).use { it.execSQL(query) } }
        fun count(table: String, owner: String): Long = BundledSQLiteDriver().open(path).use { c ->
            c.prepare("SELECT count(*) FROM $table WHERE owner_id='$owner'").use { s -> s.step(); s.getLong(0) }
        }
        fun counts(owner: String) = tables.associateWith { count(it, owner) }
        suspend fun restart() { session.close(); session = newSession() }
        fun close() { session.close(); db.close() }
    }
    private val tables = listOf("filaments", "pending_inventory_changes", "inventory_upload_batches", "inventory_sync_state",
        "inventory_pull_state", "inventory_remote_apply", "inventory_conflict_resolutions", "inventory_recovery_versions",
        "inventory_recovery_restores", "inventory_recovery_removals")
    private suspend fun fixture(block: suspend Fixture.() -> Unit) {
        val path = NSTemporaryDirectory() + "account-deletion-${Uuid.random()}.db"
        val f = Fixture(path)
        try { withTimeout(25_000) { f.block() } }
        finally { f.close(); listOf("", "-wal", "-shm").forEach { NSFileManager.defaultManager.removeItemAtPath(path + it, null) } }
    }

    @Test fun successfulDeletionPurgesOnlyConfirmedOwnerIncludingRecoveryAndOutbox() = runBlocking {
        fixture {
            seed(); val old = ready(); val other = counts("b"); val guest = counts("")
            val aId = dao.getOwnedSpools("a").first().single().id
            val b = dao.getOwnedSpools("b").first().single(); val bLogs = dao.getOwnedUsage("b", b.id).first()
            assertTrue(session.deleteAccount(old))
            ready("")
            assertTrue(counts("a").values.all { it == 0L }); assertEquals(other, counts("b")); assertEquals(guest, counts(""))
            assertEquals(b, dao.getOwnedSpools("b").first().single()); assertEquals(bLogs, dao.getOwnedUsage("b", b.id).first())
            BundledSQLiteDriver().open(path).use { c ->
                c.prepare("SELECT count(*) FROM usage_log WHERE spoolId=$aId").use { q -> q.step(); assertEquals(0L, q.getLong(0)) }
                c.prepare("PRAGMA foreign_key_check").use { q -> assertFalse(q.step()) }
            }
            assertEquals(1, accounts.deletions); assertEquals(1, accounts.clearances)
            assertFalse(session.withScope(old, false) { error("Old scope must stay invalid") })
            assertFalse(session.deleteAccount(old)); assertEquals(1, accounts.deletions)
            assertTrue(session.deletingOwners.value.isEmpty())
        }
    }

    @Test fun unconfirmedDeletionPreservesAllDataAndJournalSurvivesRestartWithoutAutomaticRetry() = runBlocking {
        fixture {
            seed(); val old = ready(); val before = counts("a"); accounts.fail = true
            assertFails { session.deleteAccount(old) }
            assertEquals(before, counts("a")); assertEquals(setOf("a"), session.deletingOwners.value)
            assertFalse(session.withScope(old, false) { error("Writes must be blocked") })
            restart(); val resumed = ready(); assertEquals(setOf("a"), session.deletingOwners.value)
            assertEquals(1, accounts.deletions); assertEquals(0, accounts.clearances)
            assertFalse(session.withScope(resumed, false) { error("Restart must not unlock writes") })
            accounts.fail = false; assertTrue(session.deleteAccount(resumed)); ready("")
            assertEquals(2, accounts.deletions); assertTrue(counts("a").values.all { it == 0L })
        }
    }

    @Test fun confirmedDeletionFinishesAfterRestartWithoutDeletingAgainOrSigningOutOtherOwner() = runBlocking {
        fixture {
            seed(); val old = ready(); val before = counts("a")
            sql("CREATE TRIGGER fail_purge BEFORE DELETE ON inventory_recovery_versions WHEN OLD.owner_id='a' BEGIN SELECT RAISE(ABORT,'Simulated local storage failure'); END")
            assertFails { session.deleteAccount(old) }; assertEquals(1, accounts.deletions); assertEquals(0, accounts.clearances)
            assertEquals(before, counts("a"))
            session.close(); sql("DROP TRIGGER fail_purge")
            accounts.sessions.value = AccountSession(SpoolAccount("b", null, null))
            session = newSession(); ready("b")
            assertEquals(1, accounts.deletions); assertTrue(counts("a").values.all { it == 0L })
            assertEquals("b", accounts.sessions.value.account?.id); assertTrue(count("filaments", "b") > 0)
            assertTrue(session.deletingOwners.value.isEmpty())
        }
    }

    @Test fun guestAndStaleScopeNeverReachServerDeletion() = runBlocking {
        fixture {
            val old = ready(); accounts.signOut(); val guest = ready("")
            assertFalse(session.deleteAccount(old)); assertFalse(session.deleteAccount(guest))
            assertEquals(0, accounts.deletions); assertTrue(session.deletingOwners.value.isEmpty())
            assertFails { db.inventorySyncDao().purgeDeletedAccount("") }
        }
    }

    @Test fun pendingDeletionDoesNotBlockOtherAccountAndConcurrentSaveCannotResurrectDeletedOwner() = runBlocking {
        fixture {
            seed(); val old = ready(); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            accounts.onDelete = { entered.complete(Unit); release.await() }
            coroutineScope {
                val deletion = async { session.deleteAccount(old) }; entered.await()
                val save = async { session.withScope(old, false) { error("Deletion must invalidate queued mutation") } }
                release.complete(Unit); assertTrue(deletion.await()); assertFalse(save.await())
            }
            ready(""); accounts.sessions.value = AccountSession(SpoolAccount("b", null, null)); val b = ready("b")
            assertTrue(session.withScope(b, false) { true }); assertEquals(0L, count("filaments", "a"))
        }
    }
    @Test fun pendingDeletionCancelsScheduledSyncAndRejectsForegroundAndWorkerRetries() = runBlocking {
        fixture {
            seed(); val old = ready(); val cancellations = MutableStateFlow(0); val schedules = MutableStateFlow(0)
            val scheduler = object : InventoryUploadScheduler {
                override val networkAvailable = kotlinx.coroutines.flow.emptyFlow<Unit>()
                override fun schedule(scope: InventoryScope) { schedules.value++ }
                override fun cancel(scope: InventoryScope) { if (scope == old) cancellations.value++ }
            }
            val remote = object : InventoryUploadRemote {
                override suspend fun upload(ownerId: String, request: String): InventoryUploadReply = error("Deleted account must not upload")
            }
            val uploader = InventoryUploader(db.inventorySyncDao(), session, remote)
            val coordinator = InventoryUploadCoordinator(db.inventorySyncDao(), session, uploader, accounts, scheduler)
            try {
                coordinator.start(); withTimeout(5000) { schedules.first { it > 0 } }
                accounts.fail = true; assertFails { session.deleteAccount(old) }
                withTimeout(5000) { cancellations.first { it > 0 } }
                assertEquals(InventoryUploadResult.StaleScope, coordinator.runPending(old))
                assertEquals(InventoryUploadResult.StaleScope, uploader.uploadNext(old))
                val scheduled = schedules.value; coordinator.retry(); assertEquals(scheduled, schedules.value)
            } finally { coordinator.close() }
        }
    }

}
