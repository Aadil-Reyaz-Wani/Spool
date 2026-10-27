package com.aadil.spool.core.network.di

import android.content.Context
import com.aadil.spool.core.network.AndroidSessionManager
import com.aadil.spool.core.network.SupabaseConfiguration
import io.github.jan.supabase.auth.SessionManager
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val platformNetworkModule: Module = module {
    single<SessionManager> {
        val configuration = get<SupabaseConfiguration>() as SupabaseConfiguration.Configured
        AndroidSessionManager(get<Context>(), configuration.url)
    }
}
