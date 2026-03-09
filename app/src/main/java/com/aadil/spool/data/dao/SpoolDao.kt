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
    fun getSpool(id: Int) : Flow<Filament?>

    @Query("SELECT current_weight FROM filaments WHERE id = :id")
    suspend fun getCurrentWeight(id: Int) : Double

    @Query("UPDATE filaments SET current_weight = :currentWeight WHERE id = :id")
    suspend fun updateCurrentWeight(id: Int, currentWeight: Double)


    // Usage Log - Print History
    @Insert(onConflict = IGNORE)
    suspend fun insertUsageLog(log: UsageLog)

    @Delete
    suspend fun deleteUsageLog(log: UsageLog)

    @Update
    suspend fun updateUsageLog(log: UsageLog)

    @Query("SELECT * FROM usage_log WHERE spoolId = :spoolId ORDER BY timestamp DESC")
    fun getSpoolUsage(spoolId: Int) : Flow<List<UsageLog>>

    @Transaction
    suspend fun deleteLogAndRestoreCurrentWeight(usageLog: UsageLog) {
        val currentWeight = getCurrentWeight(usageLog.spoolId)

        val restoredCurrentWeight = currentWeight + usageLog.gramsUsed

        updateCurrentWeight(usageLog.spoolId, restoredCurrentWeight)

        deleteUsageLog(usageLog)
    }
}