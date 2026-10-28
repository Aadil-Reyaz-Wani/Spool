package com.aadil.spool.feature.settings.di

import com.aadil.spool.feature.settings.InventoryRecoveryViewModel
import com.aadil.spool.feature.settings.InventoryConflictsViewModel
import com.aadil.spool.core.data.repository.InventorySession
import com.aadil.spool.core.data.repository.CurrencyPreferencesRepository
import com.aadil.spool.core.data.sync.InventoryUploadCoordinator
import com.aadil.spool.feature.settings.AccountViewModel
import com.aadil.spool.feature.settings.SpoolSettingsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val settingsModule: Module = module {
    viewModelOf(::SpoolSettingsViewModel)
    viewModel {
        val session = get<InventorySession>()
        val uploads = get<InventoryUploadCoordinator>()
        InventoryConflictsViewModel(get(), checkNotNull(session.scope.value), get(), get<CurrencyPreferencesRepository>().currencyCodeFlow, uploads::retry)
    }
    viewModel {
        val session = get<InventorySession>()
        val uploads = get<InventoryUploadCoordinator>()
        InventoryRecoveryViewModel(get(), checkNotNull(session.scope.value), get(), get<CurrencyPreferencesRepository>().currencyCodeFlow, uploads::retry)
    }
    viewModel { AccountViewModel(get(), get(), get(), get()) }
}
