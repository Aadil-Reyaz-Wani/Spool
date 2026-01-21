package com.aadil.spool.ui.screens.details

import android.annotation.SuppressLint
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aadil.spool.data.entity.Filament
import com.aadil.spool.data.entity.UsageLog
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
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SpoolDetailsViewModel @Inject constructor(
    private val spoolRepository: SpoolRepository
) : ViewModel() {

    private val _printObjectUiState = MutableStateFlow(PrintObjectUiState())
    val printObjectUiState = _printObjectUiState.asStateFlow()
    private val _isError = MutableStateFlow<String?>(null)
    val isError = _isError.asStateFlow()

    fun quickDeductionUpdateField(gramsUsed: String, printTitle: String, isFailed: Boolean) {
        _printObjectUiState.value = _printObjectUiState.value.copy(
            gramsUsed = gramsUsed,
            printTitle = printTitle,
            isFailed = isFailed
        )
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

    // Subtract weight from current weight
    @SuppressLint("SuspiciousIndentation")
    fun deductCurrentWeight(id: Int, deductedWeight: String) {
        val newUsageLog = _printObjectUiState.value.toUsageLog().copy(spoolId = id)
        val weight = spoolDetails.value.currentWeight
        if (deductedWeight.isNotBlank()) {
            val newCurrentWeight = weight - deductedWeight.toDouble()
            viewModelScope.launch {
                try {
                    if (newCurrentWeight >= 0 && _printObjectUiState.value.printTitle.isNotBlank()) {
                        spoolRepository.updateCurrentWeight(id, newCurrentWeight)
                        spoolRepository.insertSpoolUsageLog(newUsageLog)
                        _printObjectUiState.value = PrintObjectUiState()
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

    fun validateInputErrorsOfPrintObjectUiState() {
        if (_printObjectUiState.value.gramsUsed.isNotBlank()){
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


data class PrintObjectUiState(
    val id: Int = 0,
    val spoolId: Int = 0,
    val gramsUsed: String = "",
    val printTitle: String = "",
    val isFailed: Boolean = false
)

fun PrintObjectUiState.toUsageLog(): UsageLog {
    return UsageLog(
        id = id,
        spoolId = spoolId,
        gramsUsed = gramsUsed.toDoubleOrNull() ?: 0.0,
        title = printTitle,
        isFailure = isFailed,
        timestamp = System.currentTimeMillis(),
    )
}

















