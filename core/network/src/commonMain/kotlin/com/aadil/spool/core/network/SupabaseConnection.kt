package com.aadil.spool.core.network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.minimalConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.logging.LogLevel
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.HttpClientEngine
import io.ktor.http.HttpMethod
import kotlin.coroutines.cancellation.CancellationException

internal expect fun createSupabaseEngine(): HttpClientEngine

sealed interface SupabaseConnectionResult {
    data object NotConfigured : SupabaseConnectionResult
    data object InvalidConfiguration : SupabaseConnectionResult
    data class Http(val statusCode: Int) : SupabaseConnectionResult
    data object NetworkUnavailable : SupabaseConnectionResult
}

class SupabaseConnection internal constructor(
    private val configuration: SupabaseConfiguration,
    private val sessionManager: SessionManager? = null,
    private val setupAuthPlatform: Boolean = sessionManager != null,
    private val engineFactory: () -> HttpClientEngine = ::createSupabaseEngine,
) {
    private val engineDelegate = lazy(engineFactory)
    private val clientDelegate = lazy { createClient() }

    internal val inventoryUploadUrl: String?
        get() = (configuration as? SupabaseConfiguration.Configured)?.url?.let { "$it/rest/v1/rpc/upload_inventory_batch" }

    internal val inventoryPullUrl: String?
        get() = (configuration as? SupabaseConfiguration.Configured)?.url?.let { "$it/rest/v1/rpc/pull_inventory" }

    internal val accountSessionsEnabled: Boolean get() = sessionManager != null

    /** Shared Auth/Storage client, or null when configuration is missing/invalid. */
    val client: SupabaseClient? by clientDelegate

    /** Explicit, read-only probe. Never invoked by startup or inventory code. */
    suspend fun checkConnection(): SupabaseConnectionResult {
        when (configuration) {
            SupabaseConfiguration.NotConfigured -> return SupabaseConnectionResult.NotConfigured
            SupabaseConfiguration.Invalid -> return SupabaseConnectionResult.InvalidConfiguration
            is SupabaseConfiguration.Configured -> Unit
        }
        val supabase = checkNotNull(client)
        return try {
            // The SDK supplies apikey; publishable keys must not be sent as bearer JWTs.
            val response = supabase.httpClient.request("${configuration.url}/auth/v1/settings") {
                method = HttpMethod.Get
            }
            SupabaseConnectionResult.Http(response.status.value)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            SupabaseConnectionResult.NetworkUnavailable
        }
    }

    /** For owners with a shorter lifetime than the application (including tests). */
    suspend fun close() {
        try {
            if (clientDelegate.isInitialized()) client?.close()
        } finally {
            if (engineDelegate.isInitialized()) engineDelegate.value.close()
        }
    }

    @OptIn(SupabaseInternal::class)
    private fun createClient(): SupabaseClient? {
        val config = configuration as? SupabaseConfiguration.Configured ?: return null
        return createSupabaseClient(config.url, config.publishableKey) {
            httpEngine = engineDelegate.value
            defaultLogLevel = LogLevel.NONE
            osInformation = null
            httpConfig {
                expectSuccess = false
                followRedirects = false
            }
            install(Auth) {
                minimalConfig()
                autoSetupPlatform = false
                this@SupabaseConnection.sessionManager?.let { manager ->
                    this.sessionManager = manager
                    autoLoadFromStorage = true
                    autoSaveToStorage = true
                    alwaysAutoRefresh = true
                    autoSetupPlatform = setupAuthPlatform
                }
            }
            install(Storage)
        }
    }
}
