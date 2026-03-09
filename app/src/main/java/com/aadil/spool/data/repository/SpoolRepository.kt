package com.aadil.spool.data.repository

import com.aadil.spool.data.entity.Filament
import com.aadil.spool.data.entity.UsageLog
import kotlinx.coroutines.flow.Flow

interface SpoolRepository {

    suspend fun insertSpool(filament: Filament)
    suspend fun updateSpool(filament: Filament)
    suspend fun deleteSpool(filament: Filament)
    fun getAllSpoolsStream(): Flow<List<Filament>>
    fun getSpoolStream(id: Int): Flow<Filament?>
    suspend fun getCurrentWeightStream(id: Int) : Double
    suspend fun updateCurrentWeight(id: Int, currentWeight: Double)

    // Usage Log
    suspend fun insertSpoolUsageLog(log: UsageLog)
    suspend fun deleteSpoolUsageLog(log: UsageLog)
    suspend fun updateSpoolUsageLog(log: UsageLog)
    fun getSpoolUsageStream(spoolId: Int) : Flow<List<UsageLog>>

    suspend fun deleteLogAndRestoreCurrentWeight(usageLog: UsageLog)

}