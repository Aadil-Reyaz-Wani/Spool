package com.aadil.spool

import android.app.Application
import com.aadil.spool.core.data.di.dataModule
import com.aadil.spool.core.data.sync.InventoryUploadCoordinator
import com.aadil.spool.sync.platformInventoryUploadModule
import com.aadil.spool.core.network.di.networkModule
import com.aadil.spool.data.di.platformDatabaseModule
import com.aadil.spool.feature.dashboard.di.dashboardModule
import com.aadil.spool.feature.details.di.detailsModule
import com.aadil.spool.feature.entry.di.entryModule
import com.aadil.spool.feature.history.di.historyModule
import com.aadil.spool.feature.settings.di.settingsModule
import com.aadil.spool.notifications.androidNotificationsModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class SpoolApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val application = startKoin {
            androidLogger()
            androidContext(this@SpoolApplication)
            modules(
                platformDatabaseModule,
                dataModule,
                platformInventoryUploadModule,
                networkModule,
                androidNotificationsModule,
                dashboardModule,
                detailsModule,
                entryModule,
                historyModule,
                settingsModule
            )
        }
        application.koin.get<InventoryUploadCoordinator>().start()
    }
}
