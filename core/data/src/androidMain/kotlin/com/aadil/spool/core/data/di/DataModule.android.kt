package com.aadil.spool.core.data.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.aadil.spool.core.data.DataStoreFactory
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformDataStoreModule: Module = module {
    single<DataStore<Preferences>> {
        DataStoreFactory(get()).create()
    }
}
