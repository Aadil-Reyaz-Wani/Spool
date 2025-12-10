package com.kashmir.spool.ui.screens.entry

import android.util.Log
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

    val isError = MutableStateFlow(false)

    fun updateTextField(
        newBrand: String,
        newMaterial: String,
        newTotalWeight: String,
        newColorName: String,
        newColorHex: Long
    ) {

        _spoolEntryUiState.value = _spoolEntryUiState.value.copy(
            brand = newBrand,
            material = newMaterial,
            totalWeight = newTotalWeight,
            colorName = newColorName,
            colorHex = newColorHex
        )
    }

    // Save Spool to DB
    fun saveSpool() {

        val freshFilament = _spoolEntryUiState.value.toFilament()

        viewModelScope.launch {
            try {
                if (freshFilament.totalWeight != null && freshFilament.totalWeight > 0 ) {
                    spoolRepository.insertSpool(freshFilament)
                    _spoolEntryUiState.value = SpoolEntryUiState()
                    isError.value = false
                }else {
                    isError.value = true
                }
            }catch (e: ArithmeticException) {
                Log.d("ENTER","${e.message}")
            }
        }
    }

    fun isValid(): Boolean {
        return _spoolEntryUiState.value.brand.isNotBlank()
                && _spoolEntryUiState.value.material.isNotBlank()
                && _spoolEntryUiState.value.totalWeight.isNotBlank()
    }
}

data class SpoolEntryUiState(
    val id: Int = 0,
    val brand: String = "",
    val material: String = "",
    val totalWeight: String = "",
    val colorName: String = "",
    val colorHex: Long = 0xFF000000
)

fun SpoolEntryUiState.toFilament(): Filament {
    return Filament(
        id = id,
        brand = brand,
        material = material,
        totalWeight = totalWeight.toDoubleOrNull(),
        currentWeight = totalWeight.toDoubleOrNull(), // Keep eye on this one, this could cause problem in future
        colorHex = colorHex,
        colorName = colorName,
        tempNozzle = 0,
        tempBed = 0,
        note = ""
    )
}