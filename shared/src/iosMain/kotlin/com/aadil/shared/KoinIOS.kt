package com.aadil.shared

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
import com.aadil.spool.notifications.iosNotificationsModule
import org.koin.core.context.startKoin

fun startKoinIOS() {
    val application = startKoin {
        modules(
            platformDatabaseModule,
            dataModule,
            platformInventoryUploadModule,
            networkModule,
            iosNotificationsModule,
            dashboardModule,
            detailsModule,
            entryModule,
            historyModule,
            settingsModule
        )
    }
    application.koin.get<InventoryUploadCoordinator>().start()
}
