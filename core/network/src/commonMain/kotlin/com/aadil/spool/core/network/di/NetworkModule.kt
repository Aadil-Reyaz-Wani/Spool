package com.aadil.spool.core.network.di

import com.aadil.spool.core.network.InventoryConflictRemote
import com.aadil.spool.core.network.SupabaseInventoryConflictRemote
import com.aadil.spool.core.network.InventoryPullRemote
import com.aadil.spool.core.network.SupabaseInventoryPullRemote
import com.aadil.spool.core.network.InventoryUploadRemote
import com.aadil.spool.core.network.SupabaseInventoryUploadRemote
import com.aadil.spool.core.network.AccountRepository
import com.aadil.spool.core.network.SupabaseAccountRepository
import io.github.jan.supabase.auth.SessionManager
import org.koin.core.module.Module
import com.aadil.spool.core.network.SupabaseConfiguration
import com.aadil.spool.core.network.SupabaseConnection
import org.koin.dsl.module

internal expect val platformNetworkModule: Module

val networkModule = module {
    includes(platformNetworkModule)
    single { SupabaseConfiguration.fromBuildConfig() }
    single {
        val configuration = get<SupabaseConfiguration>()
        SupabaseConnection(configuration, sessionManager = if (configuration is SupabaseConfiguration.Configured) getOrNull<SessionManager>() else null)
    }
    single<InventoryConflictRemote> { SupabaseInventoryConflictRemote(get()) }
    single<InventoryPullRemote> { SupabaseInventoryPullRemote(get()) }
    single<InventoryUploadRemote> { SupabaseInventoryUploadRemote(get()) }
    single<AccountRepository> { SupabaseAccountRepository(get()) }
}
