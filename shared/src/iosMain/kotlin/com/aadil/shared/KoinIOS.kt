package com.aadil.shared

import com.aadil.spool.core.data.di.dataModule
import com.aadil.spool.data.di.platformDatabaseModule
import com.aadil.spool.feature.dashboard.di.dashboardModule
import com.aadil.spool.feature.details.di.detailsModule
import com.aadil.spool.feature.entry.di.entryModule
import com.aadil.spool.feature.history.di.historyModule
import com.aadil.spool.feature.settings.di.settingsModule
import org.koin.core.context.startKoin

fun startKoinIOS() {
    startKoin {
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
