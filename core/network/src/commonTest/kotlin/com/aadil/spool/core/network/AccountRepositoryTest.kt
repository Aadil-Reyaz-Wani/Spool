package com.aadil.spool.core.network

import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.TextContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

@OptIn(SupabaseInternal::class)
class AccountRepositoryTest {
    private fun authTest(block: suspend () -> Unit) = runTest {
        withContext(Dispatchers.Default) { block() }
    }

    private val user = UserInfo(
        aud = "authenticated", id = "permanent-account-id", email = "owner@example.com",
        userMetadata = buildJsonObject {
            put("full_name", JsonPrimitive("Google Name"))
            put("avatar_url", JsonPrimitive("https://example.com/photo"))
        },
    )
    private fun session() = UserSession("access", "refresh", expiresIn = 3600, tokenType = "bearer", user = user)
    private val configuration = SupabaseConfiguration.parse(TEST_URL, TEST_KEY)

    @Test
    fun onlyNameEmailAndPermanentIdReachProfile() {
        assertEquals(SpoolAccount(user.id, "Google Name", user.email), user.toSpoolAccount())
        val changed = user.copy(email = "changed@example.com", userMetadata = buildJsonObject {
            put("display_name", JsonPrimitive("My edited name"))
            put("full_name", JsonPrimitive("Google Name"))
        }).toSpoolAccount()
        assertEquals(user.id, changed.id)
        assertEquals("My edited name", changed.name)
        assertEquals("changed@example.com", changed.email)
        assertNull(user.copy(userMetadata = null).toSpoolAccount().name)
    }

    @Test
    fun missingConfigurationKeepsGuestModeWithoutNetwork() = authTest {
        val connection = SupabaseConnection(SupabaseConfiguration.NotConfigured) { error("Unexpected network") }
        assertEquals(AccountSession(), SupabaseAccountRepository(connection).sessions.first())
        connection.close()
    }

    @Test
    fun googleTokenAndOriginalNonceAreVerifiedBySupabaseBeforeSessionIsSaved() = authTest {
        val storage = MemorySessionManager()
        var requests = 0
        val connection = SupabaseConnection(configuration, storage, setupAuthPlatform = false) {
            MockEngine { request ->
                requests++
                assertEquals("/auth/v1/token", request.url.encodedPath)
                assertEquals("id_token", request.url.parameters["grant_type"])
                val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                assertEquals("google", body["provider"]?.jsonPrimitive?.content)
                assertEquals("google-id-token", body["id_token"]?.jsonPrimitive?.content)
                assertEquals("original-nonce", body["nonce"]?.jsonPrimitive?.content)
                respond(Json.encodeToString(session()), headers = headersOf("Content-Type", "application/json"))
            }
        }
        try {
            val auth = assertNotNull(connection.client).auth
            // Host tests have no Android process lifecycle; mark initialization complete explicitly.
            auth.setSessionStatus(SessionStatus.NotAuthenticated())
            val repository = SupabaseAccountRepository(connection, "client.apps.googleusercontent.com")
            assertNull(storage.loadSession())
            repository.signIn(GoogleCredentials("google-id-token", "original-nonce"))
            assertEquals(1, requests)
            assertEquals(user.id, storage.loadSession()?.user?.id)
            assertEquals(user.toSpoolAccount(), repository.sessions.first { it.account != null }.account)
        } finally { connection.close() }
    }

    @Test
    fun rejectedTokenNeverCreatesSession() = authTest {
        val storage = MemorySessionManager()
        val connection = SupabaseConnection(configuration, storage, setupAuthPlatform = false) {
            MockEngine { respond("{}", HttpStatusCode.Unauthorized, headersOf("Content-Type", "application/json")) }
        }
        try {
            assertNotNull(connection.client).auth.setSessionStatus(SessionStatus.NotAuthenticated())
            assertFails { SupabaseAccountRepository(connection).signIn(GoogleCredentials("invalid", "nonce")) }
            assertNull(storage.loadSession())
            assertEquals(AccountSession(), SupabaseAccountRepository(connection).sessions.first())
        } finally { connection.close() }
    }

    @Test
    fun newConnectionRestoresSavedAccountWithoutNetwork() = authTest {
        val storage = MemorySessionManager(session())
        val connection = SupabaseConnection(configuration, storage, setupAuthPlatform = false) {
            MockEngine { error("A fresh saved session does not need network") }
        }
        try {
            val account = withTimeout(5_000) {
                SupabaseAccountRepository(connection).sessions.first { it.account != null && !it.needsConnection }
            }
            assertEquals(user.toSpoolAccount(), account.account)
            assertFalse(account.initializing)
        } finally { connection.close() }
    }

    @Test
    fun offlineExpiredSessionRetainsCachedIdentityAndRefreshToken() = authTest {
        val expired = session().copy(expiresAt = Clock.System.now() - 1.hours)
        val storage = MemorySessionManager(expired)
        val connection = SupabaseConnection(configuration, storage, setupAuthPlatform = false) {
            MockEngine { throw IOException("Offline") }
        }
        try {
            withTimeout(5_000) {
                assertNotNull(connection.client).auth.sessionStatus.first { it is SessionStatus.RefreshFailure }
            }
            val state = withTimeout(5_000) {
                SupabaseAccountRepository(connection).sessions.first { it.account != null && it.needsConnection }
            }
            assertEquals(user.toSpoolAccount(), state.account)
            assertEquals("refresh", storage.loadSession()?.refreshToken)
        } finally { connection.close() }
    }

    @Test
    fun revokedRefreshTokenClearsAccountWithoutChangingInventory() = authTest {
        val storage = MemorySessionManager(session().copy(expiresAt = Clock.System.now() - 1.hours))
        val connection = SupabaseConnection(configuration, storage, setupAuthPlatform = false) {
            MockEngine { respond("{}", HttpStatusCode.BadRequest, headersOf("Content-Type", "application/json")) }
        }
        try {
            withTimeout(5_000) {
                assertNotNull(connection.client).auth.sessionStatus.first { it is SessionStatus.NotAuthenticated }
            }
            assertNull(storage.loadSession())
            assertEquals(AccountSession(), SupabaseAccountRepository(connection).sessions.first())
        } finally { connection.close() }
    }

    @Test
    fun offlineSignOutDeletesSavedSessionWithoutAccessToInventory() = authTest {
        val storage = MemorySessionManager(session())
        val connection = SupabaseConnection(configuration, storage, setupAuthPlatform = false) {
            MockEngine { throw IOException("Offline") }
        }
        try {
            val repository = SupabaseAccountRepository(connection)
            withTimeout(5_000) { repository.sessions.first { it.account != null && !it.needsConnection } }
            repository.signOut()
            assertNull(storage.loadSession())
            assertEquals(AccountSession(), repository.sessions.first())
        } finally { connection.close() }
    }

    @Test
    fun credentialsDoNotPrintTokens() {
        assertEquals("GoogleCredentials(REDACTED)", GoogleCredentials("secret", "nonce").toString())
    }
}
