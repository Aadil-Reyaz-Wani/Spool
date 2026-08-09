package com.aadil.spool.feature.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aadil.spool.core.data.repository.SpoolRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

open class SpoolEntryViewModel(
    private val spoolRepository: SpoolRepository
) : ViewModel() {
    private val _spoolEntryUiState = MutableStateFlow(SpoolEntryUiState())
    val spoolEntryUiState = _spoolEntryUiState.asStateFlow()

    val isError = MutableStateFlow(false)

    fun update(transform: SpoolEntryUiState.() -> SpoolEntryUiState) {
        _spoolEntryUiState.update(transform)
    }

    fun loadSpool(id: Int) {
        if (id == 0) return
        viewModelScope.launch {
            val currentSpool = spoolRepository.getSpoolStream(id).first()
            currentSpool?.let { spool ->
                _spoolEntryUiState.value = spool.toSpoolEntryUiState()
            }
        }
    }

    fun saveOrUpdateSpool(id: Int) {
        val freshFilament = _spoolEntryUiState.value.toFilament()

        viewModelScope.launch {
            if (id > 0) {
                if (
                    freshFilament.brand.isNotBlank()
                    && freshFilament.material.isNotBlank()
                    && freshFilament.totalWeight > 0 &&
                    freshFilament.totalWeight >= freshFilament.currentWeight
                ) {
                    val restockWeight = _spoolEntryUiState.value.addedWeight.toDoubleOrNull() ?: 0.0
                    val restockPrice = _spoolEntryUiState.value.addedPrice.toDoubleOrNull() ?: 0.0

                    val filamentToUpdate = freshFilament.copy(
                        totalWeight = freshFilament.totalWeight + restockWeight,
                        currentWeight = freshFilament.currentWeight + restockWeight,
                        price = freshFilament.price + restockPrice
                    )

                    spoolRepository.updateSpool(filamentToUpdate)

                    val newPricePerGram = if (filamentToUpdate.totalWeight > 0) filamentToUpdate.price / filamentToUpdate.totalWeight else 0.0
                    spoolRepository.updateAllUsageCosts(filamentToUpdate.id, newPricePerGram)

                    isError.value = false
                    _spoolEntryUiState.update { it.copy(addedWeight = "", addedPrice = "") }
                } else {
                    isError.value = true
                }
                return@launch
            } else {
                try {
                    if (freshFilament.brand.isNotBlank() && freshFilament.material.isNotBlank() && freshFilament.totalWeight > 0 && freshFilament.currentWeight <= freshFilament.totalWeight) {
                        val filamentToInsert = freshFilament.copy(
                            currentWeight = freshFilament.currentWeight.takeIf { it > 0 } ?: freshFilament.totalWeight
                        )
                        spoolRepository.insertSpool(filamentToInsert)
                        _spoolEntryUiState.value = SpoolEntryUiState()
                        isError.value = false
                    } else {
                        isError.value = true
                    }
                } catch (e: Exception) {
                    println("ENTER: ${e.message}")
                }
            }
        }
    }

    fun isValid(): Boolean {
        val weightTotal = _spoolEntryUiState.value.totalWeight.toDoubleOrNull() ?: 0.0
        val weightCurrent = _spoolEntryUiState.value.currentWeight.toDoubleOrNull() ?: 0.0

        return _spoolEntryUiState.value.brand.isNotBlank()
                && _spoolEntryUiState.value.material.isNotBlank()
                && _spoolEntryUiState.value.totalWeight.isNotBlank()
                && weightTotal >= weightCurrent
    }

    fun isEditMode(id: Int): Boolean {
        return (id > 0)
    }

    fun resetState() {
        _spoolEntryUiState.update { SpoolEntryUiState() }
    }
}
