package com.aadil.spool.data.repository

import com.aadil.spool.data.dao.SpoolDao
import com.aadil.spool.data.entity.Filament
import com.aadil.spool.data.entity.UsageLog
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class OfflineSpoolRepository @Inject constructor(
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

    override suspend fun insertSpoolUsageLog(log: UsageLog) {
        return spoolDao.insertUsageLog(log = log)
    }

    override suspend fun deleteSpoolUsageLog(log: UsageLog) {
        return spoolDao.deleteUsageLog(log = log)
    }

    override suspend fun updateSpoolUsageLog(log: UsageLog) {
        spoolDao.updateUsageLog(log = log)
    }

    override fun getSpoolUsageStream(spoolId: Int): Flow<List<UsageLog>> {
        return spoolDao.getSpoolUsage(spoolId = spoolId)
    }
}