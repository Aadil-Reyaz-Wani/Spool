package com.aadil.spool.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Insert
import com.aadil.spool.data.entity.UsageLog
import com.aadil.spool.data.repository.SpoolRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject


@HiltViewModel
class PrintHistoryViewModel @Inject constructor(
    private val spoolRepository: SpoolRepository
) : ViewModel() {

    private val _spoolId = MutableStateFlow(0)


    @OptIn(ExperimentalCoroutinesApi::class)
    val spoolPrintUsageHistoryDetails : StateFlow<List<UsageLog>> =
        _spoolId.flatMapLatest { spoolId ->
            spoolRepository.getSpoolUsageStream(spoolId)
        }
            .filterNotNull()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )




    // Helper Function
    fun triggerId(spoolId: Int) {
        _spoolId.value = spoolId
    }

}