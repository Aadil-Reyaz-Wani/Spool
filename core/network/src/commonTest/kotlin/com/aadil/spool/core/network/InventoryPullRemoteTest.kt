package com.aadil.spool.core.network

import com.aadil.spool.core.model.InventoryPullPage
import com.aadil.spool.core.model.RemoteInventorySnapshot
import com.aadil.spool.core.model.RemoteInventorySpool
import com.aadil.spool.core.model.RemoteInventoryLog
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

class InventoryPullRemoteTest {
    private val owner = "00000000-0000-4000-8000-0000000000a1"
    private val uuid = "10000000-0000-4000-8000-000000000001"
    private val session get() = UserSession("pull-access-secret", "pull-refresh-secret", expiresIn = 3600,
        tokenType = "bearer", user = UserInfo(aud = "authenticated", id = owner))
    private fun snapshot() = RemoteInventorySnapshot(RemoteInventorySpool(owner, uuid, 1, null,
        "Test", "PLA", "Black", 123, 1000.0, 900.0, 200, 60, "", 20.0, 123, null, null, null, null),
        listOf(RemoteInventoryLog(owner, "20000000-0000-4000-8000-000000000001", uuid, null, "Print", 10.0, 123, false, 2.0)))
    private fun page() = InventoryPullPage(owner, 0, 2, false, snapshot())
    private fun body(page: InventoryPullPage = page()) = Json.encodeToString(InventoryPullPage.serializer(), page)

    @Test fun boundTokenOwnerAndCursorAreSentAndCompleteAggregateIsDecoded() = runTest {
        withContext(Dispatchers.Default) {
            var requests = 0
            val connection = SupabaseConnection(SupabaseConfiguration.parse(TEST_URL, TEST_KEY), MemorySessionManager(session), setupAuthPlatform = false) {
                MockEngine { req ->
                    requests++
                    assertEquals("/rest/v1/rpc/pull_inventory", req.url.encodedPath)
                    assertEquals("Bearer pull-access-secret", req.headers["Authorization"])
                    val input = Json.parseToJsonElement((req.body as TextContent).text).jsonObject
                    assertEquals(owner, input.getValue("p_expected_owner").jsonPrimitive.content)
                    assertEquals("0", input.getValue("p_cursor").jsonPrimitive.content)
                    respond(body(), headers = headersOf("Content-Type", "application/json"))
                }
            }
            try {
                val remote = SupabaseInventoryPullRemote(connection)
                assertEquals(InventoryPullReply.Page(page()), remote.pull(owner, 0))
                assertEquals(InventoryPullReply.NotAuthenticated, remote.pull("other-account", 0))
                connection.client!!.auth.clearSession()
                assertEquals(InventoryPullReply.NotAuthenticated, remote.pull(owner, 0))
                assertEquals(1, requests)
            } finally { connection.close() }
        }
    }

    @Test fun invalidPageOwnershipCursorRevisionAndRelationshipsAreRejected() = runTest {
        withContext(Dispatchers.Default) {
            val good = page()
            val snapshot = snapshot()
            val invalid = listOf(good.copy(ownerId = "b"), good.copy(cursor = 1), good.copy(nextCursor = 0),
                good.copy(snapshot = null), good.copy(nextCursor = 0, snapshot = null, hasMore = true),
                good.copy(snapshot = snapshot.copy(spool = snapshot.spool.copy(ownerId = "b"))),
                good.copy(snapshot = snapshot.copy(spool = snapshot.spool.copy(revision = 0))),
                good.copy(snapshot = snapshot.copy(spool = snapshot.spool.copy(currentWeight = -1.0))),
                good.copy(snapshot = snapshot.copy(spool = snapshot.spool.copy(uuid = "not-a-uuid"))),
                good.copy(snapshot = snapshot.copy(logs = snapshot.logs + snapshot.logs)),
                good.copy(snapshot = snapshot.copy(logs = listOf(snapshot.logs.single().copy(ownerId = "b")))),
                good.copy(snapshot = snapshot.copy(logs = listOf(snapshot.logs.single().copy(spoolUuid = "other")))),
                good.copy(snapshot = snapshot.copy(logs = listOf(snapshot.logs.single().copy(gramsUsed = -1.0)))))
            for (page in invalid) {
                val connection = SupabaseConnection(SupabaseConfiguration.parse(TEST_URL, TEST_KEY), MemorySessionManager(session), setupAuthPlatform = false) {
                    MockEngine { respond(body(page), headers = headersOf("Content-Type", "application/json")) }
                }
                try { assertEquals(InventoryPullReply.Rejected, SupabaseInventoryPullRemote(connection).pull(owner, 0)) }
                finally { connection.close() }
            }
        }
    }

    @Test fun emptyPageIsValidAndMalformedResponsesAndHttpFailuresAreSanitized() = runTest {
        withContext(Dispatchers.Default) {
            val empty = InventoryPullPage(owner, 0, 0, false, null)
            for ((status, text, expected) in listOf(
                Triple(HttpStatusCode.OK, body(empty), InventoryPullReply.Page(empty)),
                Triple(HttpStatusCode.OK, "{}", InventoryPullReply.Rejected),
                Triple(HttpStatusCode.OK, "sensitive invalid body", InventoryPullReply.Rejected),
                Triple(HttpStatusCode.Unauthorized, "sensitive", InventoryPullReply.NotAuthenticated),
                Triple(HttpStatusCode.Forbidden, "sensitive", InventoryPullReply.NotAuthenticated),
                Triple(HttpStatusCode.NotFound, "sensitive", InventoryPullReply.Rejected),
                Triple(HttpStatusCode.TooManyRequests, "sensitive", InventoryPullReply.RetryLater),
                Triple(HttpStatusCode.InternalServerError, "sensitive", InventoryPullReply.RetryLater))) {
                val connection = SupabaseConnection(SupabaseConfiguration.parse(TEST_URL, TEST_KEY), MemorySessionManager(session), setupAuthPlatform = false) {
                    MockEngine { respond(text, status, headers = headersOf("Content-Type", "application/json")) }
                }
                try { assertEquals(expected, SupabaseInventoryPullRemote(connection).pull(owner, 0)) }
                finally { connection.close() }
            }
            val offline = SupabaseConnection(SupabaseConfiguration.parse(TEST_URL, TEST_KEY), MemorySessionManager(session), setupAuthPlatform = false) {
                MockEngine { throw IOException("Offline") }
            }
            try { assertEquals(InventoryPullReply.RetryLater, SupabaseInventoryPullRemote(offline).pull(owner, 0)) }
            finally { offline.close() }
        }
    }
}
