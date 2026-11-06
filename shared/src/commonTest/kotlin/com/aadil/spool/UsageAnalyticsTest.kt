package com.aadil.spool

import com.aadil.spool.core.data.analytics.AnalyticsBackend
import com.aadil.spool.core.data.analytics.AnalyticsConsentStore
import com.aadil.spool.core.data.analytics.UsageAnalytics
import com.aadil.spool.core.data.analytics.UsageEvent
import com.aadil.spool.feature.settings.AnalyticsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UsageAnalyticsTest {
    private class Store(var choice: Boolean? = null) : AnalyticsConsentStore {
        var failWrite = false
        var failRead = false
        override suspend fun read(): Boolean? {
            check(!failRead)
            return choice
        }
        override suspend fun write(enabled: Boolean) {
            check(!failWrite)
            choice = enabled
        }
    }

    private class Backend(override val available: Boolean = true) : AnalyticsBackend {
        var enabled = false
        var failRecord = false
        val events = mutableListOf<UsageEvent>()
        override fun setCollectionEnabled(enabled: Boolean) { this.enabled = enabled }
        override fun record(event: UsageEvent) {
            check(!failRecord)
            check(enabled)
            events += event
        }
    }

    @Test fun unknownConsentAndEarlierActionsNeverProduceEvents() = runTest {
        val backend = Backend()
        val analytics = UsageAnalytics(Store(), backend, backgroundScope)
        analytics.record(UsageEvent.SpoolCreated)
        analytics.start()
        advanceUntilIdle()
        assertFalse(backend.enabled)
        analytics.record(UsageEvent.UsageLogged)
        analytics.setConsent(true)
        assertEquals(listOf(UsageEvent.AnalyticsEnabled), backend.events)
        analytics.record(UsageEvent.SpoolCreated)
        assertEquals(listOf(UsageEvent.AnalyticsEnabled, UsageEvent.SpoolCreated), backend.events)
    }

    @Test fun declinedChoiceSurvivesRecreationAndSuppressesCollection() = runTest {
        val store = Store()
        val first = UsageAnalytics(store, Backend(), backgroundScope)
        first.setConsent(false)
        val backend = Backend()
        val recreated = UsageAnalytics(store, backend, backgroundScope)
        recreated.start()
        recreated.setConsent(false)
        recreated.record(UsageEvent.SignInCompleted)
        assertEquals(false, recreated.state.value.choice)
        assertFalse(backend.enabled)
        assertTrue(backend.events.isEmpty())
    }

    @Test fun savedOptInEnablesCollectionAndWithdrawalStopsIt() = runTest {
        val store = Store(true)
        val backend = Backend()
        val analytics = UsageAnalytics(store, backend, this)
        analytics.start()
        advanceUntilIdle()
        assertTrue(backend.enabled)
        analytics.record(UsageEvent.UsageLogged)
        analytics.setConsent(false)
        analytics.record(UsageEvent.SignInCompleted)
        assertFalse(backend.enabled)
        assertEquals(false, store.choice)
        assertEquals(listOf(UsageEvent.UsageLogged), backend.events)
    }

    @Test fun persistenceFailureDoesNotEnableCollectionAndWithdrawalStillStopsEvents() = runTest {
        val store = Store().apply { failWrite = true }
        val backend = Backend()
        val analytics = UsageAnalytics(store, backend, backgroundScope)
        assertFailsWith<IllegalStateException> { analytics.setConsent(true) }
        assertFalse(backend.enabled)
        assertNull(analytics.state.value.choice)
        store.failWrite = false
        analytics.setConsent(true)
        backend.events.clear()
        store.failWrite = true
        assertFailsWith<IllegalStateException> { analytics.setConsent(false) }
        analytics.record(UsageEvent.SpoolCreated)
        assertFalse(backend.enabled)
        assertTrue(backend.events.isEmpty())
    }

    @Test fun unavailableBackendAndUnreadablePreferencesFailClosed() = runTest {
        val unsupported = Backend(available = false)
        val analytics = UsageAnalytics(Store(true), unsupported, backgroundScope)
        analytics.setConsent(true)
        analytics.record(UsageEvent.SpoolCreated)
        assertFalse(unsupported.enabled)
        assertTrue(unsupported.events.isEmpty())
        val backend = Backend()
        val unreadable = UsageAnalytics(Store(true).apply { failRead = true }, backend, this)
        unreadable.start()
        advanceUntilIdle()
        assertFalse(backend.enabled)
        assertNull(unreadable.state.value.choice)
    }

    @Test fun backendExceptionsDoNotEscapeIntoInventoryOperations() = runTest {
        val backend = Backend().apply { failRecord = true }
        val analytics = UsageAnalytics(Store(), backend, backgroundScope)
        analytics.setConsent(true)
        analytics.record(UsageEvent.SpoolCreated)
        analytics.record(UsageEvent.UsageLogged)
    }

    @Test fun repeatedSyncRejectionsAreDeduplicatedUntilSuccessfulSync() = runTest {
        val backend = Backend()
        val analytics = UsageAnalytics(Store(), backend, backgroundScope)
        analytics.setConsent(true)
        backend.events.clear()
        repeat(5) { analytics.record(UsageEvent.SyncRejected) }
        analytics.record(UsageEvent.SyncUploadApplied)
        analytics.record(UsageEvent.SyncRejected)
        assertEquals(listOf(UsageEvent.SyncRejected, UsageEvent.SyncUploadApplied, UsageEvent.SyncRejected), backend.events)
    }

    @Test fun viewModelExposesFailedChoiceAndAllowsRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = Store().apply { failWrite = true }
            val analytics = UsageAnalytics(store, Backend(), this)
            val viewModel = AnalyticsViewModel(analytics)
            viewModel.choose(true)
            advanceUntilIdle()
            assertTrue(viewModel.error.value)
            assertFalse(viewModel.saving.value)
            assertNull(viewModel.consent.value.choice)
            store.failWrite = false
            viewModel.choose(false)
            advanceUntilIdle()
            assertFalse(viewModel.error.value)
            assertEquals(false, viewModel.consent.value.choice)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun decliningDoesNotBlockInventoryWhenPreferencesCannotBeSaved() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = Store().apply { failWrite = true }
            val backend = Backend()
            val analytics = UsageAnalytics(store, backend, this)
            val viewModel = AnalyticsViewModel(analytics)
            viewModel.choose(false)
            assertTrue(viewModel.promptDismissed.value)
            advanceUntilIdle()
            assertTrue(viewModel.error.value)
            assertFalse(backend.enabled)
            store.failWrite = false
            viewModel.retryChoice()
            advanceUntilIdle()
            assertEquals(false, store.choice)
            assertFalse(viewModel.error.value)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
