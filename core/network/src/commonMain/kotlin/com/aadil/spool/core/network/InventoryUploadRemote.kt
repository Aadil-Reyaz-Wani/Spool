package com.aadil.spool.core.network

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

sealed interface InventoryUploadReply {
    data class Applied(val revision: Long) : InventoryUploadReply
    data class Conflict(val snapshot: String) : InventoryUploadReply
    data object NotAuthenticated : InventoryUploadReply
    data object RetryLater : InventoryUploadReply
    data object Rejected : InventoryUploadReply
}

interface InventoryUploadRemote {
    suspend fun upload(ownerId: String, request: String): InventoryUploadReply
}

internal class SupabaseInventoryUploadRemote(private val connection: SupabaseConnection) : InventoryUploadRemote {
    override suspend fun upload(ownerId: String, request: String): InventoryUploadReply {
        return try {
            withTimeoutOrNull(30_000) {
                val supabase = connection.client ?: return@withTimeoutOrNull InventoryUploadReply.NotAuthenticated
                supabase.auth.awaitInitialization()
                // Capture one account/token pair. Never let a later account supply auth for this request.
                val session = supabase.auth.currentSessionOrNull() ?: return@withTimeoutOrNull InventoryUploadReply.NotAuthenticated
                if (ownerId.isBlank() || session.user?.id != ownerId || session.expiresAt <= Clock.System.now()) {
                    return@withTimeoutOrNull InventoryUploadReply.NotAuthenticated
                }
                val envelope = Json.parseToJsonElement(request).jsonObject
                require(envelope["p_expected_owner"]?.jsonPrimitive?.content == ownerId)
                val response = supabase.httpClient.request(checkNotNull(connection.inventoryUploadUrl)) {
                    method = HttpMethod.Post
                    header("Authorization", "Bearer ${session.accessToken}")
                    header("Content-Type", "application/json")
                    setBody(request)
                }
                when (response.status.value) {
                    200 -> {
                        val result = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                        require(result["batch_id"] == envelope["p_batch_id"] && result["spool_uuid"] == envelope["p_spool_uuid"])
                        val revision = requireNotNull(result["revision"]?.jsonPrimitive?.longOrNull)
                        when (result["status"]?.jsonPrimitive?.content) {
                            "applied" -> {
                                require(revision > requireNotNull(envelope["p_base_revision"]?.jsonPrimitive?.longOrNull))
                                InventoryUploadReply.Applied(revision)
                            }
                            "conflict" -> {
                                require(revision >= 0 && result["server"] is kotlinx.serialization.json.JsonObject)
                                InventoryUploadReply.Conflict(result.toString())
                            }
                            else -> InventoryUploadReply.Rejected
                        }
                    }
                    401, 403 -> InventoryUploadReply.NotAuthenticated
                    400, 404, 409, 413, 422 -> InventoryUploadReply.Rejected
                    else -> InventoryUploadReply.RetryLater
                }
            } ?: InventoryUploadReply.RetryLater
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IllegalArgumentException) {
            InventoryUploadReply.Rejected
        } catch (_: Exception) {
            // Do not expose request snapshots, tokens or server error bodies through logs/errors.
            InventoryUploadReply.RetryLater
        }
    }
}
