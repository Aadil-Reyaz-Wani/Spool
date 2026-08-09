package com.aadil.spool.core.data.repository

import com.aadil.spool.data.dao.SpoolDao
import com.aadil.spool.data.entity.Filament
import com.aadil.spool.data.entity.UsageLog
import kotlinx.coroutines.flow.Flow

interface SpoolRepository {
    fun getAllSpools(): Flow<List<Filament>>
    fun getAllSpoolsStream(): Flow<List<Filament>> = getAllSpools()

    fun getSpool(id: Int): Flow<Filament?>
    fun getSpoolStream(id: Int): Flow<Filament?> = getSpool(id)

    suspend fun insertSpool(filament: Filament)
    suspend fun updateSpool(filament: Filament)
    suspend fun deleteSpool(filament: Filament)

    suspend fun getCurrentWeight(id: Int): Double
    suspend fun getCurrentWeightStream(id: Int): Double = getCurrentWeight(id)

    suspend fun updateCurrentWeight(id: Int, currentWeight: Double)

    fun getUniqueBrand(): Flow<List<String>>
    fun getUniqueBrandStream(): Flow<List<String>> = getUniqueBrand()

    fun getUniqueMaterialType(): Flow<List<String>>
    fun getUniqueMaterialTypeStream(): Flow<List<String>> = getUniqueMaterialType()

    fun getUniqueColorHex(): Flow<List<Long>>
    fun getUniqueColorHexStream(): Flow<List<Long>> = getUniqueColorHex()

    fun getSpoolsByBrand(brand: String): Flow<List<Filament>>
    fun getSpoolsByBrandStream(brand: String): Flow<List<Filament>> = getSpoolsByBrand(brand)

    fun getSpoolsByMaterialType(material: String): Flow<List<Filament>>
    fun getSpoolsByMaterialTypeStream(material: String): Flow<List<Filament>> = getSpoolsByMaterialType(material)

    fun getSpoolsByColorHex(colorHex: Long): Flow<List<Filament>>
    fun getSpoolsByColorHexStream(colorHex: Long): Flow<List<Filament>> = getSpoolsByColorHex(colorHex)

    suspend fun insertUsageLog(log: UsageLog)
    suspend fun insertSpoolUsageLog(log: UsageLog) = insertUsageLog(log)

    suspend fun deleteUsageLog(log: UsageLog)
    suspend fun deleteSpoolUsageLog(log: UsageLog) = deleteUsageLog(log)

    suspend fun updateUsageLog(log: UsageLog)
    suspend fun updateSpoolUsageLog(log: UsageLog) = updateUsageLog(log)

    fun getSpoolUsage(spoolId: Int): Flow<List<UsageLog>>
    fun getSpoolUsageStream(spoolId: Int): Flow<List<UsageLog>> = getSpoolUsage(spoolId)

    fun getUsageLogById(id: Int): Flow<UsageLog?>
    suspend fun updateAllUsageCosts(spoolId: Int, pricePerGram: Double)
    suspend fun getCurrentLogWeight(id: Int): Double
    suspend fun deleteLogAndRestoreCurrentWeight(usageLog: UsageLog)
    suspend fun editLogAndRestoreCurrentWeight(usageLog: UsageLog): Boolean
}

class DefaultSpoolRepository(
    private val spoolDao: SpoolDao
) : SpoolRepository {
    override fun getAllSpools(): Flow<List<Filament>> = spoolDao.getAllSpools()
    override fun getSpool(id: Int): Flow<Filament?> = spoolDao.getSpool(id)
    override suspend fun insertSpool(filament: Filament) = spoolDao.insertSpool(filament)
    override suspend fun updateSpool(filament: Filament) = spoolDao.updateSpool(filament)
    override suspend fun deleteSpool(filament: Filament) = spoolDao.deleteSpool(filament)
    override suspend fun getCurrentWeight(id: Int): Double = spoolDao.getCurrentWeight(id)
    override suspend fun updateCurrentWeight(id: Int, currentWeight: Double) = spoolDao.updateCurrentWeight(id, currentWeight)
    override fun getUniqueBrand(): Flow<List<String>> = spoolDao.getUniqueBrand()
    override fun getUniqueMaterialType(): Flow<List<String>> = spoolDao.getUniqueMaterialType()
    override fun getUniqueColorHex(): Flow<List<Long>> = spoolDao.getUniqueColorHex()
    override fun getSpoolsByBrand(brand: String): Flow<List<Filament>> = spoolDao.getSpoolsByBrand(brand)
    override fun getSpoolsByMaterialType(material: String): Flow<List<Filament>> = spoolDao.getSpoolsByMaterialType(material)
    override fun getSpoolsByColorHex(colorHex: Long): Flow<List<Filament>> = spoolDao.getSpoolsByColorHex(colorHex)

    override suspend fun insertUsageLog(log: UsageLog) = spoolDao.insertUsageLog(log)
    override suspend fun deleteUsageLog(log: UsageLog) = spoolDao.deleteUsageLog(log)
    override suspend fun updateUsageLog(log: UsageLog) = spoolDao.updateUsageLog(log)
    override fun getSpoolUsage(spoolId: Int): Flow<List<UsageLog>> = spoolDao.getSpoolUsage(spoolId)
    override fun getUsageLogById(id: Int): Flow<UsageLog?> = spoolDao.getUsageLogById(id)
    override suspend fun updateAllUsageCosts(spoolId: Int, pricePerGram: Double) = spoolDao.updateAllUsageCosts(spoolId, pricePerGram)
    override suspend fun getCurrentLogWeight(id: Int): Double = spoolDao.getCurrentLogWeight(id)
    override suspend fun deleteLogAndRestoreCurrentWeight(usageLog: UsageLog) = spoolDao.deleteLogAndRestoreCurrentWeight(usageLog)
    override suspend fun editLogAndRestoreCurrentWeight(usageLog: UsageLog): Boolean = spoolDao.editLogAndRestoreCurrentWeight(usageLog)
}
