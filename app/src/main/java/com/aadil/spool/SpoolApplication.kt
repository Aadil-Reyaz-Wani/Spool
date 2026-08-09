package com.aadil.spool

import android.app.Application
import com.aadil.spool.core.data.di.dataModule
import com.aadil.spool.data.di.platformDatabaseModule
import com.aadil.spool.feature.dashboard.di.dashboardModule
import com.aadil.spool.feature.details.di.detailsModule
import com.aadil.spool.feature.entry.di.entryModule
import com.aadil.spool.feature.history.di.historyModule
import com.aadil.spool.feature.settings.di.settingsModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class SpoolApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@SpoolApplication)
            modules(
                platformDatabaseModule,
                dataModule,
                dashboardModule,
                detailsModule,
                entryModule,
                historyModule,
                settingsModule
            )
        }
    }
}