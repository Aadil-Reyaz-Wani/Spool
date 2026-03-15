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

    @Query("SELECT * FROM filaments")
    fun getAllSpools(): Flow<List<Filament>>

    @Query("SELECT * FROM filaments WHERE id = :id")
    fun getSpool(id: Int): Flow<Filament?>

    @Query("SELECT current_weight FROM filaments WHERE id = :id")
    suspend fun getCurrentWeight(id: Int): Double

    @Query("UPDATE filaments SET current_weight = :currentWeight WHERE id = :id")
    suspend fun updateCurrentWeight(id: Int, currentWeight: Double)


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

    @Query("SELECT grams_used FROM usage_log WHERE id = :id ")
    fun getCurrentLogWeight(id: Int): Double

    @Transaction
    suspend fun deleteLogAndRestoreCurrentWeight(usageLog: UsageLog) {
        val currentWeight = getCurrentWeight(usageLog.spoolId)

        val restoredCurrentWeight = currentWeight + usageLog.gramsUsed

        updateCurrentWeight(usageLog.spoolId, restoredCurrentWeight)

        deleteUsageLog(usageLog)
    }

    @Transaction
    suspend fun editLogAndRestoreCurrentWeight(usageLog: UsageLog) {
        val currentSpoolWeight = getCurrentWeight(usageLog.spoolId) // 900

        val oldLog = getUsageLogById(usageLog.id).first() ?: return
        val oldWeight = oldLog.gramsUsed // 100

        val restoredCurrentSpoolWeight = currentSpoolWeight + oldWeight // 900 + 100 = 1000
        val finalSpoolWeight = restoredCurrentSpoolWeight - usageLog.gramsUsed // 1000 - 300 = 700

        updateCurrentWeight(usageLog.spoolId, finalSpoolWeight) // 700

        updateUsageLog(usageLog) // 300
    }
}