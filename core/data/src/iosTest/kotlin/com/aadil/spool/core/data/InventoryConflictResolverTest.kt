package com.aadil.spool.core.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.aadil.spool.core.data.repository.InventorySession
import com.aadil.spool.core.data.sync.*
import com.aadil.spool.core.model.*
import com.aadil.spool.core.network.*
import com.aadil.spool.data.*
import com.aadil.spool.data.entity.UsageLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import kotlin.test.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)
class InventoryConflictResolverTest {
    private val uuid = "10000000-0000-4000-8000-000000000001"
    private val originalLog = "20000000-0000-4000-8000-000000000001"
    private val localLog = "20000000-0000-4000-8000-000000000002"
    private val cloudLog = "20000000-0000-4000-8000-000000000003"
    private fun spool(revision: Long = 1, weight: Double = 900.0, deleted: String? = null) = RemoteInventorySpool(
        "a", uuid, revision, deleted, "Original", "PLA", "Black", 123, 1000.0, weight, 200, 60, "Note", 20.0, 123,
        890.0, 150.0, 151.0, 456)
    private fun log(uuid: String = originalLog, title: String = "Original print", grams: Double = 100.0, deleted: String? = null) =
        RemoteInventoryLog("a", uuid, this.uuid, deleted, title, grams, 123, false, grams * .02)
    private fun cloud(revision: Long = 2, weight: Double = 800.0) = RemoteInventorySnapshot(spool(revision, weight), listOf(log(), log(cloudLog, "Cloud print", 50.0)))
    private class Accounts : AccountRepository {
        override val googleWebClientId = ""
        override val sessions = MutableStateFlow(AccountSession(SpoolAccount("a", null, null)))
        override suspend fun signIn(credentials: GoogleCredentials) = Unit
        override suspend fun deleteAccount(accountId: String) { error("Unexpected account deletion") }
        override suspend fun clearDeletedSession(accountId: String) { error("Unexpected deletion cleanup") }
        override suspend fun updateDisplayName(accountId: String, name: String) { error("Unexpected profile write") }
        override suspend fun signOut() { sessions.value = AccountSession() }
    }
    private class Remote(var value: RemoteInventorySnapshot) : InventoryConflictRemote {
        var handler: (suspend () -> InventorySnapshotReply)? = null
        override suspend fun snapshot(ownerId: String, spoolUuid: String) = handler?.invoke() ?: InventorySnapshotReply.Snapshot(value)
    }
    private inner class Fixture(val path: String) {
        fun open() = getSpoolDatabase(Room.databaseBuilder<SpoolDatabase>(name = path, factory = { SpoolDatabaseConstructor.initialize() }))
        var db = open()
        val dao get() = db.spoolDao()
        val sync get() = db.inventorySyncDao()
        val accounts = Accounts()
        var session = InventorySession(accounts, dao)
        val remote = Remote(cloud())
        fun resolver() = InventoryConflictResolver(sync, session, remote)
        suspend fun ready(owner: String = "a") = withTimeout(5000) { session.scope.first { it?.ownerId == owner }!! }
        suspend fun seed() {
            ready()
            sync.applyRemoteSnapshot("a", RemoteInventorySnapshot(spool(), listOf(log())))
            val parent = sync.spoolByUuid(uuid)!!
            assertTrue(dao.insertOwnedLogAndDeductWeight("a", UsageLog(spoolId = parent.id, uuid = localLog, title = "Local print", gramsUsed = 10.0, timestamp = 234, isFailure = true, pricePerPrint = .2)))
            val batch = sync.prepareSpool("a", uuid)
            sync.saveConflict("a", uuid, batch.batchId, """{"server":{"note":"conflict"}}""")
        }
        suspend fun review() = assertIs<InventoryConflictReviewResult.Ready>(resolver().review(ready(), uuid)).review
        suspend fun restart() { session.close(); db.close(); db = open(); session = InventorySession(accounts, dao); ready() }
        fun close() { session.close(); db.close() }
    }
    private suspend fun withDatabase(block: suspend Fixture.() -> Unit) {
        val path = NSTemporaryDirectory() + "spool-conflict-${Uuid.random()}.db"
        val f = Fixture(path)
        try { f.seed(); f.block() } finally {
            f.close()
            listOf("", "-wal", "-shm").forEach { NSFileManager.defaultManager.removeItemAtPath(path + it, null) }
        }
    }

    @Test fun reviewIsReadOnlyAndCloudChoiceArchivesBothFullVersionsWithoutEchoOrCursorChange() = runBlocking {
        withDatabase {
            val pending = dao.pendingInventoryChanges("a")
            val frozen = sync.batch("a", uuid)!!
            val review = review()
            assertEquals(890.0, review.local.spool.currentWeight)
            assertEquals(800.0, review.cloud.spool.currentWeight)
            assertEquals(2, review.local.logs.size)
            assertEquals(pending, dao.pendingInventoryChanges("a"))
            assertEquals(frozen, sync.batch("a", uuid))
            assertTrue(sync.resolutions("a").isEmpty())
            val id = sync.spoolByUuid(uuid)!!.id
            assertEquals(InventoryConflictResolveResult.Resolved, resolver().resolve(review, InventoryConflictChoice.Cloud))
            assertEquals(id, sync.spoolByUuid(uuid)!!.id)
            assertEquals(800.0, sync.spoolByUuid(uuid)!!.currentWeight)
            assertEquals(setOf(originalLog, cloudLog), sync.logsForSpool(id).map { it.uuid }.toSet())
            assertTrue(dao.pendingInventoryChanges("a").isEmpty())
            assertNull(sync.batch("a", uuid))
            assertNull(sync.pullCursor("a"))
            assertEquals(2L, sync.serverRevision("a", uuid))
            val archive = sync.resolutions("a").single()
            val local = Json.parseToJsonElement(archive.localSnapshot).jsonObject
            assertEquals(frozen.request, local.getValue("batch").jsonObject.getValue("request").jsonPrimitive.content)
            assertEquals(2, local.getValue("pending").jsonArray.size)
            assertTrue(archive.localSnapshot.contains("Local print"))
            assertTrue(archive.cloudSnapshot.contains("Cloud print"))
            assertTrue(sync.resolutions("b").isEmpty())
            restart()
            assertEquals(archive, sync.resolutions("a").single())
            dao.updateOwnedWeight("a", id, 700.0)
            assertEquals(1, dao.pendingInventoryChanges("a").size)
        }
    }

    @Test fun localChoiceBuildsFreshConditionalDeltaForTheEntireChosenHistory() = runBlocking {
        withDatabase {
            val review = review()
            assertEquals(InventoryConflictResolveResult.Resolved, resolver().resolve(review, InventoryConflictChoice.Local))
            assertEquals(890.0, sync.spoolByUuid(uuid)!!.currentWeight)
            val replacement = sync.batch("a", uuid)!!
            assertNotEquals(review.record.batch.batchId, replacement.batchId)
            assertEquals(2L, replacement.baseRevision)
            assertNull(replacement.conflictSnapshot)
            val request = Json.parseToJsonElement(replacement.request).jsonObject
            assertEquals(890.0, request.getValue("p_spool").jsonObject.getValue("current_weight").jsonPrimitive.double)
            val logs = request.getValue("p_logs").jsonArray.map { it.jsonObject }.associateBy { it.getValue("uuid").jsonPrimitive.content }
            assertEquals(setOf(localLog, cloudLog), logs.keys)
            assertEquals("upsert", logs.getValue(localLog).getValue("operation").jsonPrimitive.content)
            assertEquals("delete", logs.getValue(cloudLog).getValue("operation").jsonPrimitive.content)
            assertEquals(1, sync.resolutions("a").size)
            val uploader = InventoryUploader(sync, session, object : InventoryUploadRemote {
                override suspend fun upload(ownerId: String, request: String) = InventoryUploadReply.Applied(3)
            })
            assertEquals(InventoryUploadResult.Applied, uploader.uploadNext(ready()))
            assertEquals(3L, sync.serverRevision("a", uuid))
            assertTrue(dao.pendingInventoryChanges("a").isEmpty())
        }
    }

    @Test fun newerCloudEditRequiresNewReviewWithoutArchivingOrReplacingPendingChanges() = runBlocking {
        withDatabase {
            val review = review()
            remote.value = cloud(3, 700.0)
            assertEquals(InventoryConflictResolveResult.Changed, resolver().resolve(review, InventoryConflictChoice.Cloud))
            assertEquals(review.record, sync.conflictRecord("a", uuid))
            assertTrue(sync.resolutions("a").isEmpty())
            val refreshed = review()
            assertEquals(700.0, refreshed.cloud.spool.currentWeight)
            assertEquals(InventoryConflictResolveResult.Resolved, resolver().resolve(refreshed, InventoryConflictChoice.Cloud))
        }
    }

    @Test fun localEditDuringConfirmationRefetchIsPreservedAndRequiresReview() = runBlocking {
        withDatabase {
            val review = review()
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            remote.handler = { entered.complete(Unit); release.await(); InventorySnapshotReply.Snapshot(remote.value) }
            val work = async { resolver().resolve(review, InventoryConflictChoice.Cloud) }
            entered.await()
            dao.updateOwnedWeight("a", sync.spoolByUuid(uuid)!!.id, 870.0)
            release.complete(Unit)
            assertEquals(InventoryConflictResolveResult.Changed, work.await())
            assertEquals(870.0, sync.spoolByUuid(uuid)!!.currentWeight)
            assertEquals(3, dao.pendingInventoryChanges("a").size)
            assertTrue(sync.resolutions("a").isEmpty())
        }
    }

    @Test fun accountSwitchDuringConfirmationCancelsAndCannotArchiveOrApplyToNewOwner() = runBlocking {
        withDatabase {
            val review = review()
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            remote.handler = { entered.complete(Unit); release.await(); InventorySnapshotReply.Snapshot(remote.value) }
            val work = async { resolver().resolve(review, InventoryConflictChoice.Cloud) }
            entered.await()
            accounts.sessions.value = AccountSession(SpoolAccount("b", null, null))
            ready("b")
            assertEquals(InventoryConflictResolveResult.StaleScope, work.await())
            assertEquals(review.record, sync.conflictRecord("a", uuid))
            assertTrue(sync.resolutions("a").isEmpty())
            assertTrue(sync.resolutions("b").isEmpty())
        }
    }

    @Test fun keepingLocalAfterRemoteSpoolDeletionMakesNewIdentitiesAndRetainsOldTombstone() = runBlocking {
        withDatabase {
            remote.value = RemoteInventorySnapshot(spool(2, deleted = "deleted"), listOf(log(deleted = "deleted")))
            val review = review()
            assertEquals(InventoryConflictResolveResult.Resolved, resolver().resolve(review, InventoryConflictChoice.Local))
            assertNull(sync.spoolByUuid(uuid))
            assertEquals(2L, sync.serverRevision("a", uuid))
            val copy = dao.getOwnedSpools("a").first().single()
            assertNotEquals(uuid, copy.uuid)
            assertEquals(890.0, copy.currentWeight)
            assertEquals(review.record.spool!!.dryBaselineWeight, copy.dryBaselineWeight)
            val history = sync.logsForSpool(copy.id)
            assertEquals(2, history.size)
            assertTrue(history.all { it.uuid !in setOf(originalLog, localLog) })
            assertEquals(review.record.logs.map { it.title }.toSet(), history.map { it.title }.toSet())
            assertEquals(3, dao.pendingInventoryChanges("a").size)
            assertTrue(dao.pendingInventoryChanges("a").all { it.spoolUuid == copy.uuid })
            assertEquals(0L, sync.prepareSpool("a", copy.uuid).baseRevision)
            assertEquals(1, sync.resolutions("a").size)
        }
    }

    @Test fun cloudDeletionChoiceDeletesLocalWithoutEchoAndPreservesItsPrintInArchive() = runBlocking {
        withDatabase {
            remote.value = RemoteInventorySnapshot(spool(2, deleted = "deleted"), listOf(log(deleted = "deleted")))
            assertEquals(InventoryConflictResolveResult.Resolved, resolver().resolve(review(), InventoryConflictChoice.Cloud))
            assertTrue(dao.getOwnedSpools("a").first().isEmpty())
            assertTrue(dao.pendingInventoryChanges("a").isEmpty())
            assertTrue(sync.resolutions("a").single().localSnapshot.contains("Local print"))
        }
    }

    @Test fun choosingLocalDeletionQueuesConditionalParentDeletionAndCloudChoiceCanRestoreIt() = runBlocking {
        for (choice in InventoryConflictChoice.entries) withDatabase {
            sync.deleteRemoteSpool("a", uuid) // Normal outbox capture models a user's deletion.
            val review = review()
            assertNotNull(review.local.spool.deletedAt)
            assertTrue(review.local.logs.isNotEmpty())
            assertEquals(InventoryConflictResolveResult.Resolved, resolver().resolve(review, choice))
            if (choice == InventoryConflictChoice.Local) {
                assertNull(sync.spoolByUuid(uuid))
                val batch = sync.batch("a", uuid)!!
                assertEquals(2L, batch.baseRevision)
                assertTrue(Json.parseToJsonElement(batch.request).jsonObject.getValue("p_deleted").jsonPrimitive.boolean)
            } else {
                assertEquals(800.0, sync.spoolByUuid(uuid)!!.currentWeight)
                assertTrue(dao.pendingInventoryChanges("a").isEmpty())
            }
        }
    }

    @Test fun localPrintDeletedInCloudGetsNewUuidRatherThanResurrectingOldOne() = runBlocking {
        withDatabase {
            remote.value = RemoteInventorySnapshot(spool(2), listOf(log(deleted = "deleted")))
            assertEquals(InventoryConflictResolveResult.Resolved, resolver().resolve(review(), InventoryConflictChoice.Local))
            assertNull(sync.logByUuid(originalLog))
            val prints = sync.logsForSpool(sync.spoolByUuid(uuid)!!.id)
            assertEquals(2, prints.size)
            val copy = prints.single { it.title == "Original print" }
            assertNotEquals(originalLog, copy.uuid)
            val request = Json.parseToJsonElement(sync.batch("a", uuid)!!.request).jsonObject
            assertTrue(request.getValue("p_logs").jsonArray.none { it.jsonObject.getValue("uuid").jsonPrimitive.content == originalLog })
        }
    }

    @Test fun archiveOrApplyFailureRollsBackChoicePendingBatchAndOutboxGuard() = runBlocking {
        for (failure in listOf("archive", "apply")) withDatabase {
            val review = review()
            val statement = if (failure == "archive") "CREATE TRIGGER reject_choice BEFORE INSERT ON inventory_conflict_resolutions BEGIN SELECT RAISE(ABORT,'Archive failure'); END"
                else "CREATE TRIGGER reject_choice BEFORE UPDATE ON filaments BEGIN SELECT RAISE(ABORT,'Apply failure'); END"
            BundledSQLiteDriver().open(path).use { it.execSQL(statement) }
            assertEquals(InventoryConflictResolveResult.Unavailable, resolver().resolve(review, InventoryConflictChoice.Cloud))
            assertEquals(review.record, sync.conflictRecord("a", uuid))
            assertTrue(sync.resolutions("a").isEmpty())
            BundledSQLiteDriver().open(path).use { it.execSQL("DROP TRIGGER reject_choice") }
            assertEquals(InventoryConflictResolveResult.Resolved, resolver().resolve(review, InventoryConflictChoice.Cloud))
            dao.updateOwnedWeight("a", sync.spoolByUuid(uuid)!!.id, 700.0)
            assertEquals(1, dao.pendingInventoryChanges("a").size)
        }
    }

    @Test fun failedRefetchForeignResponsesAndDuplicateConfirmationNeverDiscardPendingWork() = runBlocking {
        withDatabase {
            val review = review()
            remote.handler = { InventorySnapshotReply.Unavailable }
            assertEquals(InventoryConflictResolveResult.Unavailable, resolver().resolve(review, InventoryConflictChoice.Local))
            assertEquals(review.record, sync.conflictRecord("a", uuid))
            remote.handler = null
            remote.value = cloud().copy(spool = spool(2).copy(ownerId = "b"))
            assertEquals(InventoryConflictResolveResult.Unavailable, resolver().resolve(review, InventoryConflictChoice.Cloud))
            remote.value = cloud()
            assertEquals(InventoryConflictResolveResult.Resolved, resolver().resolve(review, InventoryConflictChoice.Cloud))
            assertEquals(InventoryConflictResolveResult.Changed, resolver().resolve(review, InventoryConflictChoice.Cloud))
            assertEquals(1, sync.resolutions("a").size)
        }
    }

    @Test fun fullHistoryAndAllPendingOperationsAreArchivedBeyondQueueInspectionLimit() = runBlocking {
        withDatabase {
            val id = sync.spoolByUuid(uuid)!!.id
            repeat(125) { dao.insertOwnedLog("a", UsageLog(spoolId = id, title = "Extra $it", gramsUsed = 1.0, timestamp = it.toLong(), isFailure = false)) }
            val review = review()
            assertEquals(127, review.local.logs.size)
            assertEquals(InventoryConflictResolveResult.Resolved, resolver().resolve(review, InventoryConflictChoice.Local))
            val saved = Json.parseToJsonElement(sync.resolutions("a").single().localSnapshot).jsonObject
            assertEquals(127, saved.getValue("logs").jsonArray.size)
            assertEquals(127, saved.getValue("pending").jsonArray.size)
            assertEquals(127, Json.parseToJsonElement(sync.batch("a", uuid)!!.request).jsonObject.getValue("p_logs").jsonArray.size)
        }
    }

    @Test fun cloneInsertFailureRestoresOriginalRowsQueueBatchAndArchiveAtomically() = runBlocking {
        withDatabase {
            remote.value = RemoteInventorySnapshot(spool(2, deleted = "deleted"), listOf(log(deleted = "deleted")))
            val review = review()
            BundledSQLiteDriver().open(path).use {
                it.execSQL("CREATE TRIGGER reject_clone BEFORE INSERT ON filaments BEGIN SELECT RAISE(ABORT,'Clone failure'); END")
            }
            assertEquals(InventoryConflictResolveResult.Unavailable, resolver().resolve(review, InventoryConflictChoice.Local))
            assertEquals(review.record, sync.conflictRecord("a", uuid))
            assertTrue(sync.resolutions("a").isEmpty())
            assertEquals(1L, sync.serverRevision("a", uuid))
            BundledSQLiteDriver().open(path).use { it.execSQL("DROP TRIGGER reject_clone") }
            assertEquals(InventoryConflictResolveResult.Resolved, resolver().resolve(review, InventoryConflictChoice.Local))
            assertEquals(1, dao.getOwnedSpools("a").first().size)
        }
    }

    @Test fun bothDeletedChoiceDoesNotCreateANewSpoolOrQueueResurrection() = runBlocking {
        withDatabase {
            sync.deleteRemoteSpool("a", uuid)
            remote.value = RemoteInventorySnapshot(spool(2, deleted = "deleted"), listOf(log(deleted = "deleted")))
            val review = review()
            assertNotNull(review.local.spool.deletedAt)
            assertEquals(InventoryConflictResolveResult.Resolved, resolver().resolve(review, InventoryConflictChoice.Local))
            assertTrue(dao.getOwnedSpools("a").first().isEmpty())
            assertTrue(dao.pendingInventoryChanges("a").isEmpty())
            assertEquals(1, sync.resolutions("a").size)
        }
    }

    @Test fun historyOnlyEditAfterPreviewInvalidatesTheWholeReviewedVersion() = runBlocking {
        withDatabase {
            val review = review()
            val changed = sync.logByUuid(localLog)!!.copy(title = "Changed during review")
            assertTrue(dao.updateOwnedLog("a", changed, false))
            assertEquals(InventoryConflictResolveResult.Changed, resolver().resolve(review, InventoryConflictChoice.Cloud))
            assertEquals(changed, sync.logByUuid(localLog))
            assertTrue(sync.resolutions("a").isEmpty())
            assertNotNull(sync.batch("a", uuid)!!.conflictSnapshot)
        }
    }
}
