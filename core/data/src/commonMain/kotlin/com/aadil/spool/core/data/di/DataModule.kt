package com.aadil.spool.core.data.di

import com.aadil.spool.core.data.sync.InventoryRecoveryRepository
import com.aadil.spool.core.data.sync.LocalInventoryRecoveryRepository
import com.aadil.spool.core.data.sync.InventoryConflictRepository
import com.aadil.spool.core.data.sync.InventoryConflictResolver
import com.aadil.spool.core.data.sync.InventoryDownloader
import com.aadil.spool.core.data.sync.InventoryUploader
import com.aadil.spool.core.data.sync.InventoryUploadCoordinator
import com.aadil.spool.core.data.repository.InventoryNotifications
import com.aadil.spool.core.data.repository.InventorySession
import com.aadil.spool.core.data.repository.CurrencyPreferencesRepository
import com.aadil.spool.core.data.repository.DefaultCurrencyPreferencesRepository
import com.aadil.spool.core.data.repository.DefaultNotificationPreferencesRepository
import com.aadil.spool.core.data.repository.DefaultSpoolRepository
import com.aadil.spool.core.data.repository.NotificationPreferencesRepository
import com.aadil.spool.core.data.repository.SpoolRepository
import com.aadil.spool.core.data.analytics.AnalyticsConsentStore
import com.aadil.spool.core.data.analytics.DataStoreAnalyticsConsent
import com.aadil.spool.core.data.analytics.UsageAnalytics
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose

expect val platformDataStoreModule: Module

val dataModule = module {
    includes(platformDataStoreModule)
    single<AnalyticsConsentStore> { DataStoreAnalyticsConsent(get()) }
    single { UsageAnalytics(get(), get()) } onClose { it?.close() }
    single { InventorySession(get(), get(), getOrNull<InventoryNotifications>(), get(), syncDao = get()) } onClose { it?.close() }
    single { InventoryUploader(get(), get(), get(), analytics = get()) }
    single<InventoryRecoveryRepository> { LocalInventoryRecoveryRepository(get(), get()) }
    single<InventoryConflictRepository> { InventoryConflictResolver(get(), get(), get()) }
    single { InventoryDownloader(get(), get(), get(), analytics = get()) }
    single { InventoryUploadCoordinator(get(), get(), get(), get(), get(), downloader = get()) } onClose { it?.close() }
    factory<SpoolRepository> {
        val session = get<InventorySession>()
        DefaultSpoolRepository(get(), session, checkNotNull(session.scope.value), analytics = get())
    }
    single<CurrencyPreferencesRepository> { DefaultCurrencyPreferencesRepository(get()) }
    single<NotificationPreferencesRepository> { DefaultNotificationPreferencesRepository(get()) }
}
