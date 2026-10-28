package com.aadil.spool.core.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.aadil.spool.core.data.repository.InventorySession
import com.aadil.spool.core.data.sync.InventoryUploadCoordinator
import com.aadil.spool.core.data.sync.InventoryUploadMode
import com.aadil.spool.core.data.sync.InventoryUploadScheduler
import com.aadil.spool.core.data.repository.InventoryScope
import com.aadil.spool.core.data.sync.InventoryUploader
import com.aadil.spool.core.data.sync.InventoryUploadResult
import com.aadil.spool.core.network.AccountRepository
import com.aadil.spool.core.network.AccountSession
import com.aadil.spool.core.network.GoogleCredentials
import com.aadil.spool.core.network.InventoryUploadRemote
import com.aadil.spool.core.network.InventoryUploadReply
import com.aadil.spool.core.network.SpoolAccount
import com.aadil.spool.data.SpoolDatabase
import com.aadil.spool.data.SpoolDatabaseConstructor
import com.aadil.spool.data.entity.Filament
import com.aadil.spool.data.entity.UsageLog
import com.aadil.spool.data.getSpoolDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)
class InventoryUploaderTest {
    private class Accounts : AccountRepository {
        override val googleWebClientId = ""
        override val sessions = MutableStateFlow(AccountSession(SpoolAccount("a", null, null)))
        override suspend fun signIn(credentials: GoogleCredentials) = Unit
        override suspend fun signOut() { sessions.value = AccountSession() }
    }
    private class Remote : InventoryUploadRemote {
        val requests = mutableListOf<Pair<String, String>>()
        val requestCount = MutableStateFlow(0)
        var response: InventoryUploadReply = InventoryUploadReply.RetryLater
        var handler: (suspend () -> InventoryUploadReply)? = null
        override suspend fun upload(ownerId: String, request: String): InventoryUploadReply {
            requests += ownerId to request
            requestCount.value = requests.size
            return handler?.invoke() ?: response
        }
    }
    private class Scheduler : InventoryUploadScheduler {
        override val networkAvailable = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val scheduled = MutableStateFlow(emptyList<InventoryScope>())
        val cancelled = MutableStateFlow(emptyList<InventoryScope>())
        var failScheduling = false
        override fun schedule(scope: InventoryScope) {
            check(!failScheduling)
            scheduled.update { it + scope }
        }
        override fun cancel(scope: InventoryScope) { cancelled.update { it + scope } }
    }
    private class Fixture(val path: String) {
        fun open() = getSpoolDatabase(Room.databaseBuilder<SpoolDatabase>(name = path, factory = { SpoolDatabaseConstructor.initialize() }))
        var db = open()
        val dao get() = db.spoolDao()
        val sync get() = db.inventorySyncDao()
        val accounts = Accounts()
        var session = InventorySession(accounts, dao)
        val remote = Remote()
        val scheduler = Scheduler()
        private val coordinators = mutableListOf<InventoryUploadCoordinator>()
        fun coordinator() = InventoryUploadCoordinator(sync, session, uploader(), accounts, scheduler).also {
            coordinators += it
            it.start()
        }
        fun succeed() {
            remote.handler = {
                val base = Json.parseToJsonElement(remote.requests.last().second).jsonObject.getValue("p_base_revision").jsonPrimitive.content.toLong()
                InventoryUploadReply.Applied(base + 1)
            }
        }
        fun uploader() = InventoryUploader(sync, session, remote)
        suspend fun ready(owner: String = "a") = withTimeout(5000) { session.scope.first { it?.ownerId == owner }!! }
        suspend fun restart() {
            coordinators.forEach { it.close() }
            coordinators.clear()
            session.close()
            db.close()
            db = open()
            session = InventorySession(accounts, dao)
            ready()
        }
        fun close() { coordinators.forEach { it.close() }; session.close(); db.close() }
    }
    private suspend fun withDatabase(block: suspend Fixture.() -> Unit) {
        val path = NSTemporaryDirectory() + "spool-upload-" + Uuid.random() + ".db"
        val fixture = Fixture(path)
        try { withTimeout(20_000) { fixture.block() } } finally {
            fixture.close()
            listOf("", "-wal", "-shm").forEach { NSFileManager.defaultManager.removeItemAtPath(path + it, null) }
        }
    }
    private fun spool(owner: String = "a") = Filament(ownerId = owner, brand = "Upload", material = "PLA",
        colorName = "Black", colorHex = 1, totalWeight = 1000.0, currentWeight = 900.0, note = "", price = 20.0)
    private fun log(id: Int) = UsageLog(spoolId = id, title = "Print", gramsUsed = 10.0, timestamp = 123, isFailure = false, pricePerPrint = .2)

    @Test fun frozenBatchSurvivesRestartAndOnlyExactAcknowledgedOperationsAreRemoved() = runBlocking {
        withDatabase {
            val scope = ready()
            dao.insertOwnedSpool("a", spool())
            val parent = dao.getOwnedSpools("a").first().single()
            dao.insertOwnedLogAndDeductWeight("a", log(parent.id))
            val originalQueue = dao.pendingInventoryChanges("a")
            assertEquals(InventoryUploadResult.RetryLater, uploader().uploadNext(scope))
            val frozen = sync.batch("a", parent.uuid)!!
            val envelope = Json.parseToJsonElement(frozen.request).jsonObject
            assertEquals("890.0", envelope.getValue("p_spool").jsonObject.getValue("current_weight").jsonPrimitive.content)
            assertEquals(1, envelope.getValue("p_logs").jsonArray.size)
            dao.updateOwnedWeight("a", parent.id, 800.0)
            restart()
            assertEquals(frozen, sync.batch("a", parent.uuid))
            remote.response = InventoryUploadReply.Applied(1)
            assertEquals(InventoryUploadResult.Applied, uploader().uploadNext(ready()))
            assertEquals(remote.requests.first().second, remote.requests.last().second)
            assertEquals(1, dao.pendingInventoryChanges("a").size)
            assertTrue(dao.pendingInventoryChanges("a").none { newer -> originalQueue.any { it.operationId == newer.operationId } })
            assertEquals(1L, sync.serverRevision("a", parent.uuid))
            val next = sync.prepare("a")!!
            assertEquals(1L, next.baseRevision)
            assertNotEquals(frozen.batchId, next.batchId)
            assertEquals("800.0", Json.parseToJsonElement(next.request).jsonObject.getValue("p_spool").jsonObject.getValue("current_weight").jsonPrimitive.content)
        }
    }

    @Test fun localWritesContinueWhileNetworkWaitsAndRemainQueuedAfterAcknowledgement() = runBlocking {
        withDatabase {
            val scope = ready()
            dao.insertOwnedSpool("a", spool())
            val parent = dao.getOwnedSpools("a").first().single()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            remote.handler = { entered.complete(Unit); release.await(); InventoryUploadReply.Applied(1) }
            val uploading = async { uploader().uploadNext(scope) }
            entered.await()
            withTimeout(2000) { session.withScope(scope, Unit) { dao.updateOwnedWeight("a", parent.id, 700.0) } }
            release.complete(Unit)
            assertEquals(InventoryUploadResult.Applied, uploading.await())
            assertEquals(700.0, dao.ownedSpool("a", parent.id)!!.currentWeight)
            assertEquals(1, dao.pendingInventoryChanges("a").size)
        }
    }

    @Test fun accountSwitchCancelsUploadKeepsOldBatchAndNeverSendsItForNewAccount() = runBlocking {
        withDatabase {
            val a = ready()
            dao.insertOwnedSpool("a", spool())
            val parent = dao.getOwnedSpools("a").first().single()
            val entered = CompletableDeferred<Unit>()
            val gate = CompletableDeferred<Unit>()
            remote.handler = { entered.complete(Unit); gate.await(); InventoryUploadReply.Applied(1) }
            val service = uploader()
            val sending = async { service.uploadNext(a) }
            entered.await()
            accounts.sessions.value = AccountSession(SpoolAccount("b", null, null))
            val b = ready("b")
            assertEquals(InventoryUploadResult.StaleScope, sending.await())
            assertEquals(InventoryUploadResult.StaleScope, service.uploadNext(a))
            assertEquals(InventoryUploadResult.NoWork, service.uploadNext(b))
            assertTrue(remote.requests.all { it.first == "a" })
            assertNotNull(sync.batch("a", parent.uuid))
            assertEquals(1, dao.pendingInventoryChanges("a").size)
            assertNull(sync.serverRevision("a", parent.uuid))
        }
    }

    @Test fun conflictRetainsBothSnapshotsAcrossRestartAndDoesNotBlockAnotherSpool() = runBlocking {
        withDatabase {
            val scope = ready()
            dao.insertOwnedSpool("a", spool())
            val parent = dao.getOwnedSpools("a").first().single()
            val before = dao.pendingInventoryChanges("a")
            remote.response = InventoryUploadReply.Conflict("""{"server":{"note":"Remote version"}}""")
            assertEquals(InventoryUploadResult.Conflict, uploader().uploadNext(scope))
            val conflict = sync.batch("a", parent.uuid)!!
            assertNotNull(conflict.conflictSnapshot)
            assertEquals(before, dao.pendingInventoryChanges("a"))
            restart()
            assertEquals(conflict, sync.batch("a", parent.uuid))
            assertEquals(InventoryUploadResult.Conflict, uploader().uploadNext(ready()))
            assertEquals(1, remote.requests.size)
            dao.insertOwnedSpool("a", spool())
            remote.response = InventoryUploadReply.Applied(1)
            assertEquals(InventoryUploadResult.Applied, uploader().uploadNext(ready()))
            assertEquals(before, dao.pendingInventoryChanges("a"))
            assertEquals(1, sync.conflictCount("a"))
        }
    }

    @Test fun acknowledgementsAreAtomicAndDeletedSnapshotsAreRetained() = runBlocking {
        withDatabase {
            ready()
            dao.insertOwnedSpool("a", spool())
            val parent = dao.getOwnedSpools("a").first().single()
            dao.insertOwnedLog("a", log(parent.id))
            dao.deleteOwnedSpool("a", parent)
            val batch = sync.prepare("a")!!
            val envelope = Json.parseToJsonElement(batch.request).jsonObject
            assertEquals("true", envelope.getValue("p_deleted").jsonPrimitive.content)
            assertEquals("delete", envelope.getValue("p_logs").jsonArray.single().jsonObject.getValue("operation").jsonPrimitive.content)
            val before = dao.pendingInventoryChanges("a")
            BundledSQLiteDriver().open(path).use {
                it.execSQL("CREATE TRIGGER reject_ack BEFORE DELETE ON inventory_upload_batches BEGIN SELECT RAISE(ABORT, 'Ack failure'); END")
            }
            assertFails { sync.acknowledge(batch, 2) }
            assertEquals(before, dao.pendingInventoryChanges("a"))
            assertNull(sync.serverRevision("a", parent.uuid))
            assertEquals(batch, sync.batch("a", parent.uuid))
            BundledSQLiteDriver().open(path).use { it.execSQL("DROP TRIGGER reject_ack") }
            sync.acknowledge(batch, 2)
            assertTrue(dao.pendingInventoryChanges("a").isEmpty())
            assertNull(sync.batch("a", parent.uuid))
            assertEquals(2L, sync.serverRevision("a", parent.uuid))
            BundledSQLiteDriver().open(path).use { connection ->
                connection.prepare("SELECT COUNT(*) FROM pending_inventory_changes WHERE operation = 'delete' AND acknowledged_at IS NOT NULL").use {
                    assertTrue(it.step())
                    assertEquals(2L, it.getLong(0))
                }
            }
            assertFails { sync.acknowledge(batch, 2) }
        }
    }

    @Test fun aggregateIncludesHistoryBeyondQueuePageLimitAndKeepsLatestSnapshotPerLog() = runBlocking {
        withDatabase {
            ready()
            dao.insertOwnedSpool("a", spool())
            val parent = dao.getOwnedSpools("a").first().single()
            repeat(120) { dao.insertOwnedLog("a", log(parent.id)) }
            val history = dao.getOwnedUsage("a", parent.id).first().first()
            dao.updateOwnedLog("a", history.copy(title = "Latest"), false)
            val batch = sync.prepare("a")!!
            val envelope = Json.parseToJsonElement(batch.request).jsonObject
            assertEquals(120, envelope.getValue("p_logs").jsonArray.size)
            assertEquals(122, Json.parseToJsonElement(batch.operationIds).jsonArray.size)
            assertTrue(envelope.getValue("p_logs").jsonArray.any { it.jsonObject.getValue("payload").jsonObject.getValue("title").jsonPrimitive.content == "Latest" })
            sync.acknowledge(batch, 1)
            assertTrue(dao.pendingInventoryChanges("a").isEmpty())
        }
    }

    @Test fun guestAndMissingAuthenticationNeverClearPendingWork() = runBlocking {
        withDatabase {
            val scope = ready()
            dao.insertOwnedSpool("a", spool())
            val before = dao.pendingInventoryChanges("a")
            remote.response = InventoryUploadReply.NotAuthenticated
            assertEquals(InventoryUploadResult.NotAuthenticated, uploader().uploadNext(scope))
            assertEquals(before, dao.pendingInventoryChanges("a"))
            remote.response = InventoryUploadReply.Rejected
            assertEquals(InventoryUploadResult.Rejected, uploader().uploadNext(scope))
            assertEquals(before, dao.pendingInventoryChanges("a"))
            accounts.signOut()
            val guest = ready("")
            assertEquals(InventoryUploadResult.StaleScope, uploader().uploadNext(guest))
            assertEquals(before, dao.pendingInventoryChanges("a"))
        }
    }
    @Test fun coordinatorObservesCommittedTriggerWritesAndRunsBackgroundWorkWithoutForeground() = runBlocking {
        withDatabase {
            val active = ready()
            val coordinator = coordinator()
            withTimeout(5000) { coordinator.status.first { it.scope == active && it.mode == InventoryUploadMode.Idle } }
            dao.insertOwnedSpool("a", spool())
            withTimeout(5000) { coordinator.status.first { it.queue.pending == 1 && it.queue.ready == 1 } }
            assertTrue(scheduler.scheduled.value.contains(active))
            assertEquals(0, remote.requests.size)
            succeed()
            assertEquals(InventoryUploadResult.NoWork, coordinator.runPending(active))
            withTimeout(5000) { coordinator.status.first { it.queue.pending == 0 && it.mode == InventoryUploadMode.Idle } }
            assertEquals(1, remote.requests.size)
            assertEquals(1L, sync.serverRevision("a", dao.getOwnedSpools("a").first().single().uuid))
        }
    }

    @Test fun coordinatorUploadsNewForegroundEditsAndNetworkRecoveryReusesFailedBatch() = runBlocking {
        withDatabase {
            ready()
            dao.insertOwnedSpool("a", spool())
            val parent = dao.getOwnedSpools("a").first().single()
            val coordinator = coordinator()
            coordinator.setForeground(true)
            withTimeout(5000) { coordinator.status.first { it.mode == InventoryUploadMode.Waiting } }
            val initialBatch = sync.batch("a", parent.uuid)!!
            succeed()
            scheduler.networkAvailable.emit(Unit)
            withTimeout(5000) { coordinator.status.first { it.queue.pending == 0 && it.mode == InventoryUploadMode.Idle } }
            assertTrue(remote.requests.all { it.second == initialBatch.request })
            val sent = remote.requests.size
            dao.updateOwnedWeight("a", parent.id, 700.0)
            withTimeout(5000) { remote.requestCount.first { it > sent } }
            withTimeout(5000) { coordinator.status.first { it.queue.pending == 0 && it.mode == InventoryUploadMode.Idle } }
            assertEquals(2L, sync.serverRevision("a", parent.uuid))
            assertEquals(700.0, dao.ownedSpool("a", parent.id)!!.currentWeight)
        }
    }

    @Test fun pausingStopsForegroundRequestButBackgroundCanResumeItsFrozenBatch() = runBlocking {
        withDatabase {
            val active = ready()
            dao.insertOwnedSpool("a", spool())
            val parent = dao.getOwnedSpools("a").first().single()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            remote.handler = { entered.complete(Unit); release.await(); InventoryUploadReply.Applied(1) }
            val coordinator = coordinator()
            coordinator.setForeground(true)
            entered.await()
            val batch = sync.batch("a", parent.uuid)!!
            coordinator.setForeground(false)
            withTimeout(5000) { coordinator.status.first { it.mode == InventoryUploadMode.Idle } }
            assertEquals(1, dao.pendingInventoryChanges("a").size)
            assertEquals(batch, sync.batch("a", parent.uuid))
            succeed()
            assertEquals(InventoryUploadResult.NoWork, coordinator.runPending(active))
            assertTrue(remote.requests.all { it.second == batch.request })
            assertTrue(dao.pendingInventoryChanges("a").isEmpty())
        }
    }

    @Test fun signOutCancelsScheduledWorkAndNewAccountCannotUploadOrDisplayOldPendingWork() = runBlocking {
        withDatabase {
            val a = ready()
            dao.insertOwnedSpool("a", spool())
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            remote.handler = { entered.complete(Unit); release.await(); InventoryUploadReply.Applied(1) }
            val coordinator = coordinator()
            coordinator.setForeground(true)
            entered.await()
            accounts.signOut()
            val guest = ready("")
            withTimeout(5000) { coordinator.status.first { it.scope == guest } }
            withTimeout(5000) { scheduler.cancelled.first { it.contains(a) } }
            assertEquals(0, coordinator.status.value.queue.pending)
            assertEquals(1, dao.pendingInventoryChanges("a").size)
            succeed()
            accounts.sessions.value = AccountSession(SpoolAccount("b", null, null))
            val b = ready("b")
            withTimeout(5000) { coordinator.status.first { it.scope == b && it.mode == InventoryUploadMode.Idle } }
            assertEquals(0, coordinator.status.value.queue.pending)
            assertEquals(1, remote.requests.size)
            dao.insertOwnedSpool("b", spool("b"))
            withTimeout(5000) { remote.requestCount.first { it > 1 } }
            withTimeout(5000) { coordinator.status.first { it.scope == b && it.queue.pending == 0 && it.mode == InventoryUploadMode.Idle } }
            assertEquals("b", remote.requests.last().first)
            assertEquals(1, dao.pendingInventoryChanges("a").size)
            assertEquals(InventoryUploadResult.StaleScope, coordinator.runPending(a))
        }
    }

    @Test fun conflictCountsRefreshAndOtherSpoolsContinueAutomatically() = runBlocking {
        withDatabase {
            ready()
            dao.insertOwnedSpool("a", spool())
            remote.response = InventoryUploadReply.Conflict("""{"server":{"note":"Other device"}}""")
            val coordinator = coordinator()
            coordinator.setForeground(true)
            withTimeout(5000) { coordinator.status.first { it.queue.conflicts == 1 && it.queue.ready == 0 && it.mode == InventoryUploadMode.Idle } }
            val calls = remote.requests.size
            scheduler.networkAvailable.emit(Unit)
            succeed()
            dao.insertOwnedSpool("a", spool())
            withTimeout(5000) { remote.requestCount.first { it > calls } }
            withTimeout(5000) { coordinator.status.first { it.queue.pending == 1 && it.queue.conflicts == 1 && it.queue.ready == 0 && it.mode == InventoryUploadMode.Idle } }
            assertEquals(1, sync.conflictCount("a"))
            assertEquals(1, dao.pendingInventoryChanges("a").size)
        }
    }

    @Test fun backgroundPassIsBoundedAndRestartReschedulesRemainingWork() = runBlocking {
        withDatabase {
            val active = ready()
            repeat(25) { dao.insertOwnedSpool("a", spool()) }
            succeed()
            val coordinator = coordinator()
            assertEquals(InventoryUploadResult.RetryLater, coordinator.runPending(active))
            assertEquals(5, dao.pendingInventoryChanges("a").size)
            restart()
            val resumed = coordinator()
            withTimeout(5000) { resumed.status.first { it.queue.pending == 5 } }
            assertTrue(scheduler.scheduled.value.isNotEmpty())
            assertEquals(InventoryUploadResult.NoWork, resumed.runPending(ready()))
            assertTrue(dao.pendingInventoryChanges("a").isEmpty())
            assertEquals(25, remote.requests.size)
        }
    }

    @Test fun refreshedSessionAndManualRetryWakeForegroundWorkWithoutChangingInventoryOwner() = runBlocking {
        withDatabase {
            val active = ready()
            dao.insertOwnedSpool("a", spool())
            remote.response = InventoryUploadReply.NotAuthenticated
            val coordinator = coordinator()
            coordinator.setForeground(true)
            withTimeout(5000) { coordinator.status.first { it.mode == InventoryUploadMode.SignInRequired } }
            accounts.sessions.value = accounts.sessions.value.copy(needsConnection = true)
            succeed()
            accounts.sessions.value = accounts.sessions.value.copy(needsConnection = false)
            coordinator.retry()
            withTimeout(5000) { coordinator.status.first { it.queue.pending == 0 && it.mode == InventoryUploadMode.Idle } }
            assertEquals(active, session.scope.value)
            assertTrue(remote.requests.size >= 2)
        }
    }

    @Test fun schedulingFailureDoesNotBlockLocalSavesOrForegroundUploads() = runBlocking {
        withDatabase {
            ready()
            scheduler.failScheduling = true
            val coordinator = coordinator()
            dao.insertOwnedSpool("a", spool())
            withTimeout(5000) { coordinator.status.first { it.queue.pending == 1 } }
            succeed()
            coordinator.setForeground(true)
            withTimeout(5000) { coordinator.status.first { it.queue.pending == 0 && it.mode == InventoryUploadMode.Idle } }
            assertEquals(1, dao.getOwnedSpools("a").first().size)
        }
    }

}
