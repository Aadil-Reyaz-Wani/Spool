package com.aadil.spool.data.di

import com.aadil.spool.data.SpoolDatabase
import com.aadil.spool.data.SpoolDatabaseBuilder
import com.aadil.spool.data.getSpoolDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformDatabaseModule: Module = module {
    single<SpoolDatabase> {
        getSpoolDatabase(SpoolDatabaseBuilder().create())
    }
    single { get<SpoolDatabase>().spoolDao() }
}
