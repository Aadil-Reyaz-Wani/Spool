package com.aadil.spool.core.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.aadil.spool.core.data.sync.InventoryUploadCoordinator
import com.aadil.spool.core.data.sync.InventoryUploadScheduler
import com.aadil.spool.core.data.repository.InventoryScope
import kotlinx.coroutines.flow.emptyFlow
import com.aadil.spool.core.data.repository.InventorySession
import com.aadil.spool.core.data.sync.InventoryDownloader
import com.aadil.spool.core.data.sync.InventoryUploader
import com.aadil.spool.core.data.sync.InventoryUploadResult
import com.aadil.spool.core.model.InventoryPullPage
import com.aadil.spool.core.model.RemoteInventorySnapshot
import com.aadil.spool.core.model.RemoteInventorySpool
import com.aadil.spool.core.model.RemoteInventoryLog
import com.aadil.spool.core.network.*
import com.aadil.spool.data.*
import com.aadil.spool.data.entity.Filament
import com.aadil.spool.data.entity.UsageLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import kotlin.test.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)
class InventoryDownloaderTest {
    private val uuid = "10000000-0000-4000-8000-000000000001"
    private val logUuid = "20000000-0000-4000-8000-000000000001"
    private fun spool(revision: Long = 1, weight: Double = 900.0, deleted: String? = null) = RemoteInventorySpool(
        "a", uuid, revision, deleted, "Cloud", "PLA", "Black", 123, 1000.0, weight, 200, 60, "Remote note", 20.0, 123,
        890.0, 150.0, 151.0, 456)
    private fun log(deleted: String? = null) = RemoteInventoryLog("a", logUuid, uuid, deleted, "Print", 100.0, 123, false, 2.0)
    private fun page(cursor: Long = 0, next: Long = 1, spool: RemoteInventorySpool = spool(), logs: List<RemoteInventoryLog> = listOf(log()), more: Boolean = false) =
        InventoryPullPage("a", cursor, next, more, RemoteInventorySnapshot(spool, logs))
    private class Accounts : AccountRepository {
        override val googleWebClientId = ""
        override val sessions = MutableStateFlow(AccountSession(SpoolAccount("a", null, null)))
        override suspend fun signIn(credentials: GoogleCredentials) = Unit
        override suspend fun deleteAccount(accountId: String) { error("Unexpected account deletion") }
        override suspend fun clearDeletedSession(accountId: String) { error("Unexpected deletion cleanup") }
        override suspend fun updateDisplayName(accountId: String, name: String) { error("Unexpected profile write") }
        override suspend fun signOut() { sessions.value = AccountSession() }
    }
    private class Remote : InventoryPullRemote {
        var reply: InventoryPullReply = InventoryPullReply.RetryLater
        var handler: (suspend () -> InventoryPullReply)? = null
        val cursors = mutableListOf<Long>()
        override suspend fun pull(ownerId: String, cursor: Long): InventoryPullReply {
            cursors += cursor
            return handler?.invoke() ?: reply
        }
    }
    private inner class Fixture(val path: String) {
        fun open() = getSpoolDatabase(Room.databaseBuilder<SpoolDatabase>(name = path, factory = { SpoolDatabaseConstructor.initialize() }))
        var db = open()
        val dao get() = db.spoolDao()
        val sync get() = db.inventorySyncDao()
        val accounts = Accounts()
        var session = InventorySession(accounts, dao)
        val remote = Remote()
        fun downloader() = InventoryDownloader(sync, session, remote)
        suspend fun ready(owner: String = "a") = withTimeout(5000) { session.scope.first { it?.ownerId == owner }!! }
        suspend fun download(page: InventoryPullPage): InventoryUploadResult {
            remote.reply = InventoryPullReply.Page(page)
            return downloader().downloadNext(ready())
        }
        suspend fun restart() { session.close(); db.close(); db = open(); session = InventorySession(accounts, dao); ready() }
        fun close() { session.close(); db.close() }
    }
    private suspend fun withDatabase(block: suspend Fixture.() -> Unit) {
        val path = NSTemporaryDirectory() + "spool-pull-${Uuid.random()}.db"
        val f = Fixture(path)
        try { f.block() } finally {
            f.close()
            listOf("", "-wal", "-shm").forEach { NSFileManager.defaultManager.removeItemAtPath(path + it, null) }
        }
    }

    @Test fun newDeviceRestoresFieldsHistoryAndCursorWithoutEchoAndRestartKeepsThem() = runBlocking {
        withDatabase {
            assertEquals(InventoryUploadResult.NoWork, download(page()))
            val parent = sync.spoolByUuid(uuid)!!
            val history = sync.logByUuid(logUuid)!!
            assertEquals("Cloud", parent.brand)
            assertEquals(900.0, parent.currentWeight)
            assertEquals(890.0, parent.dryBaselineWeight)
            assertEquals(151.0, parent.lastWeighedTareGrams)
            assertEquals(456L, parent.lastDriedAt)
            assertEquals(parent.id, history.spoolId)
            assertEquals(2.0, history.pricePerPrint)
            assertFalse(sync.hasPending("a", uuid))
            assertEquals(1L, sync.serverRevision("a", uuid))
            restart()
            assertEquals(parent, sync.spoolByUuid(uuid))
            assertEquals(history, sync.logByUuid(logUuid))
            assertEquals(1L, sync.pullCursor("a"))
            assertEquals(InventoryUploadResult.NoWork, download(InventoryPullPage("a", 1, 1, false, null)))
            assertEquals(parent, sync.spoolByUuid(uuid))
        }
    }

    @Test fun cleanUpdateKeepsIntegerIdsAndAppliesHistoryAndParentDeletionWithoutEcho() = runBlocking {
        withDatabase {
            download(page())
            val id = sync.spoolByUuid(uuid)!!.id
            val historyId = sync.logByUuid(logUuid)!!.id
            download(page(1, 2, spool(2, 850.0)))
            assertEquals(id, sync.spoolByUuid(uuid)!!.id)
            assertEquals(historyId, sync.logByUuid(logUuid)!!.id)
            download(page(2, 3, spool(3, 950.0), listOf(log("deleted"))))
            assertNull(sync.logByUuid(logUuid))
            assertFalse(sync.hasPending("a", uuid))
            download(page(3, 4, spool(4, deleted = "deleted"), emptyList()))
            assertNull(sync.spoolByUuid(uuid))
            assertFalse(sync.hasPending("a", uuid))
            assertEquals(4L, sync.serverRevision("a", uuid))
            // A newer feed position carrying an older spool revision cannot resurrect a deleted UUID.
            download(page(4, 5, spool(3)))
            assertNull(sync.spoolByUuid(uuid))
        }
    }

    @Test fun concurrentLocalPrintIsPreservedWithFrozenRequestAndRemoteConflict() = runBlocking {
        withDatabase {
            download(page())
            val parent = sync.spoolByUuid(uuid)!!
            dao.updateOwnedWeight("a", parent.id, 875.0)
            val pending = dao.pendingInventoryChanges("a")
            val frozen = sync.prepareSpool("a", uuid)
            download(page(1, 2, spool(2, 800.0)))
            assertEquals(875.0, sync.spoolByUuid(uuid)!!.currentWeight)
            assertEquals(pending, dao.pendingInventoryChanges("a"))
            assertEquals(frozen.request, sync.batch("a", uuid)!!.request)
            assertNotNull(sync.batch("a", uuid)!!.conflictSnapshot)
            assertEquals(1L, sync.serverRevision("a", uuid))
            assertEquals(2L, sync.pullCursor("a"))
            restart()
            assertNotNull(sync.batch("a", uuid)!!.conflictSnapshot)
            assertEquals(pending, dao.pendingInventoryChanges("a"))
        }
    }

    @Test fun downloadDoesNotRebaseDirtyDataOrOverwriteLocalDeletion() = runBlocking {
        withDatabase {
            download(page())
            val parent = sync.spoolByUuid(uuid)!!
            dao.updateOwnedWeight("a", parent.id, 875.0)
            download(page(1, 2, spool()))
            assertEquals(875.0, sync.spoolByUuid(uuid)!!.currentWeight)
            assertNull(sync.batch("a", uuid))
            sync.deleteRemoteSpool("a", uuid) // Deliberately normal capture: models user's deletion.
            val pending = dao.pendingInventoryChanges("a")
            download(page(2, 3, spool(2)))
            assertNull(sync.spoolByUuid(uuid))
            assertEquals(pending, dao.pendingInventoryChanges("a"))
            assertNotNull(sync.batch("a", uuid)!!.conflictSnapshot)
        }
    }

    @Test fun localEditDuringDownloadWaitIsDetectedAtApplyTime() = runBlocking {
        withDatabase {
            download(page())
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            remote.handler = { entered.complete(Unit); release.await(); InventoryPullReply.Page(page(1, 2, spool(2))) }
            val active = ready()
            val work = async { downloader().downloadNext(active) }
            entered.await()
            dao.updateOwnedWeight("a", sync.spoolByUuid(uuid)!!.id, 700.0)
            release.complete(Unit)
            work.await()
            assertEquals(700.0, sync.spoolByUuid(uuid)!!.currentWeight)
            assertNotNull(sync.batch("a", uuid)!!.conflictSnapshot)
        }
    }

    @Test fun accountChangeCancelsResponseWithoutInventoryOrCursorLeak() = runBlocking {
        withDatabase {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            remote.handler = { entered.complete(Unit); release.await(); InventoryPullReply.Page(page()) }
            val active = ready()
            val work = async { downloader().downloadNext(active) }
            entered.await()
            accounts.sessions.value = AccountSession(SpoolAccount("b", null, null))
            ready("b")
            assertEquals(InventoryUploadResult.StaleScope, work.await())
            assertNull(sync.spoolByUuid(uuid))
            assertNull(sync.pullCursor("a"))
            assertNull(sync.pullCursor("b"))
        }
    }

    @Test fun uuidCollisionRollsBackParentHistoryCursorAndGuardThenLocalCaptureStillWorks() = runBlocking {
        withDatabase {
            dao.insertOwnedSpool("b", Filament(brand = "B", material = "PLA", colorName = "Black", colorHex = 123, note = ""))
            val other = dao.getOwnedSpools("b").first().single()
            dao.insertUsageLog(UsageLog(spoolId = other.id, title = "B history", gramsUsed = 1.0, timestamp = 1, isFailure = false, uuid = logUuid))
            assertFails { download(page()) }
            assertNull(sync.spoolByUuid(uuid))
            assertNull(sync.pullCursor("a"))
            assertEquals(other.id, sync.logByUuid(logUuid)!!.spoolId)
            dao.insertOwnedSpool("a", Filament(brand = "A", material = "PLA", colorName = "Black", colorHex = 123, note = ""))
            assertEquals(1, dao.pendingInventoryChanges("a").size)
        }
    }

    @Test fun restoredInventoryEditsUploadUsingPulledBaseRevision() = runBlocking {
        withDatabase {
            download(page(spool = spool(8)))
            dao.updateOwnedWeight("a", sync.spoolByUuid(uuid)!!.id, 750.0)
            var sent = ""
            val uploader = InventoryUploader(sync, session, object : InventoryUploadRemote {
                override suspend fun upload(ownerId: String, request: String): InventoryUploadReply {
                    sent = request
                    return InventoryUploadReply.Applied(9)
                }
            })
            assertEquals(InventoryUploadResult.Applied, uploader.uploadNext(ready()))
            assertTrue(sent.contains("\"p_base_revision\":8"))
            assertFalse(sync.hasPending("a", uuid))
            assertEquals(9L, sync.serverRevision("a", uuid))
        }
    }

    @Test fun retryAuthFailuresAndEmptyPagesNeverLoseCursorOrLocalData() = runBlocking {
        withDatabase {
            download(page())
            for ((reply, result) in listOf(InventoryPullReply.RetryLater to InventoryUploadResult.RetryLater,
                InventoryPullReply.NotAuthenticated to InventoryUploadResult.NotAuthenticated,
                InventoryPullReply.Rejected to InventoryUploadResult.Rejected)) {
                remote.reply = reply
                assertEquals(result, downloader().downloadNext(ready()))
                assertEquals(1L, sync.pullCursor("a"))
                assertEquals(900.0, sync.spoolByUuid(uuid)!!.currentWeight)
            }
            assertEquals(listOf(0L, 1L, 1L, 1L), remote.cursors)
        }
    }

    @Test fun failedCursorCommitRollsBackWholePageAndCanRetryWithoutDuplicates() = runBlocking {
        withDatabase {
            ready()
            // Open Room first, then inject a failure at the final cursor write.
            sync.pullCursor("a")
            BundledSQLiteDriver().open(path).use {
                it.execSQL("CREATE TRIGGER reject_pull BEFORE INSERT ON inventory_pull_state BEGIN SELECT RAISE(ABORT, 'Cursor failure'); END")
            }
            assertFails { download(page()) }
            assertNull(sync.spoolByUuid(uuid))
            assertNull(sync.logByUuid(logUuid))
            assertNull(sync.pullCursor("a"))
            assertNull(sync.serverRevision("a", uuid))
            BundledSQLiteDriver().open(path).use { it.execSQL("DROP TRIGGER reject_pull") }
            download(page())
            assertEquals(1, dao.getOwnedSpools("a").first().size)
            assertFalse(sync.hasPending("a", uuid))
            dao.updateOwnedWeight("a", sync.spoolByUuid(uuid)!!.id, 800.0)
            assertEquals(1, dao.pendingInventoryChanges("a").size)
        }
    }

    @Test fun cloudUuidCannotClaimGuestOrOtherAccountSpool() = runBlocking {
        for (owner in listOf("", "b")) withDatabase {
            dao.insertOwnedSpool(owner, Filament(brand = "Local", material = "PLA", colorName = "Black", colorHex = 123, note = "", uuid = uuid))
            val before = sync.spoolByUuid(uuid)
            assertFails { download(page()) }
            assertEquals(before, sync.spoolByUuid(uuid))
            assertNull(sync.pullCursor("a"))
            assertNull(sync.logByUuid(logUuid))
        }
    }

    @Test fun backgroundCoordinatorDownloadsWithoutLocalPendingWorkAndBoundsLargeRestores() = runBlocking {
        withDatabase {
            val scheduler = object : InventoryUploadScheduler {
                override val networkAvailable = emptyFlow<Unit>()
                override fun schedule(scope: InventoryScope) = Unit
                override fun cancel(scope: InventoryScope) = Unit
            }
            val upload = InventoryUploader(sync, session, object : InventoryUploadRemote {
                override suspend fun upload(ownerId: String, request: String): InventoryUploadReply = error("No local work should upload")
            })
            remote.handler = {
                val cursor = remote.cursors.last()
                val next = cursor + 1
                val spool = spool().copy(uuid = "10000000-0000-4000-8000-" + next.toString().padStart(12, '0'))
                InventoryPullReply.Page(page(cursor, next, spool, emptyList(), more = next < 25))
            }
            val coordinator = InventoryUploadCoordinator(sync, session, upload, accounts, scheduler, downloader = downloader())
            try {
                assertEquals(InventoryUploadResult.RetryLater, coordinator.runPending(ready()))
                assertEquals(20, dao.getOwnedSpools("a").first().size)
                assertEquals(20L, sync.pullCursor("a"))
                assertEquals(InventoryUploadResult.NoWork, coordinator.runPending(ready()))
                assertEquals(25, dao.getOwnedSpools("a").first().size)
                assertEquals(25L, sync.pullCursor("a"))
                assertTrue(dao.pendingInventoryChanges("a").isEmpty())
            } finally { coordinator.close() }
        }
    }

    @Test fun uploadReceiptRetryMustFinishBeforeDownloadingAgainstFrozenBatch() = runBlocking {
        withDatabase {
            dao.insertOwnedSpool("a", Filament(brand = "Local", material = "PLA", colorName = "Black", colorHex = 123, note = "", uuid = uuid))
            var reply: InventoryUploadReply = InventoryUploadReply.RetryLater
            val upload = InventoryUploader(sync, session, object : InventoryUploadRemote {
                override suspend fun upload(ownerId: String, request: String) = reply
            })
            val scheduler = object : InventoryUploadScheduler {
                override val networkAvailable = emptyFlow<Unit>()
                override fun schedule(scope: InventoryScope) = Unit
                override fun cancel(scope: InventoryScope) = Unit
            }
            val coordinator = InventoryUploadCoordinator(sync, session, upload, accounts, scheduler, downloader = downloader())
            try {
                assertEquals(InventoryUploadResult.RetryLater, coordinator.runPending(ready()))
                assertTrue(remote.cursors.isEmpty())
                val frozen = sync.batch("a", uuid)!!
                reply = InventoryUploadReply.Applied(1)
                remote.reply = InventoryPullReply.Page(page())
                assertEquals(InventoryUploadResult.NoWork, coordinator.runPending(ready()))
                assertNull(sync.batch("a", uuid))
                assertEquals(0, sync.conflictCount("a"))
                assertEquals(1L, sync.serverRevision("a", uuid))
                assertEquals(0L, frozen.baseRevision)
            } finally { coordinator.close() }
        }
    }

    @Test fun staleEntityCannotOverwriteDownloadEvenBetweenFormReadAndWrite() = runBlocking {
        withDatabase {
            download(page())
            val before = sync.spoolByUuid(uuid)!!
            download(page(1, 2, spool(2, 700.0)))
            assertFalse(dao.updateOwnedSpoolFromDraft("a", before.copy(brand = "Stale draft"), before))
            assertEquals(700.0, sync.spoolByUuid(uuid)!!.currentWeight)
            assertFalse(sync.hasPending("a", uuid))
            val current = sync.spoolByUuid(uuid)!!
            assertTrue(dao.updateOwnedSpoolFromDraft("a", current.copy(brand = "Fresh edit"), current))
            assertEquals("Fresh edit", sync.spoolByUuid(uuid)!!.brand)
            assertEquals(setOf("spool", "usage_log"), dao.pendingInventoryChanges("a").map { it.entityType }.toSet())
        }
    }
}
