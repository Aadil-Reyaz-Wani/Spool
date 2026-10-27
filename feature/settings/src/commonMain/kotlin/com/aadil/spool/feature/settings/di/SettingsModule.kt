package com.aadil.spool.feature.settings.di

import com.aadil.spool.feature.settings.AccountViewModel
import com.aadil.spool.feature.settings.SpoolSettingsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val settingsModule: Module = module {
    viewModelOf(::SpoolSettingsViewModel)
    viewModel { AccountViewModel(get(), get()) }
}
