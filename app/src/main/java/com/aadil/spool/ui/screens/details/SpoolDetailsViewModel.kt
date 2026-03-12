package com.aadil.spool.ui.screens.details

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aadil.spool.data.entity.Filament
import com.aadil.spool.data.mapper.toUsageLog
import com.aadil.spool.data.repository.SpoolRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PrintObjectUiState(
    val id: Int = 0,
    val spoolId: Int = 0,
    val gramsUsed: String = "",
    val printTitle: String = "",
    val isFailed: Boolean = false
)

@HiltViewModel
class SpoolDetailsViewModel @Inject constructor(
    private val spoolRepository: SpoolRepository
) : ViewModel() {

    private val _printObjectUiState = MutableStateFlow(PrintObjectUiState())
    val printObjectUiState = _printObjectUiState.asStateFlow()
    private val _isError = MutableStateFlow<String?>(null)
    val isError = _isError.asStateFlow()

    fun quickDeductionUpdateField(gramsUsed: String, printTitle: String, isFailed: Boolean) {
        _printObjectUiState.update {
            it.copy(
                gramsUsed = gramsUsed,
                printTitle = printTitle,
                isFailed = isFailed
            )
        }
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

    // Deduct weight from current weight
    fun deductCurrentWeight(id: Int, inputWeight: String) {
        val weight = spoolDetails.value.currentWeight
        val pricePerGram = spoolDetails.value.price.div(spoolDetails.value.totalWeight)
        val totalCostPerPrint = pricePerGram.times(inputWeight.toDoubleOrNull() ?: 0.0)
        val newUsageLog = _printObjectUiState.value.toUsageLog()
            .copy(spoolId = id, pricePerPrint = totalCostPerPrint)
        if (inputWeight.isNotBlank()) {
            val parsedDeductedWeight = inputWeight.toDouble()
            val newCurrentWeight = weight - parsedDeductedWeight
            viewModelScope.launch {
                if (newCurrentWeight >= 0 && _printObjectUiState.value.printTitle.isNotBlank()) {
                    try {
                        spoolRepository.updateCurrentWeight(id, newCurrentWeight)
                        if (parsedDeductedWeight > 0) {
                            spoolRepository.insertSpoolUsageLog(newUsageLog)
                            _printObjectUiState.update { PrintObjectUiState() }
                        }
                    } catch (ae: Exception) {
                        Log.e("ERROR", ae.message.toString())
                    }
                }
            }
        }
    }

    fun loadSpool(id: Int) {
        _idTrigger.value = id
    }

    fun validateInputErrorsOfPrintObjectUiState() {
        if (_printObjectUiState.value.gramsUsed.isNotBlank()) {
            if (_printObjectUiState.value.printTitle.isBlank()) {
                _isError.value = "This field cannot be blank"
            } else {
                _isError.value = null
            }
        } else {
            return
        }
    }
}







