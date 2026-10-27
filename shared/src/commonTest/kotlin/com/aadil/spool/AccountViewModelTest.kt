package com.aadil.spool

import com.aadil.spool.core.network.AccountRepository
import com.aadil.spool.core.network.AccountSession
import com.aadil.spool.core.network.GoogleCredentials
import com.aadil.spool.core.network.SpoolAccount
import com.aadil.spool.feature.settings.AccountError
import com.aadil.spool.feature.settings.AccountViewModel
import com.aadil.spool.feature.settings.GoogleSignInCancelled
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {
    private class Repository : AccountRepository {
        override val googleWebClientId = "web.apps.googleusercontent.com"
        override val sessions = MutableStateFlow(AccountSession())
        var signIns = 0
        var signOuts = 0
        var failure = false
        override suspend fun signIn(credentials: GoogleCredentials) {
            signIns++
            if (failure) error("Sensitive provider error")
            sessions.value = AccountSession(SpoolAccount("id", "Name", "mail@example.com"))
        }
        override suspend fun signOut() { signOuts++; sessions.value = AccountSession() }
    }

    @AfterTest
    fun resetDispatcher() { Dispatchers.resetMain() }

    @Test
    fun cancelPickerKeepsGuestModeAndAllowsRetry() = runTest {
        Dispatchers.setMain(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        val repository = Repository()
        val vm = AccountViewModel(repository)
        runCurrent()
        vm.signIn { throw GoogleSignInCancelled() }
        runCurrent()
        assertFalse(vm.state.value.busy)
        assertNull(vm.state.value.error)
        assertNull(vm.state.value.session.account)
        assertEquals(0, repository.signIns)
        vm.signIn { GoogleCredentials("token", "nonce") }
        runCurrent()
        assertEquals("Name", vm.state.value.session.account?.name)
    }

    @Test
    fun duplicateTapsAreIgnoredAndFailuresCanBeRetried() = runTest {
        Dispatchers.setMain(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        val repository = Repository().apply { failure = true }
        val vm = AccountViewModel(repository)
        runCurrent()
        vm.signIn { GoogleCredentials("token", "nonce") }
        vm.signIn { error("Duplicate picker must not open") }
        assertTrue(vm.state.value.busy)
        runCurrent()
        assertEquals(1, repository.signIns)
        assertEquals(AccountError.SignIn, vm.state.value.error)
        assertFalse(vm.state.value.busy)
        repository.failure = false
        vm.signIn { GoogleCredentials("token", "nonce") }
        runCurrent()
        assertEquals(2, repository.signIns)
        assertNull(vm.state.value.error)
        assertEquals("mail@example.com", vm.state.value.session.account?.email)
        var cleared = false
        vm.signOut { cleared = true }
        runCurrent()
        assertTrue(cleared)
        assertEquals(1, repository.signOuts)
        assertNull(vm.state.value.session.account)
    }
    @Test
    fun signOutFinishesProviderCleanupAfterInventoryDisposesTheViewModel() = runTest {
        Dispatchers.setMain(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        val vm = AccountViewModel(Repository())
        val store = ViewModelStore()
        store.put("account", vm)
        runCurrent()
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var cleared = false
        vm.signOut {
            started.complete(Unit)
            finish.await()
            cleared = true
        }
        runCurrent()
        assertTrue(started.isCompleted)
        store.clear()
        runCurrent()
        finish.complete(Unit)
        runCurrent()
        assertTrue(cleared)
    }
}
