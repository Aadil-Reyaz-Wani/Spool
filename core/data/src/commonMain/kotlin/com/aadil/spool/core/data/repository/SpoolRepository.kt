package com.aadil.spool.core.data.repository

import com.aadil.spool.data.dao.SpoolDao
import com.aadil.spool.data.entity.Filament
import com.aadil.spool.data.entity.UsageLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

interface SpoolRepository {
    suspend fun withActiveInventory(block: suspend () -> Unit) { block() }
    fun getAllSpools(): Flow<List<Filament>>
    fun getAllSpoolsStream(): Flow<List<Filament>> = getAllSpools()

    fun getSpool(id: Int): Flow<Filament?>
    fun getSpoolStream(id: Int): Flow<Filament?> = getSpool(id)

    suspend fun insertSpool(filament: Filament)
    suspend fun updateSpool(filament: Filament)
    suspend fun updateSpoolAndUsageCosts(filament: Filament)
    suspend fun deleteSpool(filament: Filament)

    suspend fun getCurrentWeight(id: Int): Double
    suspend fun getCurrentWeightStream(id: Int): Double = getCurrentWeight(id)

    suspend fun updateCurrentWeight(id: Int, currentWeight: Double)
    suspend fun updateCurrentWeightWithTare(id: Int, currentWeight: Double, tareGrams: Double)
    suspend fun markAsDried(id: Int, baselineWeight: Double, tareGrams: Double, driedAt: Long)

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
    private val spoolDao: SpoolDao,
    private val inventorySession: InventorySession,
    private val inventoryScope: InventoryScope,
) : SpoolRepository {
    private val ownerId get() = inventoryScope.ownerId

    private fun <T> visible(source: Flow<T>, hidden: T): Flow<T> =
        inventorySession.scope.combine(source) { active, value ->
            if (active == inventoryScope) value else hidden
        }

    private suspend fun <T> active(unavailable: T, block: suspend () -> T): T =
        inventorySession.withScope(inventoryScope, unavailable, block)

    private suspend fun write(block: suspend () -> Unit) { active(Unit, block) }

    override suspend fun withActiveInventory(block: suspend () -> Unit) = write(block)

    override fun getAllSpools(): Flow<List<Filament>> = visible(spoolDao.getOwnedSpools(ownerId), emptyList())
    override fun getSpool(id: Int): Flow<Filament?> = visible(spoolDao.getOwnedSpool(ownerId, id), null)
    override suspend fun insertSpool(filament: Filament) = write { spoolDao.insertOwnedSpool(ownerId, filament) }
    override suspend fun updateSpool(filament: Filament) = write { spoolDao.updateOwnedSpool(ownerId, filament, false) }
    override suspend fun updateSpoolAndUsageCosts(filament: Filament) = write { spoolDao.updateOwnedSpool(ownerId, filament, true) }
    override suspend fun deleteSpool(filament: Filament) = write { spoolDao.deleteOwnedSpool(ownerId, filament) }
    override suspend fun getCurrentWeight(id: Int): Double = active(0.0) { spoolDao.ownedSpool(ownerId, id)?.currentWeight ?: 0.0 }
    override suspend fun updateCurrentWeight(id: Int, currentWeight: Double) = write { spoolDao.updateOwnedWeight(ownerId, id, currentWeight) }
    override suspend fun updateCurrentWeightWithTare(id: Int, currentWeight: Double, tareGrams: Double) = write { spoolDao.updateOwnedWeightWithTare(ownerId, id, currentWeight, tareGrams) }
    override suspend fun markAsDried(id: Int, baselineWeight: Double, tareGrams: Double, driedAt: Long) = write { spoolDao.markOwnedAsDried(ownerId, id, baselineWeight, tareGrams, driedAt) }
    override fun getUniqueBrand(): Flow<List<String>> = getAllSpools().map { it.map(Filament::brand).distinct() }
    override fun getUniqueMaterialType(): Flow<List<String>> = getAllSpools().map { it.map(Filament::material).distinct() }
    override fun getUniqueColorHex(): Flow<List<Long>> = getAllSpools().map { it.map(Filament::colorHex).distinct() }
    override fun getSpoolsByBrand(brand: String): Flow<List<Filament>> = getAllSpools().map { rows -> rows.filter { it.brand == brand } }
    override fun getSpoolsByMaterialType(material: String): Flow<List<Filament>> = getAllSpools().map { rows -> rows.filter { it.material == material } }
    override fun getSpoolsByColorHex(colorHex: Long): Flow<List<Filament>> = getAllSpools().map { rows -> rows.filter { it.colorHex == colorHex } }

    override suspend fun insertUsageLog(log: UsageLog) = write { spoolDao.insertOwnedLog(ownerId, log) }
    override suspend fun deleteUsageLog(log: UsageLog) = write { spoolDao.deleteOwnedLog(ownerId, log, false) }
    override suspend fun updateUsageLog(log: UsageLog) = write { spoolDao.updateOwnedLog(ownerId, log, false); Unit }
    override fun getSpoolUsage(spoolId: Int): Flow<List<UsageLog>> = visible(spoolDao.getOwnedUsage(ownerId, spoolId), emptyList())
    override fun getUsageLogById(id: Int): Flow<UsageLog?> = visible(spoolDao.getOwnedLog(ownerId, id), null)
    override suspend fun updateAllUsageCosts(spoolId: Int, pricePerGram: Double) = write { spoolDao.updateOwnedUsageCosts(ownerId, spoolId, pricePerGram) }
    override suspend fun getCurrentLogWeight(id: Int): Double = active(0.0) { spoolDao.ownedLog(ownerId, id)?.gramsUsed ?: 0.0 }
    override suspend fun deleteLogAndRestoreCurrentWeight(usageLog: UsageLog) = write { spoolDao.deleteOwnedLog(ownerId, usageLog, true) }
    override suspend fun editLogAndRestoreCurrentWeight(usageLog: UsageLog): Boolean = active(false) { spoolDao.updateOwnedLog(ownerId, usageLog, true) }
}
