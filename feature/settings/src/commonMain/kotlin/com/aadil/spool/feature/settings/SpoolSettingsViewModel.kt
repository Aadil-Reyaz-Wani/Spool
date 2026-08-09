package com.aadil.spool.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aadil.spool.core.data.repository.CurrencyPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

open class SpoolSettingsViewModel(
    private val currencyPreferencesRepository: CurrencyPreferencesRepository
): ViewModel() {
    val selectedCurrency: StateFlow<String> = currencyPreferencesRepository.currencyCodeFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = "USD"
        )

    fun saveCurrency(newCode: String) {
        viewModelScope.launch {
            currencyPreferencesRepository.updateCurrencyCode(newCode = newCode)
        }
    }
}
