package com.aadil.spool.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aadil.spool.core.network.AccountRepository
import com.aadil.spool.core.network.AccountSession
import com.aadil.spool.core.network.GoogleCredentials
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.TimeoutCancellationException
import kotlin.coroutines.cancellation.CancellationException

enum class AccountError { SignIn, Session, SignOut }
class GoogleSignInCancelled : Exception()

data class AccountUiState(
    val session: AccountSession = AccountSession(initializing = true),
    val busy: Boolean = false,
    val error: AccountError? = null,
)

class AccountViewModel(private val repository: AccountRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(AccountUiState())
    val state = mutableState.asStateFlow()
    val googleWebClientId: String get() = repository.googleWebClientId

    init {
        viewModelScope.launch {
            try {
                repository.sessions.collect { session ->
                    mutableState.value = mutableState.value.copy(session = session)
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
                repository.signOut()
                clearProviderState()
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
