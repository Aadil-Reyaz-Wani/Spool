package com.aadil.spool.data.repository

import com.aadil.spool.data.entity.Filament
import kotlinx.coroutines.flow.Flow

interface SpoolRepository {

    suspend fun insertSpool(filament: Filament)
    suspend fun updateSpool(filament: Filament)
    suspend fun deleteSpool(filament: Filament)
    fun getAllSpoolsStream(): Flow<List<Filament>>
    fun getSpoolStream(id: Int): Flow<Filament?>
    fun getCurrentWeightStream(id: Int) : Double
    suspend fun updateCurrentWeight(id: Int, currentWeight: Double)

}