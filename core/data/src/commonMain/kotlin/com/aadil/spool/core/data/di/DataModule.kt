package com.aadil.spool.core.data.di

import com.aadil.spool.core.data.repository.CurrencyPreferencesRepository
import com.aadil.spool.core.data.repository.DefaultCurrencyPreferencesRepository
import com.aadil.spool.core.data.repository.DefaultSpoolRepository
import com.aadil.spool.core.data.repository.SpoolRepository
import org.koin.core.module.Module
import org.koin.dsl.module

expect val platformDataStoreModule: Module

val dataModule = module {
    includes(platformDataStoreModule)
    single<SpoolRepository> { DefaultSpoolRepository(get()) }
    single<CurrencyPreferencesRepository> { DefaultCurrencyPreferencesRepository(get()) }
}
