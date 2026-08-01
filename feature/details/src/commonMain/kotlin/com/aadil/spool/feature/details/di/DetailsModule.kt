package com.aadil.spool.feature.details.di

import com.aadil.spool.feature.details.SpoolDetailsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val detailsModule: Module = module {
    viewModelOf(::SpoolDetailsViewModel)
}
