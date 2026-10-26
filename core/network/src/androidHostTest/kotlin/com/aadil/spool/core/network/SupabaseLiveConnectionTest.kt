package com.aadil.spool.core.network

import com.aadil.spool.core.network.di.networkModule
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.koin.dsl.koinApplication
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SupabaseLiveConnectionTest {
    @Test
    fun configuredAppClientReachesSupabase() {
        assumeTrue("Opt in with -PsupabaseLive=true", System.getProperty("spool.supabase.live") == "true")
        runBlocking {
            val app = koinApplication { modules(networkModule) }
            val connection = app.koin.get<SupabaseConnection>()
            try {
                val result = connection.checkConnection()
                println("Supabase app-client probe: $result")
                assertEquals(200, assertIs<SupabaseConnectionResult.Http>(result).statusCode)
            } finally {
                connection.close()
                app.close()
            }
        }
    }
}
