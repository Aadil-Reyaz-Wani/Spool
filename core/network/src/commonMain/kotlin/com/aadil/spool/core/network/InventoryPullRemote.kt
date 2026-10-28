package com.aadil.spool.core.network

import com.aadil.spool.core.model.InventoryPullPage
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
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

sealed interface InventoryPullReply {
    data class Page(val value: InventoryPullPage) : InventoryPullReply
    data object NotAuthenticated : InventoryPullReply
    data object RetryLater : InventoryPullReply
    data object Rejected : InventoryPullReply
}

interface InventoryPullRemote {
    suspend fun pull(ownerId: String, cursor: Long): InventoryPullReply
}

internal class SupabaseInventoryPullRemote(private val connection: SupabaseConnection) : InventoryPullRemote {
    override suspend fun pull(ownerId: String, cursor: Long): InventoryPullReply = try {
        withTimeoutOrNull(30_000) {
            require(cursor >= 0)
            val client = connection.client ?: return@withTimeoutOrNull InventoryPullReply.NotAuthenticated
            client.auth.awaitInitialization()
            val session = client.auth.currentSessionOrNull() ?: return@withTimeoutOrNull InventoryPullReply.NotAuthenticated
            if (ownerId.isBlank() || session.user?.id != ownerId || session.expiresAt <= Clock.System.now()) {
                return@withTimeoutOrNull InventoryPullReply.NotAuthenticated
            }
            val response = client.httpClient.request(checkNotNull(connection.inventoryPullUrl)) {
                method = HttpMethod.Post
                header("Authorization", "Bearer ${session.accessToken}")
                header("Content-Type", "application/json")
                setBody(buildJsonObject { put("p_expected_owner", ownerId); put("p_cursor", cursor) }.toString())
            }
            when (response.status.value) {
                200 -> {
                    val page = Json { ignoreUnknownKeys = true }.decodeFromString<InventoryPullPage>(response.bodyAsText())
                    validatePullPage(page, ownerId, cursor)
                    InventoryPullReply.Page(page)
                }
                401, 403 -> InventoryPullReply.NotAuthenticated
                400, 404, 409, 413, 422 -> InventoryPullReply.Rejected
                else -> InventoryPullReply.RetryLater
            }
        } ?: InventoryPullReply.RetryLater
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: IllegalArgumentException) {
        InventoryPullReply.Rejected
    } catch (_: Exception) {
        // Never expose account tokens, inventory or server bodies in logs/errors.
        InventoryPullReply.RetryLater
    }
}

@OptIn(ExperimentalUuidApi::class)
internal fun validatePullPage(page: InventoryPullPage, ownerId: String, cursor: Long) {
    require(page.ownerId == ownerId && page.cursor == cursor && cursor >= 0)
    val snapshot = page.snapshot
    if (snapshot == null) {
        require(page.nextCursor == cursor && !page.hasMore)
        return
    }
    require(page.nextCursor > cursor)
    val spool = snapshot.spool
    require(spool.ownerId == ownerId && spool.revision > 0)
    Uuid.parse(spool.uuid)
    fun weight(value: Double) { require(value.isFinite() && value >= 0) }
    listOf(spool.totalWeight, spool.currentWeight, spool.price).forEach(::weight)
    listOfNotNull(spool.dryBaselineWeight, spool.dryBaselineTareGrams, spool.lastWeighedTareGrams).forEach(::weight)
    require(snapshot.logs.map { it.uuid }.toSet().size == snapshot.logs.size)
    for (log in snapshot.logs) {
        Uuid.parse(log.uuid)
        require(log.ownerId == ownerId && log.spoolUuid == spool.uuid)
        weight(log.gramsUsed)
        weight(log.pricePerPrint)
    }
}
