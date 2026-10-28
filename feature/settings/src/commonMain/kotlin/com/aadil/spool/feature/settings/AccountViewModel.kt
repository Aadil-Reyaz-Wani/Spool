package com.aadil.spool.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aadil.spool.core.data.sync.InventoryUploadCoordinator
import com.aadil.spool.core.data.sync.InventoryUploadStatus
import com.aadil.spool.core.data.repository.InventorySession
import com.aadil.spool.core.data.repository.InventoryScope
import com.aadil.spool.core.network.AccountRepository
import com.aadil.spool.core.network.AccountSession
import com.aadil.spool.core.network.GoogleCredentials
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlin.coroutines.cancellation.CancellationException

enum class AccountError { SignIn, Session, SignOut, Import }
class GoogleSignInCancelled : Exception()

data class AccountUiState(
    val session: AccountSession = AccountSession(initializing = true),
    val busy: Boolean = false,
    val error: AccountError? = null,
    val guestSpoolCount: Int = 0,
    val confirmImport: Boolean = false,
    val uploads: InventoryUploadStatus = InventoryUploadStatus(),
    val confirmSignOut: Boolean = false,
)

class AccountViewModel(
    private val repository: AccountRepository,
    private val inventory: InventorySession? = null,
    private val uploads: InventoryUploadCoordinator? = null,
) : ViewModel() {
    private var pendingImport: InventoryScope? = null
    private val mutableState = MutableStateFlow(AccountUiState())
    val state = mutableState.asStateFlow()
    val googleWebClientId: String get() = repository.googleWebClientId

    init {
        uploads?.let { coordinator ->
            viewModelScope.launch {
                coordinator.status.collect { status ->
                    mutableState.value = state.value.copy(uploads = status.forAccount(state.value.session.account?.id))
                }
            }
        }
        inventory?.let { session ->
            viewModelScope.launch {
                session.guestSpoolCount.collect { count ->
                    mutableState.value = state.value.copy(guestSpoolCount = count)
                }
            }
        }
        viewModelScope.launch {
            try {
                repository.sessions.collect { session ->
                    mutableState.value = mutableState.value.copy(
                        session = session,
                        uploads = uploads?.status?.value?.forAccount(session.account?.id) ?: InventoryUploadStatus(),
                        confirmSignOut = state.value.confirmSignOut && session.account?.id == state.value.session.account?.id,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(
                    session = AccountSession(), error = AccountError.Session,
                )
            }
        }
    }

    private fun InventoryUploadStatus.forAccount(id: String?): InventoryUploadStatus =
        if (id != null && scope?.ownerId == id) this else InventoryUploadStatus()

    fun retryUploads() { uploads?.retry() }

    fun requestSignOutConfirmation() {
        mutableState.value = state.value.copy(confirmSignOut = true)
    }

    fun dismissSignOutConfirmation() {
        mutableState.value = state.value.copy(confirmSignOut = false)
    }

    fun requestGuestImport() {
        val account = state.value.session.account ?: return
        val scope = inventory?.scope?.value ?: return
        if (state.value.busy || state.value.guestSpoolCount == 0 || scope.ownerId != account.id) return
        pendingImport = scope
        mutableState.value = state.value.copy(confirmImport = true, error = null)
    }

    fun dismissGuestImport() {
        pendingImport = null
        mutableState.value = state.value.copy(confirmImport = false)
    }

    fun confirmGuestImport() {
        val scope = pendingImport ?: return
        if (state.value.busy) return
        pendingImport = null
        mutableState.value = state.value.copy(confirmImport = false, busy = true, error = null)
        viewModelScope.launch {
            try {
                if (inventory?.importGuestInventory(scope) != true) {
                    mutableState.value = state.value.copy(error = AccountError.Import)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = state.value.copy(error = AccountError.Import)
            } finally {
                mutableState.value = state.value.copy(busy = false)
            }
        }
    }

    fun signIn(requestCredentials: suspend () -> GoogleCredentials) {
        if (state.value.busy || state.value.session.initializing || state.value.session.account != null) return
        mutableState.value = state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                repository.signIn(requestCredentials())
            } catch (_: GoogleSignInCancelled) {
                // Dismissing Google's account picker is not an authentication error.
            } catch (_: TimeoutCancellationException) {
                mutableState.value = state.value.copy(error = AccountError.SignIn)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = state.value.copy(error = AccountError.SignIn)
            } finally {
                mutableState.value = state.value.copy(busy = false)
            }
        }
    }

    fun signOut(clearProviderState: suspend () -> Unit) {
        if (state.value.busy) return
        mutableState.value = state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                // Switching inventory disposes this ViewModel; finish credential cleanup anyway.
                withContext(NonCancellable) {
                    repository.signOut()
                    withTimeout(8_000) { clearProviderState() }
                }
            } catch (_: TimeoutCancellationException) {
                // The repository has already removed this device's session.
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = state.value.copy(error = AccountError.SignOut)
            } finally {
                mutableState.value = state.value.copy(busy = false)
            }
        }
    }
}
