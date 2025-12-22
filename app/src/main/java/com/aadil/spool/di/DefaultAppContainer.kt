package com.aadil.spool.di

import android.content.Context
import com.aadil.spool.data.SpoolDatabase
import com.aadil.spool.data.repository.OfflineSpoolRepository
import com.aadil.spool.data.repository.SpoolRepository

class DefaultAppContainer(
    private val context: Context
): AppContainer {

    override val spoolRepository: SpoolRepository by lazy {
        OfflineSpoolRepository(
            SpoolDatabase.getDatabase(context = context).spoolDao()
        )
    }
}