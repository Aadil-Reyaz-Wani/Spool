package com.aadil.spool.feature.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aadil.spool.core.data.repository.SpoolRepository
import com.aadil.spool.data.entity.Filament
import com.aadil.spool.data.entity.UsageLog
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

data class PrintObjectUiState(
    val id: Int = 0,
    val spoolId: Int = 0,
    val gramsUsed: String = "",
    val printTitle: String = "",
    val isFailed: Boolean = false
)

open class SpoolDetailsViewModel(
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

    fun deductCurrentWeight(id: Int, inputWeight: String) {
        val currentState = _printObjectUiState.value
        val parsedDeductedWeight = inputWeight.toDoubleOrNull() ?: 0.0

        if (parsedDeductedWeight <= 0 || currentState.printTitle.isBlank()) return

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
                    if (newCurrentWeight < 0) {
                        _isError.value = "Cannot log more than the remaining filament weight"
                        return@launch
                    }
                    spoolRepository.updateCurrentWeight(id, newCurrentWeight)
                    spoolRepository.insertSpoolUsageLog(newUsageLog)
                } else {
                    val applied = spoolRepository.editLogAndRestoreCurrentWeight(newUsageLog)
                    if (!applied) {
                        _isError.value = "Cannot log more than the remaining filament weight"
                        return@launch
                    }
                }

                _printObjectUiState.update { PrintObjectUiState() }
                _isError.value = null
            } catch (ae: Exception) {
                println("ERROR: ${ae.message}")
            }
        }
    }

    fun resetPrintObjectUiState() {
        _printObjectUiState.update { PrintObjectUiState() }
        _isError.value = null
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
