package com.aadil.spool.core.network.di

import com.aadil.spool.core.network.SupabaseConfiguration
import com.aadil.spool.core.network.SupabaseConnection
import org.koin.dsl.module

val networkModule = module {
    single { SupabaseConfiguration.fromBuildConfig() }
    single { SupabaseConnection(get()) }
}
