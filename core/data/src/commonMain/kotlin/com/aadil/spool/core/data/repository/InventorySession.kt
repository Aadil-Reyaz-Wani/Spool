package com.aadil.spool.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.aadil.spool.data.dao.InventorySyncDao
import com.aadil.spool.core.model.currentTimeMillis
import com.aadil.spool.core.network.AccountRepository
import com.aadil.spool.data.dao.SpoolDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

interface InventoryNotifications {
    fun clearInventoryNotifications()
}

/** Empty owner ID is the existing guest inventory. Null scope locks inventory during auth restore. */
data class InventoryScope(val ownerId: String, val revision: Long)

class InventorySession(
    private val accounts: AccountRepository,
    private val dao: SpoolDao,
    private val notifications: InventoryNotifications? = null,
    private val preferences: DataStore<Preferences>? = null,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val syncDao: InventorySyncDao? = null,
) {
    private val mutex = Mutex()
    private val mutableScope = MutableStateFlow<InventoryScope?>(null)
    val scope = mutableScope.asStateFlow()
    val guestSpoolCount = dao.guestSpoolCount()
    private var revision = 0L
    private val mutableDeletingOwners = MutableStateFlow<Set<String>>(emptySet())
    val deletingOwners = mutableDeletingOwners.asStateFlow()

    init {
        coroutineScope.launch {
            try {
                val stored = preferences?.data?.first()
                mutableDeletingOwners.value = stored?.get(DELETING).orEmpty()
                // Only finish local cleanup after a durable, successful server confirmation.
                for (owner in stored?.get(DELETED).orEmpty()) {
                    finishDeletedAccount(owner)
                }
                accounts.sessions.collect { session ->
                    mutex.withLock {
                        val account = session.account
                        val owner = when {
                            account != null -> account.id.takeIf { it.isNotBlank() }
                            session.initializing || session.needsConnection -> null
                            else -> ""
                        }
                        if (owner != mutableScope.value?.ownerId) {
                            notifications?.clearInventoryNotifications()
                            revision = nextRevision(owner)
                            mutableScope.value = owner?.let { InventoryScope(it, revision) }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutex.withLock { mutableScope.value = null }
            }
        }
    }

    private suspend fun nextRevision(owner: String?): Long {
        val store = preferences ?: return revision + 1
        if (owner == null) return revision + 1
        return try {
            val saved = store.edit { values ->
                val previous = values[REVISION] ?: 0L
                val restoringSameSession = revision == 0L && values[OWNER] == owner && previous > 0
                values[REVISION] = if (restoringSameSession) previous else maxOf(previous, revision) + 1
                values[OWNER] = owner
            }
            checkNotNull(saved[REVISION])
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Preferences failure must not prevent access to correctly owned inventory.
            // A fresh scope safely discards saved UI state when its revision cannot be restored.
            maxOf(revision + 1, currentTimeMillis())
        }
    }

    suspend fun awaitReady(): InventoryScope = scope.filterNotNull().first()

    suspend fun <T> withScope(expected: InventoryScope, unavailable: T, block: suspend () -> T): T = mutex.withLock {
        if (scope.value != expected || expected.ownerId in deletingOwners.value) unavailable else block()
    }

    suspend fun importGuestInventory(expected: InventoryScope): Boolean = withScope(expected, false) {
        if (expected.ownerId.isBlank()) return@withScope false
        dao.importGuestInventory(expected.ownerId)
        true
    }

    /** Explicit user action only. An ambiguous response keeps local data and blocks further writes. */
    suspend fun deleteAccount(expected: InventoryScope): Boolean = mutex.withLock {
        if (scope.value != expected || expected.ownerId.isBlank()) return@withLock false
        val owner = expected.ownerId
        val store = checkNotNull(preferences)
        checkNotNull(syncDao)
        val values = store.edit { it[DELETING] = it[DELETING].orEmpty() + owner }
        mutableDeletingOwners.value = values[DELETING].orEmpty()
        notifications?.clearInventoryNotifications()
        if (owner !in values[DELETED].orEmpty()) {
            accounts.deleteAccount(owner)
            store.edit { it[DELETED] = it[DELETED].orEmpty() + owner }
        }
        finishDeletedAccount(owner)
        true
    }

    private suspend fun finishDeletedAccount(owner: String) {
        require(owner.isNotBlank())
        checkNotNull(syncDao).purgeDeletedAccount(owner)
        accounts.clearDeletedSession(owner)
        if (scope.value?.ownerId == owner) {
            revision++
            mutableScope.value = null
        }
        // Leave no saved inventory or profile payload in the deletion journal.
        val updated = checkNotNull(preferences).edit {
            it[DELETING] = it[DELETING].orEmpty() - owner
            it[DELETED] = it[DELETED].orEmpty() - owner
        }
        mutableDeletingOwners.value = updated[DELETING].orEmpty()
    }

    fun close() { coroutineScope.cancel() }

    private companion object {
        val DELETING = stringSetPreferencesKey("inventory_deleting_accounts")
        val DELETED = stringSetPreferencesKey("inventory_deleted_accounts")
        val OWNER = stringPreferencesKey("inventory_scope_owner")
        val REVISION = longPreferencesKey("inventory_scope_revision")
    }
}
