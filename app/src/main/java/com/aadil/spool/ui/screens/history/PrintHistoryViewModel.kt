package com.aadil.spool.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aadil.spool.data.entity.UsageLog
import com.aadil.spool.data.repository.SpoolRepository
import com.aadil.spool.ui.screens.entry.SpoolEntryUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * This viewmodel is for the print history screen
 * - Deleter button is working fine with the dialog, - but it has one problem which is
 * seriously effecting the other feature(The Details feature)
 * -Problem with delete:
 *  1. When we delete the print from the print history screen
 *  it gets delete but does not undo the weight which is deleted in the details screen.
 *  it should be like when the user deletes any of the print from the print screen
 *  then right after that the remaining capacity could be refill with the same deleted weight
 *  and bar should go up.
 *
 * The problem with print history screen:
 *  1. Edit feature is not working - When the edit button is clicked the it gets the Alert Dialog
 *  but not showing the results from the database whatever to edit.
 */

@HiltViewModel
class PrintHistoryViewModel @Inject constructor(
    private val spoolRepository: SpoolRepository
) : ViewModel() {

    private val _spoolId = MutableStateFlow(0)


    @OptIn(ExperimentalCoroutinesApi::class)
    val spoolPrintUsageHistoryDetails: StateFlow<List<UsageLog>> =
        _spoolId.flatMapLatest { spoolId ->
            spoolRepository.getSpoolUsageStream(spoolId)
        }
            .filterNotNull()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )


    // Delete history item
    fun deletePrintItem(usageLog: UsageLog) {
        viewModelScope.launch {
            spoolRepository.deleteSpoolUsageLog(usageLog)
        }
    }

    // Update history item
    fun updatePrintItem(usageLog: UsageLog) {
        viewModelScope.launch {
            spoolRepository.updateSpoolUsageLog(usageLog)
        }
    }

    // Helper Function
    fun triggerId(spoolId: Int) {
        _spoolId.value = spoolId
    }
}