package com.aadil.spool.ui.screens.dashboard

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aadil.spool.R
import com.aadil.spool.data.entity.Filament
import com.aadil.spool.data.repository.SpoolRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val spoolRepository: SpoolRepository
) : ViewModel() {

    private val _filterApplied = MutableStateFlow(IsFilterApplied())
    val isFilterAppliedState: StateFlow<IsFilterApplied> = _filterApplied.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val getAllSpool: StateFlow<List<Filament>> = _filterApplied
        .flatMapLatest {
            if (_filterApplied.value.whichFilter.isBlank() || _filterApplied.value.whichFilter == "Clear all filters") {
                spoolRepository.getAllSpoolsStream()
            } else {
                spoolRepository.getSpoolsByBrandStream(_filterApplied.value.whichFilter)
            }
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val getUniqueBrand: StateFlow<List<String>> =
        spoolRepository.getUniqueBrandStream()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )


    fun isFilterApplied(whichFilter: String) {
        _filterApplied.value = IsFilterApplied(whichFilter = whichFilter)
    }
}

data class IsFilterApplied(
    val whichFilter: String = "",
)