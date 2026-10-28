package com.aadil.spool

import com.aadil.spool.core.data.repository.SpoolRepository
import com.aadil.spool.data.entity.Filament
import com.aadil.spool.data.entity.UsageLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

internal class FakeSpoolRepository(initial: List<Filament> = emptyList()) : SpoolRepository {
    val spools = MutableStateFlow(initial)
    val logs = MutableStateFlow<List<UsageLog>>(emptyList())
    var writes = 0
    var failSaving = false
    var saveGate: CompletableDeferred<Unit>? = null

    private suspend fun beforeWrite() {
        saveGate?.await()
        check(!failSaving) { "Storage unavailable" }
        writes++
    }

    override fun getAllSpools() = spools
    override fun getSpool(id: Int) = spools.map { rows -> rows.find { it.id == id } }
    override suspend fun insertSpool(filament: Filament) {
        beforeWrite()
        spools.value += filament.copy(id = (spools.value.maxOfOrNull { it.id } ?: 0) + 1)
    }
    override suspend fun updateSpool(filament: Filament) {
        beforeWrite()
        spools.value = spools.value.map { if (it.id == filament.id) filament else it }
    }
    override suspend fun updateSpoolAndUsageCosts(filament: Filament) {
        updateSpool(filament)
        updateAllUsageCosts(filament.id, filament.price / filament.totalWeight)
    }
    override suspend fun updateSpoolFromDraft(filament: Filament, expected: Filament): Boolean {
        if (spools.value.find { it.id == filament.id } != expected) return false
        updateSpoolAndUsageCosts(filament)
        return true
    }
    override suspend fun deleteSpool(filament: Filament) { spools.value = spools.value.filterNot { it.id == filament.id } }
    override suspend fun getCurrentWeight(id: Int) = spools.value.first { it.id == id }.currentWeight
    override suspend fun updateCurrentWeight(id: Int, currentWeight: Double) {
        updateSpool(spools.value.first { it.id == id }.copy(currentWeight = currentWeight))
    }
    override suspend fun updateCurrentWeightWithTare(id: Int, currentWeight: Double, tareGrams: Double) = error("Unused")
    override suspend fun markAsDried(id: Int, baselineWeight: Double, tareGrams: Double, driedAt: Long) = error("Unused")
    override fun getUniqueBrand() = spools.map { rows -> rows.map { it.brand }.distinct() }
    override fun getUniqueMaterialType() = spools.map { rows -> rows.map { it.material }.distinct() }
    override fun getUniqueColorHex() = spools.map { rows -> rows.map { it.colorHex }.distinct() }
    override fun getSpoolsByBrand(brand: String) = spools.map { rows -> rows.filter { it.brand == brand } }
    override fun getSpoolsByMaterialType(material: String) = spools.map { rows -> rows.filter { it.material == material } }
    override fun getSpoolsByColorHex(colorHex: Long) = spools.map { rows -> rows.filter { it.colorHex == colorHex } }
    override suspend fun insertLogAndDeductWeight(log: UsageLog): Boolean {
        val spool = spools.value.firstOrNull { it.id == log.spoolId } ?: return false
        if (log.gramsUsed > spool.currentWeight) return false
        beforeWrite()
        spools.value = spools.value.map { if (it.id == spool.id) it.copy(currentWeight = it.currentWeight - log.gramsUsed) else it }
        logs.value += log
        return true
    }
    override suspend fun insertUsageLog(log: UsageLog) { beforeWrite(); logs.value += log }
    override suspend fun deleteUsageLog(log: UsageLog) { logs.value = logs.value.filterNot { it.id == log.id } }
    override suspend fun updateUsageLog(log: UsageLog) {
        logs.value = logs.value.map { if (it.id == log.id) log else it }
    }
    override fun getSpoolUsage(spoolId: Int) = logs.map { rows -> rows.filter { it.spoolId == spoolId } }
    override fun getUsageLogById(id: Int) = logs.map { rows -> rows.find { it.id == id } }
    override suspend fun updateAllUsageCosts(spoolId: Int, pricePerGram: Double) {
        logs.value = logs.value.map { if (it.spoolId == spoolId) it.copy(pricePerPrint = it.gramsUsed * pricePerGram) else it }
    }
    override suspend fun getCurrentLogWeight(id: Int) = logs.value.first { it.id == id }.gramsUsed
    override suspend fun deleteLogAndRestoreCurrentWeight(usageLog: UsageLog) = error("Unused")
    override suspend fun editLogAndRestoreCurrentWeight(usageLog: UsageLog): Boolean {
        val old = logs.value.first { it.id == usageLog.id }
        val remaining = getCurrentWeight(usageLog.spoolId) + old.gramsUsed - usageLog.gramsUsed
        if (remaining < 0) return false
        updateCurrentWeight(usageLog.spoolId, remaining)
        updateUsageLog(usageLog)
        return true
    }
}

internal fun testSpool() = Filament(
    id = 7, brand = "TestCo", material = "PLA", colorName = "Black", colorHex = 0xFF000000,
    totalWeight = 1000.0, currentWeight = 1000.0, note = "Original note", price = 200.0,
    timestamp = 100, dryBaselineWeight = 995.0, dryBaselineTareGrams = 140.0,
    lastWeighedTareGrams = 140.0, lastDriedAt = 90,
)
