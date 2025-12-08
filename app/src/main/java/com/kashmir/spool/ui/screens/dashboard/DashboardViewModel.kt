package com.kashmir.spool.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kashmir.spool.data.entity.Filament
import com.kashmir.spool.data.repository.SpoolRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.io.IOException

class DashboardViewModel(
    private val spoolRepository: SpoolRepository
) : ViewModel() {

    val getAllSpool : StateFlow<List<Filament>> =
            spoolRepository.getAllSpoolsStream()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList()
                )
}