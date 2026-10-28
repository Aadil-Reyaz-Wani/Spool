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
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

@OptIn(SupabaseInternal::class)
class InventoryUploadRemoteTest {
    private val owner = "00000000-0000-4000-8000-0000000000a1"
    private val batch = "30000000-0000-4000-8000-000000000001"
    private val spool = "10000000-0000-4000-8000-000000000001"
    private val request get() = """{"p_expected_owner":"$owner","p_batch_id":"$batch","p_spool_uuid":"$spool","p_base_revision":0}"""
    private fun success(id: String = batch) = """{"status":"applied","batch_id":"$id","spool_uuid":"$spool","revision":1}"""
    private val session get() = UserSession("access-secret", "refresh-secret", expiresIn = 3600,
        tokenType = "bearer", user = UserInfo(aud = "authenticated", id = owner))

    @Test fun sendsOneBoundTokenAndValidatesAcknowledgement() = runTest {
        withContext(Dispatchers.Default) {
            val connection = SupabaseConnection(SupabaseConfiguration.parse(TEST_URL, TEST_KEY), MemorySessionManager(session), setupAuthPlatform = false) {
                MockEngine { req ->
                    assertEquals("/rest/v1/rpc/upload_inventory_batch", req.url.encodedPath)
                    assertEquals("Bearer access-secret", req.headers["Authorization"])
                    assertEquals(request, (req.body as TextContent).text)
                    respond(success(), headers = headersOf("Content-Type", "application/json"))
                }
            }
            try {
                assertEquals(InventoryUploadReply.Applied(1), SupabaseInventoryUploadRemote(connection).upload(owner, request))
                assertEquals(InventoryUploadReply.NotAuthenticated, SupabaseInventoryUploadRemote(connection).upload("different-owner", request))
            } finally { connection.close() }
        }
    }

    @Test fun rejectsMismatchedOrInvalidAcknowledgements() = runTest {
        withContext(Dispatchers.Default) {
            for (body in listOf(success("wrong-batch"), success().replace("\"revision\":1", "\"revision\":0"),
                success().replace(spool, "wrong-spool"), "{}")) {
                val connection = SupabaseConnection(SupabaseConfiguration.parse(TEST_URL, TEST_KEY), MemorySessionManager(session), setupAuthPlatform = false) {
                    MockEngine { respond(body, headers = headersOf("Content-Type", "application/json")) }
                }
                try { assertEquals(InventoryUploadReply.Rejected, SupabaseInventoryUploadRemote(connection).upload(owner, request)) }
                finally { connection.close() }
            }
        }
    }

    @Test fun classifiesAuthFailureNetworkFailureAndServerRejectionWithoutExposingBodies() = runTest {
        withContext(Dispatchers.Default) {
            for ((status, expected) in listOf(
                HttpStatusCode.Unauthorized to InventoryUploadReply.NotAuthenticated,
                HttpStatusCode.Forbidden to InventoryUploadReply.NotAuthenticated,
                HttpStatusCode.BadRequest to InventoryUploadReply.Rejected,
                HttpStatusCode.TooManyRequests to InventoryUploadReply.RetryLater,
                HttpStatusCode.InternalServerError to InventoryUploadReply.RetryLater,
            )) {
                val connection = SupabaseConnection(SupabaseConfiguration.parse(TEST_URL, TEST_KEY), MemorySessionManager(session), setupAuthPlatform = false) {
                    MockEngine { respond("sensitive server body", status) }
                }
                try { assertEquals(expected, SupabaseInventoryUploadRemote(connection).upload(owner, request)) }
                finally { connection.close() }
            }
            val offline = SupabaseConnection(SupabaseConfiguration.parse(TEST_URL, TEST_KEY), MemorySessionManager(session), setupAuthPlatform = false) {
                MockEngine { throw IOException("Offline") }
            }
            try { assertEquals(InventoryUploadReply.RetryLater, SupabaseInventoryUploadRemote(offline).upload(owner, request)) }
            finally { offline.close() }
        }
    }

    @Test fun conflictPreservesRemoteSnapshotAndSignedOutNeverSends() = runTest {
        withContext(Dispatchers.Default) {
            var requests = 0
            val connection = SupabaseConnection(SupabaseConfiguration.parse(TEST_URL, TEST_KEY), MemorySessionManager(session), setupAuthPlatform = false) {
                MockEngine {
                    requests++
                    respond("""{"status":"conflict","batch_id":"$batch","spool_uuid":"$spool","revision":2,"server":{"spool":{"note":"Remote edit"}}}""",
                        headers = headersOf("Content-Type", "application/json"))
                }
            }
            try {
                val remote = SupabaseInventoryUploadRemote(connection)
                val conflict = assertIs<InventoryUploadReply.Conflict>(remote.upload(owner, request))
                assertNotNull(Json.parseToJsonElement(conflict.snapshot).jsonObject["server"])
                connection.client!!.auth.clearSession()
                connection.client!!.auth.setSessionStatus(SessionStatus.NotAuthenticated())
                assertEquals(InventoryUploadReply.NotAuthenticated, remote.upload(owner, request))
                assertEquals(1, requests)
            } finally { connection.close() }
        }
    }
}
