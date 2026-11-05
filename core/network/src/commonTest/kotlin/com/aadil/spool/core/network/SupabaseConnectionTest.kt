package com.aadil.spool.core.network

import com.aadil.spool.core.network.di.networkModule
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.storage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class SupabaseConnectionTest {
    @Test
    fun missingOrInvalidConfigNeverCreatesAnEngine() = runTest {
        listOf(
            SupabaseConfiguration.NotConfigured to SupabaseConnectionResult.NotConfigured,
            SupabaseConfiguration.Invalid to SupabaseConnectionResult.InvalidConfiguration,
        ).forEach { (config, result) ->
            val connection = SupabaseConnection(config) { error("Engine must not be created") }
            assertNull(connection.client)
            assertEquals(result, connection.checkConnection())
            connection.close()
        }
    }

    @Test
    @OptIn(SupabaseInternal::class)
    fun clientIsSharedAndPassiveUntilExplicitProbe() = runTest {
        var engines = 0
        var requests = 0
        val connection = SupabaseConnection(SupabaseConfiguration.parse(TEST_URL, TEST_KEY)) {
            engines++
            MockEngine {
                requests++
                respond("{}")
            }
        }
        try {
            assertEquals(0, engines)
            val client = assertNotNull(connection.client)
            assertSame(client, connection.client)
            assertEquals(1, engines)
            assertEquals(0, requests)
            assertFalse(client.auth.config.autoLoadFromStorage)
            assertFalse(client.auth.config.autoSaveToStorage)
            assertFalse(client.auth.config.alwaysAutoRefresh)
            assertFalse(client.auth.config.autoSetupPlatform)
            assertNotNull(client.storage)
            assertEquals(SupabaseConnectionResult.Http(200), connection.checkConnection())
            assertEquals(1, requests)
        } finally {
            connection.close()
        }
    }

    @Test
    fun probeUsesSdkApiKeyWithoutBearerAndReturnsOnlyStatusIncludingErrorsAndRedirects() = runTest {
        listOf(200, 401, 403, 404, 500, 302).forEach { status ->
            var requests = 0
            val connection = SupabaseConnection(SupabaseConfiguration.parse(TEST_URL, TEST_KEY)) {
                MockEngine { request ->
                    requests++
                    assertEquals(HttpMethod.Get, request.method)
                    assertEquals("$TEST_URL/auth/v1/settings", request.url.toString())
                    assertEquals(listOf(TEST_KEY), request.headers.getAll("apikey"))
                    assertNull(request.headers[HttpHeaders.Authorization])
                    respond(
                        content = "private response content that must not escape the probe",
                        status = HttpStatusCode.fromValue(status),
                        headers = headersOf(HttpHeaders.Location, "https://elsewhere.example"),
                    )
                }
            }
            try {
                assertEquals(SupabaseConnectionResult.Http(status), connection.checkConnection())
                assertEquals(1, requests)
            } finally {
                connection.close()
            }
        }
    }

    @Test
    fun offlineErrorsAreSanitized() = runTest {
        val connection = SupabaseConnection(SupabaseConfiguration.parse(TEST_URL, TEST_KEY)) {
            MockEngine { throw IOException("Sensitive request details") }
        }
        try {
            assertEquals(SupabaseConnectionResult.NetworkUnavailable, connection.checkConnection())
        } finally {
            connection.close()
        }
    }

    @Test
    fun cancellationPropagates() = runTest {
        val connection = SupabaseConnection(SupabaseConfiguration.parse(TEST_URL, TEST_KEY)) {
            MockEngine { throw CancellationException("Cancelled probe") }
        }
        try {
            assertFailsWith<CancellationException> { connection.checkConnection() }
        } finally {
            connection.close()
        }
    }

    @Test
    fun koinCanStartWithoutConfigurationAndSharesConnection() = runTest {
        val app = koinApplication {
            modules(networkModule, module {
                single<SupabaseConfiguration> { SupabaseConfiguration.NotConfigured }
            })
        }
        try {
            val connection = app.koin.get<SupabaseConnection>()
            assertSame(connection, app.koin.get<SupabaseConnection>())
            assertEquals(SupabaseConnectionResult.NotConfigured, connection.checkConnection())
        } finally {
            app.close()
        }
    }
}
