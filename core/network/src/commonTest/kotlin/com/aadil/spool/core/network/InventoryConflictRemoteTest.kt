package com.aadil.spool.core.network

import com.aadil.spool.core.model.*
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
import kotlinx.serialization.json.*
import kotlin.test.*

class InventoryConflictRemoteTest {
    private val owner = "00000000-0000-4000-8000-0000000000a1"
    private val uuid = "10000000-0000-4000-8000-000000000001"
    private val session get() = UserSession("snapshot-access-secret", "snapshot-refresh-secret", expiresIn = 3600,
        tokenType = "bearer", user = UserInfo(aud = "authenticated", id = owner))
    private fun snapshot() = RemoteInventorySnapshot(RemoteInventorySpool(owner, uuid, 2, null, "Test", "PLA", "Black", 123,
        1000.0, 800.0, 200, 60, "Note", 20.0, 123, null, null, null, null), emptyList())
    private fun text(value: RemoteInventorySnapshot = snapshot()) = Json.encodeToString(RemoteInventorySnapshot.serializer(), value)

    @Test fun capturesOneAccountTokenAndReadsOnlyRequestedAggregate() = runTest {
        withContext(Dispatchers.Default) {
            var requests = 0
            val connection = SupabaseConnection(SupabaseConfiguration.parse(TEST_URL, TEST_KEY), MemorySessionManager(session), setupAuthPlatform = false) {
                MockEngine { req ->
                    requests++
                    assertEquals("/rest/v1/rpc/get_inventory_snapshot", req.url.encodedPath)
                    assertEquals("Bearer snapshot-access-secret", req.headers["Authorization"])
                    val input = Json.parseToJsonElement((req.body as TextContent).text).jsonObject
                    assertEquals(owner, input.getValue("p_expected_owner").jsonPrimitive.content)
                    assertEquals(uuid, input.getValue("p_spool_uuid").jsonPrimitive.content)
                    respond(text(), headers = headersOf("Content-Type", "application/json"))
                }
            }
            try {
                val remote = SupabaseInventoryConflictRemote(connection)
                assertEquals(InventorySnapshotReply.Snapshot(snapshot()), remote.snapshot(owner, uuid))
                assertEquals(InventorySnapshotReply.NotAuthenticated, remote.snapshot("other", uuid))
                connection.client!!.auth.clearSession()
                assertEquals(InventorySnapshotReply.NotAuthenticated, remote.snapshot(owner, uuid))
                assertEquals(1, requests)
            } finally { connection.close() }
        }
    }

    @Test fun foreignInvalidAndMissingSnapshotsNeverBecomeReviewable() = runTest {
        withContext(Dispatchers.Default) {
            for (body in listOf(text(snapshot().copy(spool = snapshot().spool.copy(ownerId = "other"))),
                text(snapshot().copy(spool = snapshot().spool.copy(uuid = "10000000-0000-4000-8000-000000000002"))),
                text(snapshot().copy(spool = snapshot().spool.copy(revision = 0))),
                text(snapshot().copy(spool = snapshot().spool.copy(currentWeight = -1.0))),
                "{}", "{\"spool\":null,\"logs\":[]}", "sensitive invalid body")) {
                val connection = SupabaseConnection(SupabaseConfiguration.parse(TEST_URL, TEST_KEY), MemorySessionManager(session), setupAuthPlatform = false) {
                    MockEngine { respond(body, headers = headersOf("Content-Type", "application/json")) }
                }
                try { assertEquals(InventorySnapshotReply.Unavailable, SupabaseInventoryConflictRemote(connection).snapshot(owner, uuid)) }
                finally { connection.close() }
            }
        }
    }

    @Test fun classifiesAuthAndNetworkErrorsWithoutExposingServerBodies() = runTest {
        withContext(Dispatchers.Default) {
            for ((status, expected) in listOf(HttpStatusCode.Unauthorized to InventorySnapshotReply.NotAuthenticated,
                HttpStatusCode.Forbidden to InventorySnapshotReply.NotAuthenticated,
                HttpStatusCode.NotFound to InventorySnapshotReply.Unavailable,
                HttpStatusCode.TooManyRequests to InventorySnapshotReply.Unavailable)) {
                val connection = SupabaseConnection(SupabaseConfiguration.parse(TEST_URL, TEST_KEY), MemorySessionManager(session), setupAuthPlatform = false) {
                    MockEngine { respond("sensitive body", status) }
                }
                try { assertEquals(expected, SupabaseInventoryConflictRemote(connection).snapshot(owner, uuid)) }
                finally { connection.close() }
            }
            val offline = SupabaseConnection(SupabaseConfiguration.parse(TEST_URL, TEST_KEY), MemorySessionManager(session), setupAuthPlatform = false) {
                MockEngine { throw IOException("Offline") }
            }
            try { assertEquals(InventorySnapshotReply.Unavailable, SupabaseInventoryConflictRemote(offline).snapshot(owner, uuid)) }
            finally { offline.close() }
        }
    }
}
