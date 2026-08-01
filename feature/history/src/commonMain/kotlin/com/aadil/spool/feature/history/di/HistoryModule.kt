package com.aadil.spool.feature.history.di

import com.aadil.spool.feature.history.PrintHistoryViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val historyModule: Module = module {
    viewModelOf(::PrintHistoryViewModel)
}
