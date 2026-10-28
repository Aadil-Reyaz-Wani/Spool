package com.aadil.spool.core.network

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.put
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.coroutines.cancellation.CancellationException

/** Display data only. Inventory ownership must use id, never email or name. */
data class SpoolAccount(val id: String, val name: String?, val email: String?)
data class AccountSession(
    val account: SpoolAccount? = null,
    val initializing: Boolean = false,
    val needsConnection: Boolean = false,
)

// Tokens must never appear in logs, saved UI state, or exception messages.
class GoogleCredentials(val idToken: String, val nonce: String) {
    override fun toString(): String = "GoogleCredentials(REDACTED)"
}

interface AccountRepository {
    val googleWebClientId: String
    val sessions: Flow<AccountSession>
    suspend fun signIn(credentials: GoogleCredentials)
    suspend fun signOut()
    suspend fun updateDisplayName(accountId: String, name: String)
}

const val MAX_DISPLAY_NAME_LENGTH = 80
internal const val DISPLAY_NAME_KEY = "spool_display_name"

fun normalizeDisplayName(value: String): String? = value.trim().takeIf { name ->
    name.isNotEmpty() && name.length <= MAX_DISPLAY_NAME_LENGTH && name.none { it.isISOControl() }
}

internal fun UserInfo.toSpoolAccount(): SpoolAccount {
    fun text(key: String): String? = (userMetadata?.get(key) as? JsonPrimitive)
        ?.takeIf { it.isString }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    return SpoolAccount(id, text(DISPLAY_NAME_KEY) ?: text("display_name") ?: text("full_name") ?: text("name"), email)
}

internal class SupabaseAccountRepository(
    private val connection: SupabaseConnection,
    private val configuredClientId: String = SupabaseBuildConfig.GOOGLE_WEB_CLIENT_ID,
) : AccountRepository {
    private val accountMutex = Mutex()

    override val googleWebClientId: String
        get() = if (connection.accountSessionsEnabled) configuredClientId else ""

    override val sessions: Flow<AccountSession> = flow {
        val auth = connection.client?.auth
        if (auth == null || !connection.accountSessionsEnabled) {
            emit(AccountSession())
            return@flow
        }
        // A cached identity is display-only while the SDK refreshes an expired session offline.
        var cached = auth.sessionManager.loadSession()?.user?.toSpoolAccount()
        auth.sessionStatus.collect { status ->
            emit(when (status) {
                is SessionStatus.Authenticated -> {
                    cached = status.session.user?.toSpoolAccount()
                    AccountSession(cached)
                }
                is SessionStatus.NotAuthenticated -> {
                    cached = null
                    AccountSession()
                }
                is SessionStatus.Initializing -> AccountSession(cached, initializing = cached == null, needsConnection = cached != null)
                is SessionStatus.RefreshFailure -> AccountSession(cached, needsConnection = true)
            })
        }
    }

    override suspend fun signIn(credentials: GoogleCredentials) {
        accountMutex.withLock {
            require(credentials.idToken.isNotBlank() && credentials.nonce.isNotBlank())
            val auth = checkNotNull(connection.client?.auth)
            withTimeout(30_000) {
                auth.awaitInitialization()
                auth.signInWith(IDToken) {
                    provider = Google
                    idToken = credentials.idToken
                    nonce = credentials.nonce
                }
            }
        }
    }

    override suspend fun signOut() {
        accountMutex.withLock {
            val auth = connection.client?.auth ?: return
            try {
                withTimeout(8_000) { auth.signOut() }
            } catch (_: TimeoutCancellationException) {
                // Continue with local sign-out and clearing the credential provider state.
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Signing out of this device must also work offline.
            } finally {
                withContext(NonCancellable) { auth.clearSession() }
            }
        }
    }

    override suspend fun updateDisplayName(accountId: String, name: String) {
        val normalized = requireNotNull(normalizeDisplayName(name))
        require(accountId.isNotBlank())
        accountMutex.withLock {
            check(connection.accountSessionsEnabled)
            val auth = checkNotNull(connection.client?.auth)
            withTimeout(30_000) {
                auth.awaitInitialization()
                check(auth.currentUserOrNull()?.id == accountId)
                // The custom key is separate from Google/Apple's provider-owned name fields.
                val updated = auth.updateUser { data { put(DISPLAY_NAME_KEY, normalized) } }
                check(updated.id == accountId && updated.toSpoolAccount().name == normalized)
            }
        }
    }

}
