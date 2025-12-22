package com.aadil.spool.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.IGNORE
import androidx.room.Query
import androidx.room.Update
import com.aadil.spool.data.entity.Filament
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
    fun getCurrentWeight(id: Int) : Double

    @Query("UPDATE filaments SET current_weight = :currentWeight WHERE id = :id")
    suspend fun updateCurrentWeight(id: Int, currentWeight: Double)
}