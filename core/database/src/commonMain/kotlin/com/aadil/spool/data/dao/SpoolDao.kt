package com.aadil.spool.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.IGNORE
import androidx.room.OnConflictStrategy.Companion.REPLACE
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
    @Insert(onConflict = REPLACE)
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
    suspend fun editLogAndRestoreCurrentWeight(usageLog: UsageLog) {
        val currentSpoolWeight = getCurrentWeight(usageLog.spoolId)
        val oldLog = getUsageLogById(usageLog.id).first() ?: return
        val oldWeight = oldLog.gramsUsed
        val restoredCurrentSpoolWeight = currentSpoolWeight + oldWeight
        val finalSpoolWeight = restoredCurrentSpoolWeight - usageLog.gramsUsed
        updateCurrentWeight(usageLog.spoolId, finalSpoolWeight)
        updateUsageLog(usageLog)
    }
}
