package com.kashmir.spool.ui.screens.details

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kashmir.spool.data.entity.Filament
import com.kashmir.spool.data.repository.SpoolRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SpoolDetailsViewModel(
    private val spoolRepository: SpoolRepository
) : ViewModel() {

    private val _printWeight = MutableStateFlow("")
    var printWeight = _printWeight.asStateFlow()

    fun quickDeductionUpdateField(newValue: String) {
        _printWeight.value = newValue
    }


    private val _idTrigger = MutableStateFlow(0)
    private val emptyFilament = Filament(
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

    @OptIn(ExperimentalCoroutinesApi::class)
    val spoolDetails: StateFlow<Filament> = _idTrigger
        .flatMapLatest { id ->
            if (id == 0) {
                flowOf(emptyFilament)
            } else {
                spoolRepository.getSpoolStream(id)
            }
        }
        .filterNotNull()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyFilament
        )

    fun deleteSpool(filament: Filament) {
        viewModelScope.launch {
            spoolRepository.deleteSpool(filament)
        }
    }

    fun deductCurrentWeight(id: Int, deductedWeight: String) {
        val weight = spoolDetails.value.currentWeight

            if (deductedWeight.isNotBlank()) {
                val newCurrentWeight = weight - deductedWeight.toDouble()
                viewModelScope.launch {
                    try {
                        if (newCurrentWeight > 0) {
                            spoolRepository.updateCurrentWeight(id, newCurrentWeight)
                            _printWeight.value = ""
                        }
                    } catch (ae: ArithmeticException) {
                        println("Please enter the valid number")
                        Log.e("ERROR", ae.message.toString())
                    }
                }
            }

    }

    fun loadSpool(id: Int) {
        _idTrigger.value = id
    }
}




















