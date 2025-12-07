package com.kashmir.spool.ui.screens.entry

import androidx.core.graphics.isSrgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kashmir.spool.data.entity.Filament
import com.kashmir.spool.data.repository.SpoolRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SpoolEntryViewModel(
    private val spoolRepository: SpoolRepository
) : ViewModel() {
    private val _spoolEntryUiState = MutableStateFlow(SpoolEntryUiState())
    val spoolEntryUiState = _spoolEntryUiState.asStateFlow()

    fun updateTextField(
        newBrand: String,
        newMaterial: String,
        newTotalWeight: Int,
        newColorHex: Long
    ) {

        _spoolEntryUiState.value = _spoolEntryUiState.value.copy(
            brand = newBrand,
            material = newMaterial,
            totalWeight = newTotalWeight,
            colorHex = newColorHex
        )
    }

    // Save Spool to DB
    fun saveSpool() {

        val freshFilament = _spoolEntryUiState.value.toFilament()

        viewModelScope.launch {
            spoolRepository.insertSpool(freshFilament)
            _spoolEntryUiState.value = SpoolEntryUiState()
        }
    }

    fun isValid(): Boolean {
        return _spoolEntryUiState.value.brand.isNotBlank()
                && _spoolEntryUiState.value.material.isNotBlank()
                && _spoolEntryUiState.value.totalWeight > 0
                && _spoolEntryUiState.value.colorHex.isSrgb
    }
}

data class SpoolEntryUiState(
    val id: Int = 0,
    val brand: String = "",
    val material: String = "",
    val totalWeight: Int = 0,
    val colorHex: Long = 0xFF000000
)

fun SpoolEntryUiState.toFilament(): Filament {
    return Filament(
        id = id,
        brand = brand,
        material = material,
        totalWeight = totalWeight,
        colorHex = colorHex
    )
}