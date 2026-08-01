package com.aadil.spool.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aadil.spool.core.data.repository.SpoolRepository
import com.aadil.spool.data.entity.Filament
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

enum class FilterType {
    ALL, BRAND, MATERIAL, COLOR
}

data class IsFilterApplied(
    val whichFilter: String = "",
    val filterType: FilterType = FilterType.ALL
)

open class DashboardViewModel(
    private val spoolRepository: SpoolRepository
) : ViewModel() {

    private val _filterApplied = MutableStateFlow(IsFilterApplied())
    val filterAppliedState: StateFlow<IsFilterApplied> = _filterApplied.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val getAllSpool: StateFlow<List<Filament>> = _filterApplied
        .flatMapLatest { filter ->
            when(filter.filterType) {
                FilterType.ALL -> spoolRepository.getAllSpoolsStream()
                FilterType.BRAND -> spoolRepository.getSpoolsByBrandStream(filter.whichFilter)
                FilterType.MATERIAL -> spoolRepository.getSpoolsByMaterialTypeStream(filter.whichFilter)
                FilterType.COLOR -> spoolRepository.getSpoolsByColorHexStream(filter.whichFilter.toLong())
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

    val getUniqueMaterialType: StateFlow<List<String>> =
        spoolRepository.getUniqueMaterialTypeStream()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val getUniqueColorHex: StateFlow<List<Long>> =
        spoolRepository.getUniqueColorHexStream()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    fun applyFilter(filterValue: String, type: FilterType) {
        if (filterValue == "Clear all filters" || filterValue.isBlank()) {
            _filterApplied.value = IsFilterApplied(whichFilter = filterValue)
        } else {
            _filterApplied.value = IsFilterApplied(whichFilter = filterValue, filterType = type)
        }
    }
}
