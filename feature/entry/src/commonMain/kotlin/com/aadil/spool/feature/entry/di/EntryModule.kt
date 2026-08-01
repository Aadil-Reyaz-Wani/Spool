package com.aadil.spool.feature.entry.di

import com.aadil.spool.feature.entry.SpoolEntryViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val entryModule: Module = module {
    viewModelOf(::SpoolEntryViewModel)
}
