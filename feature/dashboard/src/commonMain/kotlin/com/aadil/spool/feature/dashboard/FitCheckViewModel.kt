package com.aadil.spool.feature.dashboard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aadil.spool.core.data.repository.SpoolRepository
import com.aadil.spool.data.entity.Filament
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class FitCheckResult(val spool: Filament, val fits: Boolean, val excess: Double)

data class FitCheckUiState(
    val gramsText: String = "",
    val checkedGrams: Double = 0.0,
    val results: List<FitCheckResult> = emptyList(),
)

class FitCheckViewModel(
    spoolRepository: SpoolRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    // Persist only scalar input. Inventory and derived results are reloaded after recreation.
    private val gramsText = savedStateHandle.getStateFlow("gramsText", "")
    private val checkedGrams = savedStateHandle.getStateFlow("checkedGrams", 0.0)

    val uiState = combine(gramsText, checkedGrams, spoolRepository.getAllSpools()) { text, needed, spools ->
        FitCheckUiState(
            gramsText = text,
            checkedGrams = needed,
            results = if (needed > 0.0) {
                spools.map { FitCheckResult(it, it.currentWeight >= needed, it.currentWeight - needed) }
                    .sortedWith(compareByDescending<FitCheckResult> { it.fits }
                        .thenBy { if (it.fits) it.excess else -it.excess })
            } else emptyList(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FitCheckUiState(gramsText.value, checkedGrams.value))

    fun initialize(initialGrams: Double) {
        if (savedStateHandle.get<Boolean>("initialized") == true) return
        savedStateHandle["initialized"] = true
        if (initialGrams.isFinite() && initialGrams > 0.0) {
            savedStateHandle["gramsText"] = initialGrams.toString()
            savedStateHandle["checkedGrams"] = initialGrams
        }
    }

    fun updateGrams(text: String) {
        savedStateHandle["gramsText"] = text
    }

    fun check() {
        val needed = gramsText.value.replace(',', '.').toDoubleOrNull()
        savedStateHandle["checkedGrams"] = needed?.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
    }
}
