package com.aadil.spool.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import javax.annotation.meta.When
import javax.inject.Inject

enum class FilterType {
    ALL, BRAND, MATERIAL
}
data class IsFilterApplied(
    val whichFilter: String = "",
    val filterType: FilterType = FilterType.ALL
)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val spoolRepository: SpoolRepository
) : ViewModel() {

    private val _filterApplied = MutableStateFlow(IsFilterApplied())
    val isFilterAppliedState: StateFlow<IsFilterApplied> = _filterApplied.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val getAllSpool: StateFlow<List<Filament>> = _filterApplied
        .flatMapLatest { filter ->
//            if (_filterApplied.value.whichFilter.isBlank() || _filterApplied.value.whichFilter == "Clear all filters") {
//                spoolRepository.getAllSpoolsStream()
//            } else if (_filterApplied.value.whichFilter.isNotBlank()){
//                spoolRepository.getSpoolsByBrandStream(_filterApplied.value.whichFilter)
//            }else {
//                spoolRepository.getSpoolsByMaterialTypeStream(_filterApplied.value.whichFilter)
//            }
            when(filter.filterType) {
                FilterType.ALL -> spoolRepository.getAllSpoolsStream()
                FilterType.BRAND -> spoolRepository.getSpoolsByBrandStream(filter.whichFilter)
                FilterType.MATERIAL -> spoolRepository.getSpoolsByMaterialTypeStream(filter.whichFilter)
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


    fun applyFilter(filterValue: String, type: FilterType) {
        if (filterValue == "Clear all filters" || filterValue.isBlank()) {
            _filterApplied.value = IsFilterApplied(whichFilter = filterValue)
        } else {
            _filterApplied.value = IsFilterApplied(whichFilter = filterValue, filterType = type)
        }

    }
}