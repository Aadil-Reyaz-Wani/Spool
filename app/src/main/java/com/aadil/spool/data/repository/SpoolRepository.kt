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

    fun getUniqueBrandStream(): Flow<List<String>>
    fun getUniqueMaterialTypeStream(): Flow<List<String>>
    fun getUniqueColorHexStream(): Flow<List<Long>>

    fun getSpoolsByBrandStream(brand: String): Flow<List<Filament>>
    fun getSpoolsByMaterialTypeStream(material: String): Flow<List<Filament>>
    fun getSpoolsByColorHexStream(colorHex: Long): Flow<List<Filament>>

    // Usage Log
    suspend fun insertSpoolUsageLog(log: UsageLog)
    suspend fun deleteSpoolUsageLog(log: UsageLog)
    suspend fun updateSpoolUsageLog(log: UsageLog)
    fun getSpoolUsageStream(spoolId: Int) : Flow<List<UsageLog>>

    fun getUsageLogById(id: Int): Flow<UsageLog?>
    suspend fun deleteLogAndRestoreCurrentWeight(usageLog: UsageLog)

    suspend fun editLogAndRestoreCurrentWeight(usageLog: UsageLog)

}