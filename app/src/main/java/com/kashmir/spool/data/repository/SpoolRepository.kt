package com.kashmir.spool.data.repository

import com.kashmir.spool.data.entity.Filament
import kotlinx.coroutines.flow.Flow

interface SpoolRepository {

    suspend fun insertSpool(filament: Filament) // This is DONE
    suspend fun updateSpool(filament: Filament)
    suspend fun deleteSpool(filament: Filament)
    fun getAllSpoolsStream(): Flow<List<Filament>> // This one is also Completed
    fun getSpoolStream(id: Int): Flow<Filament>

}