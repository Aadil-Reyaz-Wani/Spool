package com.aadil.spool.core.data.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.aadil.spool.core.data.DataStoreFactory
import com.aadil.spool.core.data.analytics.AnalyticsBackend
import com.aadil.spool.core.data.analytics.FirebaseAnalyticsBackend
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformDataStoreModule: Module = module {
    single<AnalyticsBackend> { FirebaseAnalyticsBackend(get()) }
    single<DataStore<Preferences>> {
        DataStoreFactory(get()).create()
    }
}
