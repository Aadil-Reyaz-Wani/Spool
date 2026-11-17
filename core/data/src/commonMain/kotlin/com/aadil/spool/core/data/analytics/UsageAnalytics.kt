package com.aadil.spool.core.data.analytics

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// A closed event list prevents accidental transmission of inventory or account fields.
enum class UsageEvent(val eventName: String) {
    AnalyticsEnabled("analytics_enabled"),
    SpoolCreated("spool_created"),
    UsageLogged("usage_logged"),
    SignInCompleted("sign_in_completed"),
    SyncUploadApplied("sync_upload_applied"),
    SyncDownloadProcessed("sync_download_processed"),
    SyncRejected("sync_rejected"),
}

interface AnalyticsBackend {
    val available: Boolean
    fun setCollectionEnabled(enabled: Boolean)
    fun record(event: UsageEvent)
}

interface AnalyticsConsentStore {
    suspend fun read(): Boolean?
    suspend fun write(enabled: Boolean)
}

class DataStoreAnalyticsConsent(private val dataStore: DataStore<Preferences>) : AnalyticsConsentStore {
    private val key = booleanPreferencesKey("usage_analytics_consent")
    override suspend fun read(): Boolean? = dataStore.data.first()[key]
    override suspend fun write(enabled: Boolean) { dataStore.edit { it[key] = enabled } }
}

data class AnalyticsConsentState(
    val loaded: Boolean = false,
    val choice: Boolean? = null,
    val available: Boolean = false,
)

class UsageAnalytics(
    private val store: AnalyticsConsentStore,
    private val backend: AnalyticsBackend,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(AnalyticsConsentState(available = backend.available))
    val state = mutableState.asStateFlow()
    private var rejectedReported = false
    private var collectionEnabled = false
    private val initialization = scope.launch(start = CoroutineStart.LAZY) {
        mutex.withLock {
            // The manifest also disables SDK collection before application initialization.
            val choice = try {
                store.read()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            val configured = runCatching { backend.setCollectionEnabled(choice == true && backend.available) }.isSuccess
            collectionEnabled = choice == true && backend.available && configured
            mutableState.value = AnalyticsConsentState(true, choice, backend.available && configured)
        }
    }

    fun start() { initialization.start() }

    suspend fun setConsent(enabled: Boolean) {
        initialization.start()
        initialization.join()
        mutex.withLock {
            val previousChoice = state.value.choice
            // Stop collection immediately on withdrawal, even if persisting the choice fails.
            if (!enabled) {
                collectionEnabled = false
                runCatching { backend.setCollectionEnabled(false) }
            }
            store.write(enabled)
            val configured = runCatching { backend.setCollectionEnabled(enabled && backend.available) }.isSuccess
            collectionEnabled = enabled && backend.available && configured
            mutableState.value = AnalyticsConsentState(true, enabled, backend.available && configured)
            rejectedReported = false
            if (collectionEnabled && previousChoice != true) runCatching { backend.record(UsageEvent.AnalyticsEnabled) }
        }
    }

    fun record(event: UsageEvent) {
        // Never replay actions performed before consent or before preferences have loaded.
        if (!state.value.loaded || state.value.choice != true || !state.value.available) return
        // Inventory writes never wait for preference I/O or consent changes.
        if (!mutex.tryLock()) return
        try {
            if (!collectionEnabled || state.value.choice != true || !state.value.available) return
            if (event == UsageEvent.SyncRejected && rejectedReported) return
            runCatching { backend.record(event) }
            when (event) {
                UsageEvent.SyncRejected -> rejectedReported = true
                UsageEvent.SyncUploadApplied, UsageEvent.SyncDownloadProcessed -> rejectedReported = false
                else -> Unit
            }
        } finally {
            mutex.unlock()
        }
    }

    fun close() { scope.cancel() }
}
