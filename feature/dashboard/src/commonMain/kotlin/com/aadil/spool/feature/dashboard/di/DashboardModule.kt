package com.aadil.spool.feature.dashboard.di

import com.aadil.spool.feature.dashboard.DashboardViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val dashboardModule: Module = module {
    viewModelOf(::DashboardViewModel)
}
