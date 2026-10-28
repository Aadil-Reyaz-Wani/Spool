package com.aadil.spool

import com.aadil.spool.core.network.AccountRepository
import com.aadil.spool.core.network.AccountSession
import com.aadil.spool.core.network.GoogleCredentials
import com.aadil.spool.core.network.SpoolAccount
import com.aadil.spool.feature.settings.AccountError
import com.aadil.spool.feature.settings.AccountViewModel
import com.aadil.spool.feature.settings.GoogleSignInCancelled
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.advanceUntilIdle
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {
    private class Repository : AccountRepository {
        override val googleWebClientId = "web.apps.googleusercontent.com"
        override val sessions = MutableStateFlow(AccountSession())
        var signIns = 0
        var signOuts = 0
        var nameUpdates = 0
        var nameUpdateHandler: (suspend () -> Unit)? = null
        var failure = false
        override suspend fun signIn(credentials: GoogleCredentials) {
            signIns++
            if (failure) error("Sensitive provider error")
            sessions.value = AccountSession(SpoolAccount("id", "Name", "mail@example.com"))
        }
        override suspend fun updateDisplayName(accountId: String, name: String) {
            nameUpdates++
            nameUpdateHandler?.invoke()
            if (failure) error("Sensitive profile error")
            check(sessions.value.account?.id == accountId)
            sessions.value = sessions.value.copy(account = sessions.value.account?.copy(name = name))
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
    private fun signedInRepository() = Repository().apply {
        sessions.value = AccountSession(SpoolAccount("id", "Google Name", "mail@example.com"))
    }

    @Test fun editPrefillsCancelDoesNotWriteAndSaveOnlyChangesName() = runTest {
        Dispatchers.setMain(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        val repo = signedInRepository(); val vm = AccountViewModel(repo); runCurrent()
        vm.editName(); assertEquals("Google Name", vm.state.value.nameDraft)
        vm.changeName("Cancelled"); vm.cancelNameEdit(); vm.saveName(); runCurrent()
        assertEquals(0, repo.nameUpdates); assertEquals("Google Name", vm.state.value.session.account?.name)
        vm.editName(); vm.changeName("  Chosen 王  "); vm.saveName(); runCurrent()
        assertEquals(1, repo.nameUpdates); assertTrue(vm.state.value.nameSaved); assertFalse(vm.state.value.editingName)
        assertEquals(SpoolAccount("id", "Chosen 王", "mail@example.com"), vm.state.value.session.account)
    }

    @Test fun invalidNameNeverWritesAndGuestCannotOpenEditor() = runTest {
        Dispatchers.setMain(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        val repo = signedInRepository(); val vm = AccountViewModel(repo); runCurrent()
        vm.editName(); vm.changeName("  "); vm.saveName(); runCurrent()
        assertEquals(AccountError.NameInvalid, vm.state.value.error); assertEquals(0, repo.nameUpdates)
        vm.changeName("First\nLast"); vm.saveName(); assertEquals(0, repo.nameUpdates)
        vm.changeName("Valid"); vm.changeName("x".repeat(81)); assertEquals("Valid", vm.state.value.nameDraft)
        repo.sessions.value = AccountSession(); runCurrent(); vm.editName(); vm.saveName()
        assertFalse(vm.state.value.editingName); assertEquals("", vm.state.value.nameDraft); assertEquals(0, repo.nameUpdates)
    }

    @Test fun failedSaveRetainsDraftAcrossRecreationWithoutAutomaticRetry() = runTest {
        Dispatchers.setMain(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        val repo = signedInRepository(); repo.failure = true; val saved = SavedStateHandle()
        val vm = AccountViewModel(repo, saved = saved); runCurrent()
        vm.editName(); vm.changeName("Offline draft"); vm.saveName(); runCurrent()
        assertEquals(AccountError.Profile, vm.state.value.error); assertTrue(vm.state.value.editingName)
        assertEquals("Offline draft", vm.state.value.nameDraft); assertEquals("Google Name", vm.state.value.session.account?.name)
        val restored = AccountViewModel(repo, saved = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })); runCurrent()
        assertTrue(restored.state.value.editingName); assertEquals("Offline draft", restored.state.value.nameDraft)
        assertEquals(1, repo.nameUpdates); assertFalse(restored.state.value.busy)
        repo.failure = false; restored.saveName(); runCurrent()
        assertEquals(2, repo.nameUpdates); assertEquals("Offline draft", restored.state.value.session.account?.name)
        assertTrue(restored.state.value.nameSaved)
    }

    @Test fun busySaveIgnoresDuplicateSaveAndBlocksDraftChanges() = runTest {
        Dispatchers.setMain(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        val repo = signedInRepository(); val finish = CompletableDeferred<Unit>(); repo.nameUpdateHandler = { finish.await() }
        val vm = AccountViewModel(repo); runCurrent(); vm.editName(); vm.changeName("Chosen"); vm.saveName(); vm.saveName(); runCurrent()
        assertEquals(1, repo.nameUpdates); assertTrue(vm.state.value.busy)
        vm.cancelNameEdit(); vm.changeName("Changed mid-save"); assertEquals("Chosen", vm.state.value.nameDraft)
        finish.complete(Unit); runCurrent(); assertFalse(vm.state.value.busy)
        assertEquals("Chosen", vm.state.value.session.account?.name); assertTrue(vm.state.value.nameSaved)
    }

    @Test fun providerRefreshDoesNotReplaceUnfinishedDraft() = runTest {
        Dispatchers.setMain(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        val repo = signedInRepository(); val vm = AccountViewModel(repo); runCurrent()
        vm.editName(); vm.changeName("My draft")
        repo.sessions.value = AccountSession(SpoolAccount("id", "Updated provider name", "new@example.com")); runCurrent()
        assertEquals("My draft", vm.state.value.nameDraft); assertTrue(vm.state.value.editingName)
        vm.cancelNameEdit(); vm.editName(); assertEquals("Updated provider name", vm.state.value.nameDraft)
        assertEquals("new@example.com", vm.state.value.session.account?.email)
    }

    @Test fun accountSwitchClearsDraftAndLateReplyCannotShowSuccessForOtherAccount() = runTest {
        Dispatchers.setMain(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        val repo = signedInRepository(); val finish = CompletableDeferred<Unit>(); repo.nameUpdateHandler = { finish.await() }
        val saved = SavedStateHandle(); val vm = AccountViewModel(repo, saved = saved); runCurrent()
        vm.editName(); vm.changeName("A draft"); vm.saveName(); runCurrent()
        repo.sessions.value = AccountSession(SpoolAccount("b", "B", "b@example.com")); runCurrent()
        assertFalse(vm.state.value.editingName); assertEquals("", vm.state.value.nameDraft)
        assertFalse(saved.contains("profile_draft"))
        finish.complete(Unit); runCurrent()
        assertEquals("B", vm.state.value.session.account?.name); assertFalse(vm.state.value.nameSaved)
        assertNull(vm.state.value.error); assertFalse(vm.state.value.busy)
    }

    @Test fun staleSavedDraftIsDiscardedForOtherAccountAndMissingProviderNameIsOptional() = runTest {
        Dispatchers.setMain(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        val repo = signedInRepository(); repo.sessions.value = AccountSession(SpoolAccount("b", null, "b@example.com"))
        val saved = SavedStateHandle(mapOf("profile_owner" to "id", "profile_draft" to "Private A draft", "profile_editing" to true))
        val vm = AccountViewModel(repo, saved = saved); runCurrent()
        assertFalse(vm.state.value.editingName); assertFalse(saved.contains("profile_draft"))
        assertNull(vm.state.value.session.account?.name)
        vm.editName(); assertEquals("", vm.state.value.nameDraft)
        vm.changeName("New name"); vm.saveName(); runCurrent(); assertEquals("New name", vm.state.value.session.account?.name)
    }

    @Test fun timedOutNameSaveShowsRetryErrorWithoutDiscardingDraft() = runTest {
        Dispatchers.setMain(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        val repo = signedInRepository(); repo.nameUpdateHandler = { withTimeout(1) { CompletableDeferred<Unit>().await() } }
        val vm = AccountViewModel(repo); runCurrent(); vm.editName(); vm.changeName("Retry draft"); vm.saveName(); advanceUntilIdle()
        assertEquals(AccountError.Profile, vm.state.value.error); assertFalse(vm.state.value.busy)
        assertTrue(vm.state.value.editingName); assertEquals("Retry draft", vm.state.value.nameDraft)
        assertEquals("Google Name", vm.state.value.session.account?.name)
    }

    @Test fun longProviderNameCanBeShortenedBeforeSaving() = runTest {
        Dispatchers.setMain(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        val repo = signedInRepository(); repo.sessions.value = AccountSession(SpoolAccount("id", "x".repeat(100), "mail@example.com"))
        val vm = AccountViewModel(repo); runCurrent(); vm.editName(); vm.changeName("x".repeat(99))
        assertEquals(99, vm.state.value.nameDraft.length)
        vm.saveName(); assertEquals(AccountError.NameInvalid, vm.state.value.error); assertEquals(0, repo.nameUpdates)
        vm.changeName("Short name"); vm.saveName(); runCurrent(); assertEquals("Short name", vm.state.value.session.account?.name)
    }

}
