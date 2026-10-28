package com.aadil.spool.sync

import com.aadil.spool.core.data.repository.InventoryScope
import com.aadil.spool.core.data.sync.InventoryUploadScheduler
import kotlinx.coroutines.flow.emptyFlow
import org.koin.dsl.module

actual val platformInventoryUploadModule = module {
    single<InventoryUploadScheduler> {
        object : InventoryUploadScheduler {
            // iOS sign-in is not enabled. Foreground retries work without promising iOS background execution.
            override val networkAvailable = emptyFlow<Unit>()
            override fun schedule(scope: InventoryScope) = Unit
            override fun cancel(scope: InventoryScope) = Unit
        }
    }
}
