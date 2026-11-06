package com.aadil.spool.core.data.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.aadil.spool.core.data.DataStoreFactory
import com.aadil.spool.core.data.analytics.AnalyticsBackend
import com.aadil.spool.core.data.analytics.UsageEvent
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformDataStoreModule: Module = module {
    single<AnalyticsBackend> {
        object : AnalyticsBackend {
            override val available = false
            override fun setCollectionEnabled(enabled: Boolean) = Unit
            override fun record(event: UsageEvent) = Unit
        }
    }
    single<DataStore<Preferences>> {
        DataStoreFactory().create()
    }
}
