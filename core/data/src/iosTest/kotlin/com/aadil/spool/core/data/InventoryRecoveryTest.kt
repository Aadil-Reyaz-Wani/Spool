package com.aadil.spool.core.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.aadil.spool.core.data.repository.InventorySession
import com.aadil.spool.core.data.sync.*
import com.aadil.spool.core.model.*
import com.aadil.spool.core.network.*
import com.aadil.spool.data.*
import com.aadil.spool.data.entity.*
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
class InventoryRecoveryTest {
    private val uuid = "10000000-0000-4000-8000-000000000001"
    private val logUuid = "20000000-0000-4000-8000-000000000001"
    private val sourceId = "deleted:device:$uuid"
    private val json = Json
    private fun snapshot(deleted: Boolean = false): RemoteInventorySnapshot {
        val s = RemoteInventorySpool("a", uuid, 1, if (deleted) "deleted" else null,
            "RecoveryQA", "PLA", "Black", 4280821800, 1000.0, 900.0, 215, 60, "Note\n第二行", 20.0, 123,
            899.0, 140.0, 141.0, 456)
        return RemoteInventorySnapshot(s, listOf(RemoteInventoryLog("a", logUuid, uuid,
            if (deleted) "deleted" else null, "Print", 100.0, 234, true, 2.0)))
    }
    private class Accounts : AccountRepository {
        override val googleWebClientId = ""
        override val sessions = MutableStateFlow(AccountSession(SpoolAccount("a", null, null)))
        override suspend fun signIn(credentials: GoogleCredentials) = Unit
        override suspend fun deleteAccount(accountId: String) { error("Unexpected account deletion") }
        override suspend fun clearDeletedSession(accountId: String) { error("Unexpected deletion cleanup") }
        override suspend fun updateDisplayName(accountId: String, name: String) { error("Unexpected profile write") }
        override suspend fun signOut() { sessions.value = AccountSession() }
    }
    private inner class Fixture(val path: String) {
        fun open() = getSpoolDatabase(Room.databaseBuilder<SpoolDatabase>(name = path, factory = { SpoolDatabaseConstructor.initialize() }))
        var db = open()
        val sync get() = db.inventorySyncDao()
        val recovery get() = db.inventoryRecoveryDao()
        val accounts = Accounts()
        var session = InventorySession(accounts, db.spoolDao())
        fun repository() = LocalInventoryRecoveryRepository(recovery, session)
        suspend fun ready(owner: String = "a") = withTimeout(5000) { session.scope.first { it?.ownerId == owner }!! }
        suspend fun seedDeleted() {
            ready(); sync.applyRemoteSnapshot("a", snapshot()); sync.deleteRemoteSpool("a", uuid)
        }
        suspend fun preview(id: String = sourceId) = assertNotNull(repository().preview(ready(), id))
        fun sql(statement: String) { BundledSQLiteDriver().open(path).use { it.execSQL(statement) } }
        suspend fun restart() { session.close(); db.close(); db = open(); session = InventorySession(accounts, db.spoolDao()); ready() }
        fun close() { session.close(); db.close() }
    }
    private suspend fun fixture(block: suspend Fixture.() -> Unit) {
        val path = NSTemporaryDirectory() + "recovery-${Uuid.random()}.db"
        val f = Fixture(path)
        try { f.block() } finally { f.close(); listOf("", "-wal", "-shm").forEach { NSFileManager.defaultManager.removeItemAtPath(path + it, null) } }
    }

    @Test fun localDeletionCapturesBeforeCascadeAndPreviewIsReadOnly() = runBlocking {
        fixture {
            seedDeleted()
            assertNull(sync.spoolByUuid(uuid))
            val pending = db.spoolDao().pendingInventoryChanges("a")
            assertEquals(2, pending.size)
            val item = repository().observe(ready()).first().single()
            assertEquals("deleted_device", item.kind)
            assertEquals("RecoveryQA", item.brand)
            val p = preview()
            assertEquals(900.0, p.version.spool.currentWeight)
            assertEquals("Note\n第二行", p.version.spool.note)
            assertEquals(1, p.version.logs.size)
            assertTrue(p.version.logs.single().isFailure)
            assertEquals(2.0, p.version.logs.single().pricePerPrint)
            assertEquals(pending, db.spoolDao().pendingInventoryChanges("a"))
            assertNull(p.source.restoredUuid)
        }
    }

    @Test fun offlineRestorePreservesEveryFieldQueuesNewIdentitiesAndRetainsOriginal() = runBlocking {
        fixture {
            seedDeleted(); val p = preview()
            assertEquals(InventoryRecoveryResult.Restored, repository().restore(ready(), p))
            val restored = db.spoolDao().getOwnedSpools("a").first().single()
            assertNotEquals(uuid, restored.uuid)
            assertEquals(snapshot().spool.let { s -> Filament(restored.id, s.brand, s.material, s.colorName, s.colorHex,
                s.totalWeight, s.currentWeight, s.tempNozzle, s.tempBed, s.note, s.price, s.timestamp,
                s.dryBaselineWeight, s.dryBaselineTareGrams, s.lastWeighedTareGrams, s.lastDriedAt, restored.uuid, "a") }, restored)
            val log = sync.logsForSpool(restored.id).single()
            assertNotEquals(logUuid, log.uuid)
            assertEquals(UsageLog(log.id, restored.id, "Print", 100.0, 234, true, 2.0, log.uuid), log)
            assertEquals(4, db.spoolDao().pendingInventoryChanges("a").size)
            assertNull(sync.spoolByUuid(uuid))
            assertEquals(p.source.snapshot, recovery.source("a", sourceId)!!.snapshot)
            assertEquals(restored.uuid, recovery.source("a", sourceId)!!.restoredUuid)
            assertEquals(0L, sync.prepareSpool("a", restored.uuid).baseRevision)
        }
    }

    @Test fun duplicateConfirmationRestartAndDeletingCopyCannotReplayOriginalRestore() = runBlocking {
        fixture {
            seedDeleted(); val p = preview(); val scope = ready()
            val a = async { repository().restore(scope, p) }; val b = async { repository().restore(scope, p) }
            assertEquals(InventoryRecoveryResult.Restored, a.await()); assertEquals(InventoryRecoveryResult.Restored, b.await())
            assertEquals(1, db.spoolDao().getOwnedSpools("a").first().size)
            val copiedUuid = recovery.source("a", sourceId)!!.restoredUuid!!
            restart()
            assertTrue(repository().observe(ready()).first().single().restored)
            assertEquals(InventoryRecoveryResult.Restored, repository().restore(ready(), p))
            sync.deleteRemoteSpool("a", copiedUuid)
            assertEquals(InventoryRecoveryResult.Restored, repository().restore(ready(), p))
            assertTrue(db.spoolDao().getOwnedSpools("a").first().isEmpty())
            assertNotNull(recovery.source("a", "deleted:device:$copiedUuid"))
        }
    }

    @Test fun failedHistoryInsertRollsBackCopyReceiptAndOutbox() = runBlocking {
        fixture {
            seedDeleted(); val p = preview(); val pending = db.spoolDao().pendingInventoryChanges("a")
            sql("CREATE TRIGGER reject_recovery BEFORE INSERT ON usage_log BEGIN SELECT RAISE(ABORT,'test failure'); END")
            assertEquals(InventoryRecoveryResult.Unavailable, repository().restore(ready(), p))
            assertNull(recovery.source("a", sourceId)!!.restoredUuid)
            assertEquals(pending, db.spoolDao().pendingInventoryChanges("a"))
            assertTrue(db.spoolDao().getOwnedSpools("a").first().isEmpty())
            sql("DROP TRIGGER reject_recovery")
            assertEquals(InventoryRecoveryResult.Restored, repository().restore(ready(), p))
        }
    }

    @Test fun accountSwitchGuestAndForeignSourceCannotReadOrRestoreAnotherOwnersCopies() = runBlocking {
        fixture {
            seedDeleted(); val p = preview(); val old = ready()
            accounts.sessions.value = AccountSession(SpoolAccount("b", null, null)); val b = ready("b")
            assertTrue(repository().observe(b).first().isEmpty())
            assertNull(repository().preview(b, sourceId))
            assertEquals(InventoryRecoveryResult.StaleScope, repository().restore(old, p))
            assertEquals(InventoryRecoveryResult.StaleScope, repository().restore(b, p))
            accounts.signOut(); val guest = ready("")
            assertTrue(repository().observe(guest).first().isEmpty())
            assertNull(repository().preview(guest, sourceId))
            assertNull(recovery.source("a", sourceId)!!.restoredUuid)
        }
    }

    @Test fun changedOrCorruptSourceAndTamperedPreviewNeverCreateCopies() = runBlocking {
        fixture {
            seedDeleted(); val p = preview()
            val fake = p.copy(version = p.version.copy(spool = p.version.spool.copy(currentWeight = 1.0)))
            assertEquals(InventoryRecoveryResult.Unavailable, repository().restore(ready(), fake))
            sql("UPDATE inventory_recovery_versions SET created_at=created_at+1")
            assertEquals(InventoryRecoveryResult.Changed, repository().restore(ready(), p))
            sql("UPDATE inventory_recovery_versions SET snapshot='invalid'")
            assertNull(repository().preview(ready(), sourceId))
            assertNull(recovery.source("a", sourceId)!!.restoredUuid)
            assertTrue(db.spoolDao().getOwnedSpools("a").first().isEmpty())
        }
    }

    @Test fun cloudDeletionIsRetainedAtomicallyAndRestoresOfflineWithoutTouchingCursorOrTombstone() = runBlocking {
        fixture {
            ready()
            val cloud = snapshot(deleted = true)
            val text = json.encodeToString(RemoteInventorySnapshot.serializer(), cloud)
            val downloader = InventoryDownloader(sync, session, object : InventoryPullRemote {
                override suspend fun pull(ownerId: String, cursor: Long) = InventoryPullReply.Page(InventoryPullPage("a", 0, 10, false, cloud))
            })
            assertEquals(InventoryUploadResult.NoWork, downloader.downloadNext(ready()))
            val p = preview("deleted:cloud:$uuid")
            assertEquals(1, p.version.logs.size)
            assertEquals(InventoryRecoveryResult.Restored, repository().restore(ready(), p))
            assertEquals(10L, sync.pullCursor("a"))
            assertEquals(1L, sync.serverRevision("a", uuid))
            assertNull(sync.spoolByUuid(uuid))
            assertEquals(2, db.spoolDao().pendingInventoryChanges("a").size)
            val older = p.source.snapshot
            sync.applyPull(InventoryPullPage("a", 10, 11, false, cloud), "{}", text)
            assertEquals(older, recovery.source("a", p.source.id)!!.snapshot)
            assertEquals(1, repository().observe(ready()).first().size)
        }
    }

    @Test fun cloudDeletionCacheRollsBackWithRejectedInventoryApplication() = runBlocking {
        fixture {
            ready()
            db.spoolDao().insertSpool(Filament(brand = "Other", material = "PLA", colorName = "", colorHex = 0, note = "", uuid = uuid, ownerId = "b"))
            val cloud = snapshot(deleted = true)
            assertFailsWith<IllegalArgumentException> {
                sync.applyPull(InventoryPullPage("a", 0, 10, false, cloud), "{}", json.encodeToString(RemoteInventorySnapshot.serializer(), cloud))
            }
            assertNull(recovery.source("a", "deleted:cloud:$uuid"))
            assertNull(sync.pullCursor("a"))
            assertEquals("b", sync.spoolByUuid(uuid)!!.ownerId)
        }
    }

    @Test fun bothSavedConflictVersionsRestoreAndLiveCloudVersionExcludesDeletedPrints() = runBlocking {
        fixture {
            ready(); sync.applyRemoteSnapshot("a", snapshot())
            db.spoolDao().updateOwnedWeight("a", sync.spoolByUuid(uuid)!!.id, 850.0)
            val batch = sync.prepareSpool("a", uuid); sync.saveConflict("a", uuid, batch.batchId, "{}")
            val cloud = snapshot().copy(spool = snapshot().spool.copy(revision = 2, currentWeight = 800.0),
                logs = snapshot().logs + snapshot().logs.single().copy(uuid = "20000000-0000-4000-8000-000000000002", deletedAt = "older"))
            val resolver = InventoryConflictResolver(sync, session, object : InventoryConflictRemote {
                override suspend fun snapshot(ownerId: String, spoolUuid: String) = InventorySnapshotReply.Snapshot(cloud)
            })
            val r = assertIs<InventoryConflictReviewResult.Ready>(resolver.review(ready(), uuid)).review
            assertEquals(InventoryConflictResolveResult.Resolved, resolver.resolve(r, InventoryConflictChoice.Cloud))
            val items = repository().observe(ready()).first(); assertEquals(2, items.size)
            for (item in items) {
                val p = preview(item.id)
                assertEquals(1, p.version.logs.size)
                assertEquals(if (item.kind == "conflict_local") 850.0 else 800.0, p.version.spool.currentWeight)
                assertEquals(InventoryRecoveryResult.Restored, repository().restore(ready(), p))
            }
            assertEquals(3, db.spoolDao().getOwnedSpools("a").first().size)
            assertEquals(800.0, sync.spoolByUuid(uuid)!!.currentWeight)
            assertEquals(1, sync.resolutions("a").size)
        }
    }

    @Test fun restoredCopyUploadsNormallyWithoutReusingDeletedUuid() = runBlocking {
        fixture {
            seedDeleted(); repository().restore(ready(), preview())
            val requests = mutableListOf<JsonObject>()
            val uploader = InventoryUploader(sync, session, object : InventoryUploadRemote {
                override suspend fun upload(ownerId: String, request: String): InventoryUploadReply {
                    val value = Json.parseToJsonElement(request).jsonObject; requests += value
                    return InventoryUploadReply.Applied(value.getValue("p_base_revision").jsonPrimitive.long + 1)
                }
            })
            assertEquals(InventoryUploadResult.Applied, uploader.uploadNext(ready()))
            assertEquals(InventoryUploadResult.Applied, uploader.uploadNext(ready()))
            assertEquals(InventoryUploadResult.NoWork, uploader.uploadNext(ready()))
            assertTrue(requests.first().getValue("p_deleted").jsonPrimitive.boolean)
            assertFalse(requests.last().getValue("p_deleted").jsonPrimitive.boolean)
            assertNotEquals(uuid, requests.last().getValue("p_spool_uuid").jsonPrimitive.content)
            assertTrue(db.spoolDao().pendingInventoryChanges("a").all { it.acknowledgedAt != null })
        }
    }

    @Test fun recoveryCaptureFailureKeepsOriginalInventoryAndDoesNotQueueDeletion() = runBlocking {
        fixture {
            ready(); sync.applyRemoteSnapshot("a", snapshot())
            sql("CREATE TRIGGER reject_archive BEFORE INSERT ON inventory_recovery_versions BEGIN SELECT RAISE(ABORT,'test failure'); END")
            assertFailsWith<Exception> { sync.deleteRemoteSpool("a", uuid) }
            assertNotNull(sync.spoolByUuid(uuid))
            assertNotNull(sync.logByUuid(logUuid))
            assertTrue(db.spoolDao().pendingInventoryChanges("a").isEmpty())
            assertTrue(repository().observe(ready()).first().isEmpty())
        }
    }

    @Test fun savedLocallyDeletedConflictReconstructsItsRetainedHistory() = runBlocking {
        fixture {
            seedDeleted()
            val batch = sync.prepareSpool("a", uuid); sync.saveConflict("a", uuid, batch.batchId, "{}")
            val cloud = snapshot().copy(spool = snapshot().spool.copy(revision = 2, currentWeight = 800.0))
            val resolver = InventoryConflictResolver(sync, session, object : InventoryConflictRemote {
                override suspend fun snapshot(ownerId: String, spoolUuid: String) = InventorySnapshotReply.Snapshot(cloud)
            })
            val review = assertIs<InventoryConflictReviewResult.Ready>(resolver.review(ready(), uuid)).review
            assertEquals(InventoryConflictResolveResult.Resolved, resolver.resolve(review, InventoryConflictChoice.Cloud))
            val item = repository().observe(ready()).first().single { it.kind == "conflict_local" }
            val copy = preview(item.id)
            assertEquals(900.0, copy.version.spool.currentWeight)
            assertEquals("Print", copy.version.logs.single().title)
            assertEquals(InventoryRecoveryResult.Restored, repository().restore(ready(), copy))
            assertEquals(800.0, sync.spoolByUuid(uuid)!!.currentWeight)
            assertEquals(2, db.spoolDao().getOwnedSpools("a").first().size)
        }
    }
    @Test fun removingRestoredDeviceCopyKeepsInventoryReceiptsAndPendingWorkAcrossRestart() = runBlocking {
        fixture {
            seedDeleted(); val original = preview(); val scope = ready()
            repository().restore(scope, original)
            val copy = db.spoolDao().getOwnedSpools("a").first().single()
            val logs = sync.logsForSpool(copy.id)
            val pending = db.spoolDao().pendingInventoryChanges("a")
            val batch = sync.prepareSpool("a", uuid)
            assertEquals(InventoryRecoveryRemovalResult.Removed, repository().remove(scope, sourceId))
            assertNull(recovery.source("a", sourceId)); assertTrue(recovery.isRemoved("a", sourceId))
            BundledSQLiteDriver().open(path).use { connection ->
                connection.prepare("SELECT spool_uuid FROM inventory_recovery_restores WHERE owner_id='a'").use {
                    assertTrue(it.step()); assertEquals(copy.uuid, it.getText(0))
                }
            }
            assertEquals(pending, db.spoolDao().pendingInventoryChanges("a"))
            assertEquals(batch, sync.batch("a", uuid))
            assertEquals(copy, db.spoolDao().getOwnedSpools("a").first().single())
            assertEquals(logs, sync.logsForSpool(copy.id))
            assertEquals(InventoryRecoveryResult.Changed, repository().restore(scope, original))
            assertEquals(InventoryRecoveryRemovalResult.Removed, repository().remove(scope, sourceId))
            restart()
            assertTrue(repository().observe(ready()).first().isEmpty())
            // Re-capturing the same source is suppressed without blocking the active copy's own deletion.
            sync.deleteRemoteSpool("a", copy.uuid)
            assertNotNull(recovery.source("a", "deleted:device:${copy.uuid}"))
            assertTrue(recovery.isRemoved("a", sourceId))
        }
    }

    @Test fun removedCloudCopyCannotReturnOnRepeatedPullAndDoesNotChangeRemoteRevisionOrCursor() = runBlocking {
        fixture {
            val scope = ready(); val cloud = snapshot(deleted = true)
            val text = json.encodeToString(RemoteInventorySnapshot.serializer(), cloud)
            val id = "deleted:cloud:$uuid"
            sync.applyPull(InventoryPullPage("a", 0, 10, false, cloud), "{}", text)
            assertEquals(InventoryRecoveryRemovalResult.Removed, repository().remove(scope, id))
            assertEquals(10L, sync.pullCursor("a")); assertEquals(1L, sync.serverRevision("a", uuid))
            assertTrue(db.spoolDao().pendingInventoryChanges("a").isEmpty())
            restart()
            sync.applyPull(InventoryPullPage("a", 10, 11, false, cloud), "{}", text)
            assertNull(recovery.source("a", id)); assertEquals(11L, sync.pullCursor("a"))
            assertTrue(repository().observe(ready()).first().isEmpty())
            sync.saveDeletedCloud("b", uuid, text, 100)
            assertNotNull(recovery.source("b", id)); assertFalse(recovery.isRemoved("b", id))
        }
    }

    @Test fun removingConflictSidesClearsOnlyChosenPayloadAndKeepsOtherVersionRestorable() = runBlocking {
        fixture {
            val scope = ready(); sync.applyRemoteSnapshot("a", snapshot())
            val text = json.encodeToString(RemoteInventorySnapshot.serializer(), snapshot())
            sync.archiveResolution(InventoryConflictResolution("archive", "a", uuid, "cloud", text, text, 123))
            val local = "conflict:archive:local"; val cloud = "conflict:archive:cloud"
            assertEquals(InventoryRecoveryRemovalResult.Removed, repository().remove(scope, local))
            val archive = sync.resolutions("a").single()
            assertEquals("{}", archive.localSnapshot); assertEquals(text, archive.cloudSnapshot)
            assertNull(recovery.source("a", local)); assertEquals(cloud, repository().observe(scope).first().single().id)
            assertEquals(InventoryRecoveryResult.Restored, repository().restore(scope, preview(cloud)))
            val spools = db.spoolDao().getOwnedSpools("a").first(); val pending = db.spoolDao().pendingInventoryChanges("a")
            assertEquals(InventoryRecoveryRemovalResult.Removed, repository().remove(scope, cloud))
            assertEquals("{}", sync.resolutions("a").single().cloudSnapshot)
            assertEquals(spools, db.spoolDao().getOwnedSpools("a").first())
            assertEquals(pending, db.spoolDao().pendingInventoryChanges("a"))
            assertTrue(repository().observe(scope).first().isEmpty())
        }
    }

    @Test fun guestOtherAccountAndStaleSessionCannotRemoveCopiesOrAddExclusions() = runBlocking {
        fixture {
            seedDeleted(); val a = ready()
            accounts.sessions.value = AccountSession(SpoolAccount("b", null, null)); val b = ready("b")
            assertEquals(InventoryRecoveryRemovalResult.StaleScope, repository().remove(a, sourceId))
            assertEquals(InventoryRecoveryRemovalResult.Unavailable, repository().remove(b, sourceId))
            assertFalse(recovery.isRemoved("a", sourceId)); assertFalse(recovery.isRemoved("b", sourceId))
            accounts.signOut(); val guest = ready("")
            assertEquals(InventoryRecoveryRemovalResult.StaleScope, repository().remove(guest, sourceId))
            assertNotNull(recovery.source("a", sourceId))
        }
    }

    @Test fun failedRemovalRollsBackExclusionAndRetainsSnapshotAndQueue() = runBlocking {
        fixture {
            seedDeleted(); val before = preview(); val pending = db.spoolDao().pendingInventoryChanges("a")
            sql("CREATE TRIGGER reject_remove BEFORE DELETE ON inventory_recovery_versions BEGIN SELECT RAISE(ABORT,'test failure'); END")
            assertEquals(InventoryRecoveryRemovalResult.Unavailable, repository().remove(ready(), sourceId))
            assertFalse(recovery.isRemoved("a", sourceId)); assertEquals(before.source, recovery.source("a", sourceId))
            assertEquals(pending, db.spoolDao().pendingInventoryChanges("a"))
            sql("DROP TRIGGER reject_remove")
            assertEquals(InventoryRecoveryRemovalResult.Removed, repository().remove(ready(), sourceId))
        }
    }

    @Test fun corruptSavedCopyCanBeRemovedWithoutDecodingOrTouchingSyncData() = runBlocking {
        fixture {
            seedDeleted(); val pending = db.spoolDao().pendingInventoryChanges("a")
            sql("UPDATE inventory_recovery_versions SET snapshot='invalid'")
            assertNull(repository().preview(ready(), sourceId))
            assertEquals(InventoryRecoveryRemovalResult.Removed, repository().remove(ready(), sourceId))
            assertNull(recovery.source("a", sourceId)); assertTrue(recovery.isRemoved("a", sourceId))
            assertEquals(pending, db.spoolDao().pendingInventoryChanges("a"))
        }
    }

    @Test fun suppressingRecaptureDoesNotBlockSubsequentInventoryDeletionOrItsOutbox() = runBlocking {
        fixture {
            seedDeleted(); repository().remove(ready(), sourceId)
            // A conflict can restore the original live cloud UUID after an earlier local deletion.
            sync.applyRemoteSnapshot("a", snapshot())
            val count = db.spoolDao().pendingInventoryChanges("a").size
            sync.deleteRemoteSpool("a", uuid)
            assertNull(sync.spoolByUuid(uuid)); assertNull(sync.logByUuid(logUuid))
            assertEquals(count + 2, db.spoolDao().pendingInventoryChanges("a").size)
            assertNull(recovery.source("a", sourceId)); assertTrue(recovery.isRemoved("a", sourceId))
        }
    }

}
