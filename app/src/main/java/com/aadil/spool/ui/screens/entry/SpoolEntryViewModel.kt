package com.aadil.spool.ui.screens.entry

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aadil.spool.data.entity.Filament
import com.aadil.spool.data.repository.SpoolRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SpoolEntryViewModel(
    private val spoolRepository: SpoolRepository
) : ViewModel() {
    private val _spoolEntryUiState = MutableStateFlow(SpoolEntryUiState())
    val spoolEntryUiState = _spoolEntryUiState.asStateFlow()

    val isError = MutableStateFlow(false)

    // Text field value changes
    fun updateTextField(
        newBrand: String,
        newMaterial: String,
        newTotalWeight: String,
        newColorName: String,
        newColorHex: Long,
        newCurrentWeight: String,
        newTempNozzle: String,
        newTempBed: String,
        newNote: String
    ) {

        _spoolEntryUiState.value = _spoolEntryUiState.value.copy(
            brand = newBrand,
            material = newMaterial,
            totalWeight = newTotalWeight,
            currentWeight = newCurrentWeight,
            colorName = newColorName,
            colorHex = newColorHex,
            tempNozzle = newTempNozzle,
            tempBed = newTempBed,
            note = newNote
        )
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


    // Save Spool to DB
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
                    spoolRepository.updateSpool(freshFilament)
                    isError.value = false
                } else {
                    isError.value = true
                }
                return@launch
            } else {
                try {
                    if (freshFilament.brand.isNotBlank() && freshFilament.material.isNotBlank() && freshFilament.totalWeight > 0) {

                        val filamentToInsert = freshFilament.copy(
                            currentWeight = freshFilament.totalWeight
                        )

                        spoolRepository.insertSpool(filamentToInsert)
                        _spoolEntryUiState.value = SpoolEntryUiState()
                        isError.value = false
                    } else {
                        isError.value = true
                    }
                } catch (e: ArithmeticException) {
                    Log.d("ENTER", "${e.message}")
                }
            }

        }
    }

    // Conditions for navigation
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
        _spoolEntryUiState.value = SpoolEntryUiState()
    }

}


// Ui State and Extension Functions
data class SpoolEntryUiState(
    val id: Int = 0,
    val brand: String = "",
    val material: String = "",
    val totalWeight: String = "",
    val colorName: String = "",
    val colorHex: Long = 0xFF000000,
    val currentWeight: String = "",
    val tempNozzle: String = "",
    val tempBed: String = "",
    val note: String = ""
)

fun SpoolEntryUiState.toFilament(): Filament {
    return Filament(
        id = id,
        brand = brand,
        material = material,
        totalWeight = totalWeight.toDoubleOrNull() ?: 0.0,
        currentWeight = currentWeight.toDoubleOrNull() ?: 0.0,
        colorHex = colorHex,
        colorName = colorName,
        tempNozzle = tempNozzle.toIntOrNull() ?: 0,
        tempBed = tempBed.toIntOrNull() ?: 0,
        note = note
    )
}

fun Filament.toSpoolEntryUiState(): SpoolEntryUiState {
    return SpoolEntryUiState(
        id = id,
        brand = brand,
        material = material,
        totalWeight = totalWeight.toString(),
        currentWeight = currentWeight.toString(),
        colorHex = colorHex,
        colorName = colorName,
        tempNozzle = tempNozzle.toString(),
        tempBed = tempBed.toString(),
        note = note
    )
}