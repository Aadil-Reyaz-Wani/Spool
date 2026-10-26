package com.aadil.spool.core.network

import io.ktor.http.Url

sealed interface SupabaseConfiguration {
    data object NotConfigured : SupabaseConfiguration
    data object Invalid : SupabaseConfiguration

    // No data-class toString: configuration must never print the key.
    class Configured internal constructor(
        val url: String,
        internal val publishableKey: String,
    ) : SupabaseConfiguration

    companion object {
        fun fromBuildConfig(): SupabaseConfiguration = parse(
            SupabaseBuildConfig.URL,
            SupabaseBuildConfig.PUBLISHABLE_KEY,
        )

        fun parse(url: String?, publishableKey: String?): SupabaseConfiguration {
            val origin = url.orEmpty().trim()
            val key = publishableKey.orEmpty().trim()
            if (origin.isEmpty() && key.isEmpty()) return NotConfigured
            if (!origin.matches(Regex("https://[A-Za-z0-9.-]+(?::443)?/?")) ||
                !key.matches(Regex("sb_publishable_[A-Za-z0-9_-]+"))
            ) return Invalid

            val parsed = runCatching { Url(origin) }.getOrNull() ?: return Invalid
            if (parsed.host.length > 253 || parsed.host.split('.').any { label ->
                    label.length !in 1..63 ||
                        !label.matches(Regex("[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?"))
                }
            ) return Invalid

            return Configured("https://${parsed.host}", key)
        }
    }
}
