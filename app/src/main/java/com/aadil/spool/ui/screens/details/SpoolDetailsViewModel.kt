package com.aadil.spool.ui.screens.details

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aadil.spool.data.entity.Filament
import com.aadil.spool.data.entity.UsageLog
import com.aadil.spool.data.mapper.toUsageLog
import com.aadil.spool.data.repository.SpoolRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
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

    // This function is used to update the current weight from the details as well as from the print-history.
    // What it actually does is, it updates the current weight when a user deletes or updates the particular log from the print-history
    // and also update the current weight when a user prints something.
    fun deductCurrentWeight(id: Int, inputWeight: String) {
        val currentState = _printObjectUiState.value
        val parsedDeductedWeight = inputWeight.toDoubleOrNull() ?: 0.0

        // Safety check: Don't do anything if title is blank or weight is 0
        if (parsedDeductedWeight <= 0 || currentState.printTitle.isBlank()) return

        // Calculate cost
        val filament = spoolDetails.value
        val pricePerGram = if (filament.totalWeight > 0) filament.price / filament.totalWeight else 0.0
        val totalCost = pricePerGram * parsedDeductedWeight

        val newUsageLog = currentState.toUsageLog().copy(
            id = currentState.id,
            spoolId = id,
            pricePerPrint = totalCost
        )

        viewModelScope.launch {
            try {
                if (currentState.id == 0) {
                    val newCurrentWeight = filament.currentWeight - parsedDeductedWeight
                    if (newCurrentWeight >= 0) {
                        spoolRepository.updateCurrentWeight(id, newCurrentWeight)
                        spoolRepository.insertSpoolUsageLog(newUsageLog)
                    }
                } else {
                    spoolRepository.editLogAndRestoreCurrentWeight(newUsageLog)
                }

                // Clear the dialog state
                _printObjectUiState.update { PrintObjectUiState() }
            } catch (ae: Exception) {
                Log.e("ERROR", ae.message.toString())
            }
        }
    }

    fun loadSpool(id: Int) {
        _idTrigger.value = id
    }

    fun validateInputErrorsOfPrintObjectUiState() {
        val currentState = _printObjectUiState.value
        if (currentState.gramsUsed.isNotBlank()) {
            if (currentState.printTitle.isBlank() || currentState.gramsUsed.isBlank()) {
                _isError.update { "This field cannot be blank" }
            } else {
                _isError.value = null
            }
        } else {
            return
        }
    }


    fun prepareEditLog(log: UsageLog) {
        _printObjectUiState.update {
            it.copy(
                id = log.id,
                spoolId = log.spoolId,
                gramsUsed = log.gramsUsed.toString(),
                printTitle = log.title,
                isFailed = log.isFailure
            )
        }
    }
}