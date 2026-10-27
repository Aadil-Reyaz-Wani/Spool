package com.aadil.spool.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.IGNORE
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.aadil.spool.data.entity.Filament
import com.aadil.spool.data.entity.UsageLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

@Dao
interface SpoolDao {

    @Insert(onConflict = IGNORE)
    suspend fun insertSpool(filament: Filament)

    @Update
    suspend fun updateSpool(filament: Filament)

    @Delete
    suspend fun deleteSpool(filament: Filament)

    @Query("SELECT * FROM filaments ORDER BY timestamp DESC")
    fun getAllSpools(): Flow<List<Filament>>

    @Query("SELECT * FROM filaments WHERE id = :id")
    fun getSpool(id: Int): Flow<Filament?>

    @Query("SELECT current_weight FROM filaments WHERE id = :id")
    suspend fun getCurrentWeight(id: Int): Double

    @Query("UPDATE filaments SET current_weight = :currentWeight WHERE id = :id")
    suspend fun updateCurrentWeight(id: Int, currentWeight: Double)

    @Query("UPDATE filaments SET current_weight = :currentWeight, last_weighed_tare_grams = :tareGrams WHERE id = :id")
    suspend fun updateCurrentWeightWithTare(id: Int, currentWeight: Double, tareGrams: Double)

    @Query("UPDATE filaments SET current_weight = :baselineWeight, last_weighed_tare_grams = :tareGrams, dry_baseline_weight = :baselineWeight, dry_baseline_tare_grams = :tareGrams, last_dried_at = :driedAt WHERE id = :id")
    suspend fun markAsDried(id: Int, baselineWeight: Double, tareGrams: Double, driedAt: Long)

    @Query("SELECT DISTINCT brand FROM filaments")
    fun getUniqueBrand(): Flow<List<String>>

    @Query("SELECT DISTINCT material FROM filaments")
    fun getUniqueMaterialType(): Flow<List<String>>

    @Query("SELECT DISTINCT color_hex FROM filaments")
    fun getUniqueColorHex(): Flow<List<Long>>

    @Query("SELECT * FROM filaments WHERE brand = :brand")
    fun getSpoolsByBrand(brand: String): Flow<List<Filament>>

    @Query("SELECT * FROM filaments WHERE material = :material")
    fun getSpoolsByMaterialType(material: String): Flow<List<Filament>>

    @Query("SELECT * FROM filaments WHERE color_hex = :colorHex")
    fun getSpoolsByColorHex(colorHex: Long): Flow<List<Filament>>

    // Usage Log - Print History
    // A duplicate UUID must not replace and delete an existing history record.
    @Insert
    suspend fun insertUsageLog(log: UsageLog)

    @Delete
    suspend fun deleteUsageLog(log: UsageLog)

    @Update
    suspend fun updateUsageLog(log: UsageLog)

    @Query("SELECT * FROM usage_log WHERE spoolId = :spoolId ORDER BY timestamp DESC")
    fun getSpoolUsage(spoolId: Int): Flow<List<UsageLog>>

    @Query("SELECT * FROM usage_log WHERE id = :id")
    fun getUsageLogById(id: Int): Flow<UsageLog?>

    @Query("UPDATE usage_log SET price_per_print = grams_used * :pricePerGram WHERE spoolId = :spoolId")
    suspend fun updateAllUsageCosts(spoolId: Int, pricePerGram: Double)

    @Transaction
    suspend fun updateSpoolAndUsageCosts(filament: Filament) {
        updateSpool(filament)
        updateAllUsageCosts(filament.id, filament.price / filament.totalWeight)
    }

    @Query("SELECT grams_used FROM usage_log WHERE id = :id ")
    suspend fun getCurrentLogWeight(id: Int): Double

    @Transaction
    suspend fun deleteLogAndRestoreCurrentWeight(usageLog: UsageLog) {
        val currentWeight = getCurrentWeight(usageLog.spoolId)
        val restoredCurrentWeight = currentWeight + usageLog.gramsUsed
        updateCurrentWeight(usageLog.spoolId, restoredCurrentWeight)
        deleteUsageLog(usageLog)
    }

    @Transaction
    suspend fun editLogAndRestoreCurrentWeight(usageLog: UsageLog): Boolean {
        val currentSpoolWeight = getCurrentWeight(usageLog.spoolId)
        val oldLog = getUsageLogById(usageLog.id).first() ?: return false
        val oldWeight = oldLog.gramsUsed
        val restoredCurrentSpoolWeight = currentSpoolWeight + oldWeight
        val finalSpoolWeight = restoredCurrentSpoolWeight - usageLog.gramsUsed
        if (finalSpoolWeight < 0) return false
        updateCurrentWeight(usageLog.spoolId, finalSpoolWeight)
        updateUsageLog(usageLog)
        return true
    }

    // Ownership is inherited by usage history through its spool foreign key.
    @Query("SELECT * FROM filaments WHERE owner_id = :ownerId ORDER BY timestamp DESC")
    fun getOwnedSpools(ownerId: String): Flow<List<Filament>>

    @Query("SELECT * FROM filaments WHERE id = :id AND owner_id = :ownerId")
    fun getOwnedSpool(ownerId: String, id: Int): Flow<Filament?>

    @Query("SELECT * FROM filaments WHERE id = :id AND owner_id = :ownerId")
    suspend fun ownedSpool(ownerId: String, id: Int): Filament?

    @Query("SELECT l.* FROM usage_log l INNER JOIN filaments f ON f.id = l.spoolId WHERE f.owner_id = :ownerId AND l.spoolId = :spoolId ORDER BY l.timestamp DESC")
    fun getOwnedUsage(ownerId: String, spoolId: Int): Flow<List<UsageLog>>

    @Query("SELECT l.* FROM usage_log l INNER JOIN filaments f ON f.id = l.spoolId WHERE f.owner_id = :ownerId AND l.id = :id")
    fun getOwnedLog(ownerId: String, id: Int): Flow<UsageLog?>

    @Query("SELECT l.* FROM usage_log l INNER JOIN filaments f ON f.id = l.spoolId WHERE f.owner_id = :ownerId AND l.id = :id")
    suspend fun ownedLog(ownerId: String, id: Int): UsageLog?

    @Query("SELECT COUNT(*) FROM filaments WHERE owner_id = ''")
    fun guestSpoolCount(): Flow<Int>

    @Query("UPDATE filaments SET owner_id = :ownerId WHERE owner_id = ''")
    suspend fun claimGuestSpools(ownerId: String): Int

    @Transaction
    suspend fun importGuestInventory(ownerId: String): Int {
        require(ownerId.isNotBlank())
        // One atomic ownership transfer; IDs, UUIDs, history and every inventory value stay intact.
        return claimGuestSpools(ownerId)
    }

    @Transaction
    suspend fun insertOwnedSpool(ownerId: String, filament: Filament) {
        if (filament.id != 0) return
        insertSpool(filament.copy(ownerId = ownerId))
    }

    @Transaction
    suspend fun updateOwnedSpool(ownerId: String, filament: Filament, updateCosts: Boolean) {
        val stored = ownedSpool(ownerId, filament.id) ?: return
        if (filament.ownerId != ownerId || stored.uuid != filament.uuid) return
        if (updateCosts) updateSpoolAndUsageCosts(filament) else updateSpool(filament)
    }

    @Transaction
    suspend fun deleteOwnedSpool(ownerId: String, filament: Filament) {
        val stored = ownedSpool(ownerId, filament.id) ?: return
        if (stored.uuid == filament.uuid) deleteSpool(stored)
    }

    @Query("UPDATE filaments SET current_weight = :weight WHERE id = :id AND owner_id = :ownerId")
    suspend fun updateOwnedWeight(ownerId: String, id: Int, weight: Double)

    @Query("UPDATE filaments SET current_weight = :weight, last_weighed_tare_grams = :tare WHERE id = :id AND owner_id = :ownerId")
    suspend fun updateOwnedWeightWithTare(ownerId: String, id: Int, weight: Double, tare: Double)

    @Query("UPDATE filaments SET current_weight = :weight, last_weighed_tare_grams = :tare, dry_baseline_weight = :weight, dry_baseline_tare_grams = :tare, last_dried_at = :driedAt WHERE id = :id AND owner_id = :ownerId")
    suspend fun markOwnedAsDried(ownerId: String, id: Int, weight: Double, tare: Double, driedAt: Long)

    @Transaction
    suspend fun insertOwnedLog(ownerId: String, log: UsageLog) {
        if (log.id == 0 && ownedSpool(ownerId, log.spoolId) != null) insertUsageLog(log)
    }

    @Transaction
    suspend fun updateOwnedLog(ownerId: String, log: UsageLog, restoreWeight: Boolean): Boolean {
        val stored = ownedLog(ownerId, log.id) ?: return false
        if (stored.uuid != log.uuid || stored.spoolId != log.spoolId) return false
        if (restoreWeight) return editLogAndRestoreCurrentWeight(log)
        updateUsageLog(log)
        return true
    }

    @Transaction
    suspend fun deleteOwnedLog(ownerId: String, log: UsageLog, restoreWeight: Boolean) {
        val stored = ownedLog(ownerId, log.id) ?: return
        if (stored.uuid != log.uuid || stored.spoolId != log.spoolId) return
        if (restoreWeight) deleteLogAndRestoreCurrentWeight(stored) else deleteUsageLog(stored)
    }

    @Transaction
    suspend fun updateOwnedUsageCosts(ownerId: String, id: Int, pricePerGram: Double) {
        if (ownedSpool(ownerId, id) != null) updateAllUsageCosts(id, pricePerGram)
    }
}
