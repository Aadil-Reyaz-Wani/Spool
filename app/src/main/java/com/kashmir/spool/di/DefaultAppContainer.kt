package com.kashmir.spool.di

import android.content.Context
import com.kashmir.spool.data.SpoolDatabase
import com.kashmir.spool.data.repository.OfflineSpoolRepository
import com.kashmir.spool.data.repository.SpoolRepository

class DefaultAppContainer(
    private val context: Context
): AppContainer {

    override val spoolRepository: SpoolRepository by lazy {
        OfflineSpoolRepository(
            SpoolDatabase.getDatabase(context = context).spoolDao()
        )
    }
}