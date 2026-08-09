package com.aadil.spool.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aadil.spool.core.data.repository.SpoolRepository
import com.aadil.spool.data.entity.UsageLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

open class PrintHistoryViewModel(
    private val spoolRepository: SpoolRepository
) : ViewModel() {

    private val _spoolId = MutableStateFlow<Int?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val spoolPrintUsageHistoryDetails: StateFlow<List<UsageLog>> =
        _spoolId
            .filterNotNull()
            .flatMapLatest { spoolId ->
                spoolRepository.getSpoolUsageStream(spoolId)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    fun deletePrintItem(usageLog: UsageLog) {
        viewModelScope.launch {
            try {
                spoolRepository.deleteLogAndRestoreCurrentWeight(usageLog)
            } catch (e: Exception) {
                println("PrintHistoryViewModel: Error deleting print item: $e")
            }
        }
    }

    fun triggerId(spoolId: Int) {
        _spoolId.value = spoolId
    }
}
