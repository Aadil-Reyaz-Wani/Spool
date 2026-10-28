package com.aadil.spool.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import com.aadil.spool.core.data.repository.DefaultSpoolRepository
import com.aadil.spool.core.data.repository.InventoryNotifications
import com.aadil.spool.core.data.repository.InventorySession
import com.aadil.spool.core.network.AccountRepository
import com.aadil.spool.core.network.AccountSession
import com.aadil.spool.core.network.GoogleCredentials
import com.aadil.spool.core.network.SpoolAccount
import com.aadil.spool.data.SpoolDatabase
import com.aadil.spool.data.SpoolDatabaseConstructor
import com.aadil.spool.data.entity.Filament
import com.aadil.spool.data.entity.UsageLog
import com.aadil.spool.data.getSpoolDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)
class InventoryOwnershipTest {
    private class Accounts : AccountRepository {
        override val googleWebClientId = ""
        override val sessions = MutableStateFlow(AccountSession(initializing = true))
        override suspend fun signIn(credentials: GoogleCredentials) = Unit
        override suspend fun updateDisplayName(accountId: String, name: String) { error("Unexpected profile write") }
        override suspend fun signOut() { sessions.value = AccountSession() }
        fun login(id: String, email: String = "$id@example.com") {
            sessions.value = AccountSession(SpoolAccount(id, "Name", email))
        }
    }

    private class Fixture(val db: SpoolDatabase) {
        val dao = db.spoolDao()
        val accounts = Accounts()
        var cleared = 0
        val session = InventorySession(accounts, dao, object : InventoryNotifications {
            override fun clearInventoryNotifications() { cleared++ }
        })
        suspend fun switch(owner: String): DefaultSpoolRepository {
            if (owner.isEmpty()) accounts.signOut() else accounts.login(owner)
            val scope = withTimeout(5_000) { session.scope.first { it?.ownerId == owner }!! }
            return DefaultSpoolRepository(dao, session, scope)
        }
        suspend fun stored(owner: String): List<Filament> = dao.getOwnedSpools(owner).first()
    }

    private suspend fun withDatabase(block: suspend Fixture.() -> Unit) {
        val path = NSTemporaryDirectory() + "spool-ownership-${Uuid.random()}.db"
        val db = getSpoolDatabase(Room.databaseBuilder<SpoolDatabase>(name = path, factory = { SpoolDatabaseConstructor.initialize() }))
        val fixture = Fixture(db)
        try { withTimeout(20_000) { fixture.block() } }
        finally {
            fixture.session.close()
            db.close()
            listOf("", "-wal", "-shm").forEach { NSFileManager.defaultManager.removeItemAtPath(path + it, null) }
        }
    }

    private fun spool(brand: String) = Filament(brand = brand, material = "PLA", colorName = "Black", colorHex = 1, note = "Original", totalWeight = 1000.0, currentWeight = 900.0, price = 25.0)
    private fun log(id: Int) = UsageLog(spoolId = id, title = "Print", gramsUsed = 10.0, timestamp = 123, isFailure = false, pricePerPrint = .25)

    @Test fun explicitImportPreservesAllValuesAndHistoryAndNeverAutomaticallyClaimsGuestData() = runBlocking {
        withDatabase {
            val guest = switch("")
            guest.insertSpool(spool("Guest"))
            val original = guest.getAllSpools().first().single()
            guest.insertUsageLog(log(original.id))
            val history = guest.getSpoolUsage(original.id).first().single()
            val a = switch("account-a")
            assertTrue(a.getAllSpools().first().isEmpty())
            assertNull(a.getSpool(original.id).first())
            assertEquals(1, session.guestSpoolCount.first())
            assertEquals(original, stored("").single())
            assertTrue(session.importGuestInventory(session.scope.value!!))
            assertEquals(original.copy(ownerId = "account-a"), a.getAllSpools().first().single())
            assertEquals(listOf(history), a.getSpoolUsage(original.id).first())
            assertEquals(0, session.guestSpoolCount.first())
            assertTrue(session.importGuestInventory(session.scope.value!!))
            assertEquals(1, a.getAllSpools().first().size)
            assertTrue(switch("").getAllSpools().first().isEmpty())
            assertTrue(switch("account-b").getAllSpools().first().isEmpty())
            assertEquals(original.copy(ownerId = "account-a"), switch("account-a").getAllSpools().first().single())
        }
    }

    @Test fun importAddsToExistingAccountWithoutOverwritingEitherHistory() = runBlocking {
        withDatabase {
            val guest = switch("")
            guest.insertSpool(spool("Guest"))
            val guestSpool = guest.getAllSpools().first().single()
            guest.insertUsageLog(log(guestSpool.id))
            val guestLog = guest.getSpoolUsage(guestSpool.id).first().single()
            val account = switch("account-a")
            account.insertSpool(spool("Existing account"))
            val owned = account.getAllSpools().first().single()
            account.insertUsageLog(log(owned.id))
            val ownedLog = account.getSpoolUsage(owned.id).first().single()
            session.importGuestInventory(session.scope.value!!)
            assertEquals(setOf(guestSpool.copy(ownerId = "account-a"), owned), account.getAllSpools().first().toSet())
            assertEquals(listOf(guestLog), account.getSpoolUsage(guestSpool.id).first())
            assertEquals(listOf(ownedLog), account.getSpoolUsage(owned.id).first())
            assertEquals(0, session.guestSpoolCount.first())
        }
    }

    @Test fun accountSwitchBlocksAllReadsAndMutationsUsingStaleEntitiesOrIds() = runBlocking {
        withDatabase {
            val a = switch("account-a")
            a.insertSpool(spool("Private A"))
            val original = a.getAllSpools().first().single()
            a.insertUsageLog(log(original.id))
            val history = a.getSpoolUsage(original.id).first().single()
            val originalQueue = dao.pendingInventoryChanges("account-a")
            val b = switch("account-b")
            assertTrue(a.getAllSpools().first().isEmpty())
            assertNull(a.getSpool(original.id).first())
            assertTrue(b.getAllSpools().first().isEmpty())
            assertTrue(b.getSpoolUsage(original.id).first().isEmpty())
            assertNull(b.getUsageLogById(history.id).first())
            assertEquals(0.0, b.getCurrentWeight(original.id))
            assertEquals(0.0, b.getCurrentLogWeight(history.id))
            for (repo in listOf(a, b)) {
                repo.updateSpool(original.copy(note = "Wrong"))
                repo.updateSpoolAndUsageCosts(original.copy(price = 999.0))
                repo.deleteSpool(original)
                repo.updateCurrentWeight(original.id, 5.0)
                repo.updateCurrentWeightWithTare(original.id, 5.0, 1.0)
                repo.markAsDried(original.id, 5.0, 1.0, 888)
                assertFalse(repo.insertLogAndDeductWeight(log(original.id)))
                repo.insertUsageLog(log(original.id))
                repo.updateUsageLog(history.copy(title = "Wrong"))
                repo.deleteUsageLog(history)
                repo.deleteLogAndRestoreCurrentWeight(history)
                assertFalse(repo.editLogAndRestoreCurrentWeight(history.copy(gramsUsed = 500.0)))
                repo.updateAllUsageCosts(original.id, 500.0)
            }
            a.insertSpool(spool("Stale new draft"))
            b.insertSpool(spool("Private B"))
            assertEquals(originalQueue, dao.pendingInventoryChanges("account-a"))
            assertEquals(1, dao.pendingInventoryChanges("account-b").size)
            assertEquals(listOf(original), stored("account-a"))
            assertEquals(listOf(history), dao.getOwnedUsage("account-a", original.id).first())
            assertEquals(listOf("Private B"), b.getUniqueBrand().first())
            assertTrue(b.getSpoolsByBrand("Private A").first().isEmpty())
            assertTrue(b.getSpoolsByMaterialType("ABS").first().isEmpty())
            assertTrue(b.getSpoolsByColorHex(999).first().isEmpty())
            assertEquals(1, b.getUniqueMaterialType().first().size)
            assertEquals(1, b.getUniqueColorHex().first().size)
            assertTrue(switch("").getAllSpools().first().isEmpty())
            assertTrue(cleared >= 3)
        }
    }

    @Test fun authorizedInventoryEditsKeepWorkingWithinTheActiveAccount() = runBlocking {
        withDatabase {
            val repo = switch("account-a")
            repo.insertSpool(spool("Editable"))
            val original = repo.getAllSpools().first().single()
            repo.updateCurrentWeight(original.id, 890.0)
            repo.insertUsageLog(log(original.id))
            val print = repo.getSpoolUsage(original.id).first().single()
            repo.updateSpool(original.copy(note = "Edited", currentWeight = 890.0))
            val edited = repo.getSpool(original.id).first()!!
            repo.updateSpoolAndUsageCosts(edited.copy(price = 50.0))
            assertEquals(.5, repo.getUsageLogById(print.id).first()!!.pricePerPrint)
            assertTrue(repo.editLogAndRestoreCurrentWeight(print.copy(gramsUsed = 20.0, pricePerPrint = 1.0)))
            assertEquals(880.0, repo.getCurrentWeight(original.id))
            val updated = repo.getUsageLogById(print.id).first()!!
            repo.updateUsageLog(updated.copy(title = "Renamed"))
            assertEquals("Renamed", repo.getUsageLogById(print.id).first()!!.title)
            assertEquals(20.0, repo.getCurrentLogWeight(print.id))
            repo.updateAllUsageCosts(original.id, .1)
            assertEquals(2.0, repo.getUsageLogById(print.id).first()!!.pricePerPrint)
            repo.deleteLogAndRestoreCurrentWeight(updated)
            assertEquals(900.0, repo.getCurrentWeight(original.id))
            assertTrue(repo.getSpoolUsage(original.id).first().isEmpty())
            repo.insertUsageLog(log(original.id))
            repo.deleteUsageLog(repo.getSpoolUsage(original.id).first().single())
            assertTrue(repo.getSpoolUsage(original.id).first().isEmpty())
            repo.updateCurrentWeightWithTare(original.id, 800.0, 140.0)
            repo.markAsDried(original.id, 790.0, 141.0, 1234)
            val final = repo.getSpool(original.id).first()!!
            assertEquals(original.uuid, final.uuid)
            assertEquals("account-a", final.ownerId)
            assertEquals("Edited", final.note)
            assertEquals(790.0, final.currentWeight)
            assertEquals(141.0, final.dryBaselineTareGrams)
            assertEquals(1234L, final.lastDriedAt)
            repo.updateSpool(final.copy(ownerId = "account-b", note = "Wrong owner"))
            assertEquals(final, repo.getSpool(original.id).first())
            repo.insertUsageLog(log(original.id))
            repo.deleteSpool(final)
            assertTrue(repo.getAllSpools().first().isEmpty())
            assertTrue(repo.getSpoolUsage(original.id).first().isEmpty())
        }
    }

    @Test fun rejectedImportCannotMoveGuestInventoryIntoAnOldAccount() = runBlocking {
        withDatabase {
            switch("").insertSpool(spool("Guest"))
            switch("account-a")
            val oldScope = session.scope.value!!
            switch("account-b")
            assertFalse(session.importGuestInventory(oldScope))
            assertEquals(1, stored("").size)
            assertTrue(stored("account-a").isEmpty())
            assertTrue(stored("account-b").isEmpty())
            switch("")
            assertFalse(session.importGuestInventory(session.scope.value!!))
            assertEquals(1, stored("").size)
        }
    }

    @Test fun profileNameAndEmailChangesDoNotChangeOwnershipAndSameAccountLoginRejectsOldRepository() = runBlocking {
        withDatabase {
            val a = switch("account-a")
            a.insertSpool(spool("A"))
            val oldScope = session.scope.value!!
            val before = a.getAllSpools().first()
            val pending = db.spoolDao().pendingInventoryChanges("account-a")
            accounts.sessions.value = AccountSession(SpoolAccount("account-a", "Edited profile name", "changed@example.com"))
            assertEquals(before, a.getAllSpools().first())
            assertEquals(pending, db.spoolDao().pendingInventoryChanges("account-a"))
            assertEquals(oldScope, session.scope.value)
            assertEquals(1, a.getAllSpools().first().size)
            switch("")
            val again = switch("account-a")
            assertTrue(session.scope.value!!.revision > oldScope.revision)
            a.insertSpool(spool("Stale"))
            assertEquals(1, again.getAllSpools().first().size)
            assertTrue(a.getAllSpools().first().isEmpty())
        }
    }

    @Test fun accountChangesWaitForInFlightInventoryOperations() = runBlocking {
        withDatabase {
            val a = switch("account-a")
            val expected = session.scope.value!!
            kotlinx.coroutines.coroutineScope {
                val entered = CompletableDeferred<Unit>()
                val finish = CompletableDeferred<Unit>()
                val operation = async {
                    session.withScope(expected, Unit) {
                        entered.complete(Unit)
                        finish.await()
                        dao.insertOwnedSpool(expected.ownerId, spool("In-flight A"))
                    }
                }
                entered.await()
                accounts.login("account-b")
                assertEquals(expected, session.scope.value)
                finish.complete(Unit)
                operation.await()
                withTimeout(5_000) { session.scope.first { it?.ownerId == "account-b" } }
                a.insertSpool(spool("Stale after switch"))
                assertEquals(listOf("In-flight A"), stored("account-a").map { it.brand })
                assertTrue(stored("account-b").isEmpty())
            }
        }
    }

    @Test fun restartingSameAccountKeepsRevisionWhileSignOutAndLoginInvalidateIt() = runBlocking {
        withDatabase {
            session.close()
            val values = MutableStateFlow<Preferences>(emptyPreferences())
            val store = object : DataStore<Preferences> {
                override val data = values
                override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                    values.value = transform(values.value)
                    return values.value
                }
            }
            accounts.login("account-a")
            val first = InventorySession(accounts, dao, preferences = store)
            val original = first.awaitReady()
            first.close()
            val restarted = InventorySession(accounts, dao, preferences = store)
            try {
                assertEquals(original, restarted.awaitReady())
                accounts.signOut()
                val guest = withTimeout(5_000) { restarted.scope.first { it?.ownerId == "" }!! }
                assertTrue(guest.revision > original.revision)
                accounts.login("account-a")
                val again = withTimeout(5_000) { restarted.scope.first { it?.ownerId == "account-a" }!! }
                assertTrue(again.revision > guest.revision)
            } finally { restarted.close() }
        }
    }

    @Test fun unavailablePreferencesAndRecoveryKeepInventoryAvailableWithoutReusingScopes() = runBlocking {
        withDatabase {
            session.close()
            var unavailable = true
            val values = MutableStateFlow<Preferences>(emptyPreferences())
            val store = object : DataStore<Preferences> {
                override val data = values
                override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                    if (unavailable) error("Disk unavailable")
                    values.value = transform(values.value)
                    return values.value
                }
            }
            accounts.login("account-a")
            val access = InventorySession(accounts, dao, preferences = store)
            try {
                val owner = access.awaitReady()
                val repo = DefaultSpoolRepository(dao, access, owner)
                repo.insertSpool(spool("Preserved offline access"))
                assertEquals(1, repo.getAllSpools().first().size)
                assertEquals("account-a", owner.ownerId)
                unavailable = false
                accounts.login("account-b")
                val b = withTimeout(5_000) { access.scope.first { it?.ownerId == "account-b" }!! }
                assertTrue(b.revision > owner.revision)
                accounts.login("account-a")
                val a = withTimeout(5_000) { access.scope.first { it?.ownerId == "account-a" }!! }
                assertTrue(a.revision > b.revision)
                repo.insertSpool(spool("Stale after recovery"))
                assertEquals(1, stored("account-a").size)
            } finally { access.close() }
        }
    }

    @Test fun initializationNeverExposesGuestInventoryUntilAuthenticationHasResolved() = runBlocking {
        withDatabase {
            dao.insertSpool(spool("Guest"))
            assertNull(session.scope.value)
            accounts.sessions.value = AccountSession(SpoolAccount("account-a", null, "a@example.com"), needsConnection = true)
            val active = session.awaitReady()
            assertEquals("account-a", active.ownerId)
            assertTrue(DefaultSpoolRepository(dao, session, active).getAllSpools().first().isEmpty())
            assertEquals(1, stored("").size)
        }
    }
}
