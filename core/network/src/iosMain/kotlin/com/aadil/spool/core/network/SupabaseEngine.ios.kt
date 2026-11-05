package com.aadil.spool.core.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

internal actual fun createSupabaseEngine(): HttpClientEngine = Darwin.create()
