package com.aadil.spool.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

internal const val TEST_URL = "https://project.supabase.co"
internal const val TEST_KEY = "sb_publishable_test_only_not_a_real_key"

class SupabaseConfigurationTest {
    @Test
    fun missingAndPartialConfigurationAreExplicit() {
        assertEquals(SupabaseConfiguration.NotConfigured, SupabaseConfiguration.parse(null, "  "))
        assertEquals(SupabaseConfiguration.Invalid, SupabaseConfiguration.parse(TEST_URL, null))
        assertEquals(SupabaseConfiguration.Invalid, SupabaseConfiguration.parse(null, TEST_KEY))
    }

    @Test
    fun rejectsUnsafeOrigins() {
        listOf(
            "http://project.supabase.co", "project.supabase.co", "https://",
            "https://user:password@project.supabase.co", "$TEST_URL/auth/v1",
            "$TEST_URL?key=value", "$TEST_URL#fragment", "$TEST_URL:80",
            "https://bad host.supabase.co", "https://project..supabase.co",
            "https://-project.supabase.co", "https://project.supabase.co\\evil",
            "$TEST_URL\nInjected: header",
        ).forEach { url ->
            assertEquals(SupabaseConfiguration.Invalid, SupabaseConfiguration.parse(url, TEST_KEY))
        }
    }

    @Test
    fun acceptsOnlyModernMobilePublishableKeys() {
        listOf(
            "sb_secret_test_only", "service_role", "sbp_management_token",
            "database-password", "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoic2VydmljZV9yb2xlIn0.test",
            "sb_publishable_", "sb_publishable_bad key", "$TEST_KEY\r\nInjected: header",
        ).forEach { key ->
            assertEquals(SupabaseConfiguration.Invalid, SupabaseConfiguration.parse(TEST_URL, key))
        }
    }

    @Test
    fun normalizesOriginAndDoesNotPrintKey() {
        val config = assertIs<SupabaseConfiguration.Configured>(
            SupabaseConfiguration.parse(" $TEST_URL:443/ ", " $TEST_KEY ")
        )
        assertEquals(TEST_URL, config.url)
        assertFalse(config.toString().contains(TEST_KEY))
    }
}
