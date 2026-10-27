package com.aadil.spool.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
    accounts: AccountRepository,
    private val dao: SpoolDao,
    private val notifications: InventoryNotifications? = null,
    private val preferences: DataStore<Preferences>? = null,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val mutex = Mutex()
    private val mutableScope = MutableStateFlow<InventoryScope?>(null)
    val scope = mutableScope.asStateFlow()
    val guestSpoolCount = dao.guestSpoolCount()
    private var revision = 0L

    init {
        coroutineScope.launch {
            try {
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
        if (scope.value != expected) unavailable else block()
    }

    suspend fun importGuestInventory(expected: InventoryScope): Boolean = withScope(expected, false) {
        if (expected.ownerId.isBlank()) return@withScope false
        dao.importGuestInventory(expected.ownerId)
        true
    }

    fun close() { coroutineScope.cancel() }

    private companion object {
        val OWNER = stringPreferencesKey("inventory_scope_owner")
        val REVISION = longPreferencesKey("inventory_scope_revision")
    }
}
