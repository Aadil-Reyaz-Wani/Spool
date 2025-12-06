package com.kashmir.spool.data.repository

import com.kashmir.spool.data.dao.SpoolDao
import com.kashmir.spool.data.entity.Filament
import kotlinx.coroutines.flow.Flow

class OfflineSpoolRepository(
    private val spoolDao: SpoolDao
) : SpoolRepository {
    override suspend fun insertSpool(filament: Filament) {
        return spoolDao.insertSpool(filament = filament)
    }

    override suspend fun deleteSpool(filament: Filament) {
        return spoolDao.deleteSpool(filament = filament)
    }

    override suspend fun updateSpool(filament: Filament) {
        return spoolDao.updateSpool(filament = filament)
    }

    override fun getAllSpoolsStream(): Flow<List<Filament>> {
        return spoolDao.getAllSpools()
    }
}