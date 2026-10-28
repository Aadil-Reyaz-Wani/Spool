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
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.yield
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
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
    @Test fun displayNameValidationSupportsUnicodeAndRejectsBlankLongOrMultilineNames() {
        assertEquals("Aadil 王", normalizeDisplayName("  Aadil 王  "))
        assertEquals("x".repeat(80), normalizeDisplayName("x".repeat(80)))
        assertNull(normalizeDisplayName("   "))
        assertNull(normalizeDisplayName("x".repeat(81)))
        assertNull(normalizeDisplayName("First\nLast"))
        assertNull(normalizeDisplayName("First\u0000Last"))
        val custom = user.copy(userMetadata = buildJsonObject {
            put(DISPLAY_NAME_KEY, "Chosen name"); put("display_name", "Provider display"); put("full_name", "New Google Name")
        })
        assertEquals("Chosen name", custom.toSpoolAccount().name)
        assertEquals("Google Name", user.copy(userMetadata = buildJsonObject {
            put(DISPLAY_NAME_KEY, JsonPrimitive(false)); put("full_name", "Google Name")
        }).toSpoolAccount().name)
    }

    @Test fun profileUpdateSendsOnlyCustomNameAndPersistsItInCachedSession() = authTest {
        val storage = MemorySessionManager(session()); var requests = 0
        val updated = user.copy(userMetadata = buildJsonObject {
            put("full_name", "Google Name"); put(DISPLAY_NAME_KEY, "Chosen 王")
        })
        val connection = SupabaseConnection(configuration, storage, setupAuthPlatform = false) {
            MockEngine { request ->
                requests++; assertEquals("/auth/v1/user", request.url.encodedPath); assertEquals("PUT", request.method.value)
                val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                assertEquals(setOf("data"), body.keys)
                assertEquals(buildJsonObject { put(DISPLAY_NAME_KEY, "Chosen 王") }, body["data"])
                respond(Json.encodeToString(UserInfo.serializer(), updated), headers = headersOf("Content-Type", "application/json"))
            }
        }
        try {
            val repository = SupabaseAccountRepository(connection)
            repository.updateDisplayName(user.id, "  Chosen 王  ")
            assertEquals(1, requests)
            assertEquals(SpoolAccount(user.id, "Chosen 王", user.email), storage.loadSession()?.user?.toSpoolAccount())
            assertEquals("Chosen 王", repository.sessions.first { it.account != null }.account?.name)
        } finally { connection.close() }
        val offline = SupabaseConnection(configuration, storage, setupAuthPlatform = false) { MockEngine { error("Offline") } }
        try {
            val restored = withTimeout(5000) { SupabaseAccountRepository(offline).sessions.first { it.account != null } }
            assertEquals("Chosen 王", restored.account?.name); assertEquals(user.id, restored.account?.id)
        } finally { offline.close() }
    }

    @Test fun wrongAccountAndInvalidNamesNeverSendProfileRequests() = authTest {
        var requests = 0
        val connection = SupabaseConnection(configuration, MemorySessionManager(session()), setupAuthPlatform = false) {
            MockEngine { requests++; error("Unexpected profile request") }
        }
        try {
            val repository = SupabaseAccountRepository(connection)
            assertFails { repository.updateDisplayName("other-account", "Name") }
            assertFails { repository.updateDisplayName(user.id, " ") }
            assertFails { repository.updateDisplayName(user.id, "x".repeat(81)) }
            assertFails { repository.updateDisplayName("", "Name") }
            assertEquals(0, requests)
        } finally { connection.close() }
    }

    @Test fun offlineProfileFailureKeepsPreviousNameAndCanBeRetried() = authTest {
        val storage = MemorySessionManager(session()); var offline = true
        val updated = user.copy(userMetadata = buildJsonObject { put(DISPLAY_NAME_KEY, "Retry name") })
        val connection = SupabaseConnection(configuration, storage, setupAuthPlatform = false) {
            MockEngine {
                if (offline) throw IOException("Offline")
                respond(Json.encodeToString(UserInfo.serializer(), updated), headers = headersOf("Content-Type", "application/json"))
            }
        }
        try {
            val repository = SupabaseAccountRepository(connection)
            assertFails { repository.updateDisplayName(user.id, "Retry name") }
            assertEquals("Google Name", storage.loadSession()?.user?.toSpoolAccount()?.name)
            offline = false; repository.updateDisplayName(user.id, "Retry name")
            assertEquals("Retry name", storage.loadSession()?.user?.toSpoolAccount()?.name)
        } finally { connection.close() }
    }

    @Test fun laterGoogleSignInUsesSavedCustomNameOverNewProviderNameWithoutProfileWrite() = authTest {
        val chosen = user.copy(userMetadata = buildJsonObject {
            put(DISPLAY_NAME_KEY, "Chosen name"); put("full_name", "Changed Google Name")
        })
        var requests = 0
        val connection = SupabaseConnection(configuration, MemorySessionManager(), setupAuthPlatform = false) {
            MockEngine { request ->
                requests++; assertEquals("/auth/v1/token", request.url.encodedPath)
                respond(Json.encodeToString(session().copy(user = chosen)), headers = headersOf("Content-Type", "application/json"))
            }
        }
        try {
            assertNotNull(connection.client).auth.setSessionStatus(SessionStatus.NotAuthenticated())
            val repository = SupabaseAccountRepository(connection)
            repository.signIn(GoogleCredentials("google-id-token", "nonce"))
            assertEquals("Chosen name", repository.sessions.first { it.account != null }.account?.name)
            assertEquals(1, requests)
        } finally { connection.close() }
    }

    @Test fun profileRequestFinishesBeforeSignOutCanSwitchItsAccount() = authTest {
        val started = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>(); var signOutRequests = 0
        val storage = MemorySessionManager(session())
        val updated = user.copy(userMetadata = buildJsonObject { put(DISPLAY_NAME_KEY, "Chosen") })
        val connection = SupabaseConnection(configuration, storage, setupAuthPlatform = false) {
            MockEngine { request ->
                if (request.url.encodedPath == "/auth/v1/user") {
                    started.complete(Unit); finish.await()
                    respond(Json.encodeToString(UserInfo.serializer(), updated), headers = headersOf("Content-Type", "application/json"))
                } else {
                    assertEquals("/auth/v1/logout", request.url.encodedPath); signOutRequests++
                    respond("{}", headers = headersOf("Content-Type", "application/json"))
                }
            }
        }
        try {
            val repository = SupabaseAccountRepository(connection)
            coroutineScope {
                val write = async { repository.updateDisplayName(user.id, "Chosen") }
                started.await(); val out = async { repository.signOut() }; yield()
                assertEquals(0, signOutRequests)
                finish.complete(Unit); write.await(); out.await()
            }
            assertEquals(1, signOutRequests); assertNull(storage.loadSession())
            assertEquals(AccountSession(), repository.sessions.first())
        } finally { finish.complete(Unit); connection.close() }
    }

}
