package com.aadil.spool.core.network

import com.aadil.spool.core.model.RemoteInventorySnapshot
import io.github.jan.supabase.auth.auth
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

sealed interface InventorySnapshotReply {
    data class Snapshot(val value: RemoteInventorySnapshot) : InventorySnapshotReply
    data object NotAuthenticated : InventorySnapshotReply
    data object Unavailable : InventorySnapshotReply
}

interface InventoryConflictRemote {
    suspend fun snapshot(ownerId: String, spoolUuid: String): InventorySnapshotReply
}

internal class SupabaseInventoryConflictRemote(private val connection: SupabaseConnection) : InventoryConflictRemote {
    override suspend fun snapshot(ownerId: String, spoolUuid: String): InventorySnapshotReply = try {
        withTimeoutOrNull(30_000) {
            val client = connection.client ?: return@withTimeoutOrNull InventorySnapshotReply.NotAuthenticated
            client.auth.awaitInitialization()
            val session = client.auth.currentSessionOrNull() ?: return@withTimeoutOrNull InventorySnapshotReply.NotAuthenticated
            if (ownerId.isBlank() || session.user?.id != ownerId || session.expiresAt <= Clock.System.now()) {
                return@withTimeoutOrNull InventorySnapshotReply.NotAuthenticated
            }
            val response = client.httpClient.request(checkNotNull(connection.inventorySnapshotUrl)) {
                method = HttpMethod.Post
                header("Authorization", "Bearer ${session.accessToken}")
                header("Content-Type", "application/json")
                setBody(buildJsonObject { put("p_expected_owner", ownerId); put("p_spool_uuid", spoolUuid) }.toString())
            }
            when (response.status.value) {
                200 -> {
                    val value = Json { ignoreUnknownKeys = true }.decodeFromString<RemoteInventorySnapshot>(response.bodyAsText())
                    validateInventorySnapshot(value, ownerId)
                    require(value.spool.uuid == spoolUuid)
                    InventorySnapshotReply.Snapshot(value)
                }
                401, 403 -> InventorySnapshotReply.NotAuthenticated
                else -> InventorySnapshotReply.Unavailable
            }
        } ?: InventorySnapshotReply.Unavailable
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // Never expose either version, credentials or server errors through logging.
        InventorySnapshotReply.Unavailable
    }
}
