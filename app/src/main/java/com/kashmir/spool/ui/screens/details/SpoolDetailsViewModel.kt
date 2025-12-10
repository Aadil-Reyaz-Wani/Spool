package com.kashmir.spool.ui.screens.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kashmir.spool.data.entity.Filament
import com.kashmir.spool.data.repository.SpoolRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

class SpoolDetailsViewModel(
    private val spoolRepository: SpoolRepository
) : ViewModel() {

    private val _idTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val spoolDetails: StateFlow<Filament> = _idTrigger
        .flatMapLatest { id->
            if (id == 0) {
                flowOf(
                    Filament(
                        id = 0,
                        brand = "",
                        material = "",
                        totalWeight = 0.0,
                        colorHex = 0xFF000000,
                        colorName = "",
                        currentWeight = 0.0,
                        tempBed = 0,
                        tempNozzle = 0,
                        note = ""
                    )
                )
            }else {
                spoolRepository.getSpoolStream(id)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Filament(
                id = 0,
                brand = "",
                material = "",
                totalWeight = 0.0,
                colorHex = 0xFF000000,
                colorName = "",
                currentWeight = 0.0,
                tempBed = 0,
                tempNozzle = 0,
                note = ""
            )
        )

    fun loadSpool(id: Int) {
        _idTrigger.value = id
    }
}

data class SpoolDetailsUiState(
    val id: Int = 0,
    val brand: String = "",
    val material: String = "",
    val totalWeight: Double? = 0.0,
    val colorHex: Long = 0xFF000000,
    val currentWeight: Double? = 0.0,
    val tempNozzle: Int = 0,
    val tempBed: Int = 0
)