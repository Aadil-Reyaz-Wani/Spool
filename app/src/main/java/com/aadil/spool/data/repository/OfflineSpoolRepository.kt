package com.aadil.spool.data.repository

import com.aadil.spool.data.dao.SpoolDao
import com.aadil.spool.data.entity.Filament
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

    override fun getSpoolStream(id: Int): Flow<Filament?> {
        return spoolDao.getSpool(id = id)
    }

    override fun getCurrentWeightStream(id: Int): Double {
        return spoolDao.getCurrentWeight(id = id)
    }

    override suspend fun updateCurrentWeight(id: Int, currentWeight: Double) {
        return spoolDao.updateCurrentWeight(id = id, currentWeight = currentWeight)
    }
}