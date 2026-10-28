package com.aadil.spool.core.data.di

import com.aadil.spool.core.data.sync.InventoryUploader
import com.aadil.spool.core.data.repository.InventoryNotifications
import com.aadil.spool.core.data.repository.InventorySession
import com.aadil.spool.core.data.repository.CurrencyPreferencesRepository
import com.aadil.spool.core.data.repository.DefaultCurrencyPreferencesRepository
import com.aadil.spool.core.data.repository.DefaultNotificationPreferencesRepository
import com.aadil.spool.core.data.repository.DefaultSpoolRepository
import com.aadil.spool.core.data.repository.NotificationPreferencesRepository
import com.aadil.spool.core.data.repository.SpoolRepository
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose

expect val platformDataStoreModule: Module

val dataModule = module {
    includes(platformDataStoreModule)
    single { InventorySession(get(), get(), getOrNull<InventoryNotifications>(), get()) } onClose { it?.close() }
    single { InventoryUploader(get(), get(), get()) }
    factory<SpoolRepository> {
        val session = get<InventorySession>()
        DefaultSpoolRepository(get(), session, checkNotNull(session.scope.value))
    }
    single<CurrencyPreferencesRepository> { DefaultCurrencyPreferencesRepository(get()) }
    single<NotificationPreferencesRepository> { DefaultNotificationPreferencesRepository(get()) }
}
