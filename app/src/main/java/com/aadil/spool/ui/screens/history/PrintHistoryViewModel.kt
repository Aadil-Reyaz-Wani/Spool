package com.aadil.spool.ui.screens.history

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aadil.spool.data.entity.UsageLog
import com.aadil.spool.data.repository.SpoolRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
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
 *  then right after that the remaining capacity could be refilled with the same deleted weight
 *  and bar should go up.
 *
 * The problem with print history screen:
 *  1. Edit feature is not working - When the edit button is clicked it gets the Alert Dialog
 *  but not showing the results from the database whatever to edit.
 */

@HiltViewModel
class PrintHistoryViewModel @Inject constructor(
    private val spoolRepository: SpoolRepository
) : ViewModel() {

    private val _spoolId = MutableStateFlow<Int?>(null)


    @OptIn(ExperimentalCoroutinesApi::class)
    val spoolPrintUsageHistoryDetails: StateFlow<List<UsageLog>> =
        _spoolId
            .filterNotNull() // Don't even talk to the DB until we have a real ID
            .flatMapLatest { spoolId ->
                spoolRepository.getSpoolUsageStream(spoolId)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )


    // Delete print-history item
    fun deletePrintItem(usageLog: UsageLog) {
        viewModelScope.launch {
            try {
                // Doing this with @Transaction in DAO because it has no. of db operations to execute
                spoolRepository.deleteLogAndRestoreCurrentWeight(usageLog)
            } catch (e: Exception) {
                // Log error or update the UI error state - for now I am logging the error
                Log.e("PrintHistoryViewModel", "Error deleting print item: $e")
            }
        }
    }

    // Not working right now - Edit Print-History
    // Update print-history item
    fun updatePrintItem(usageLog: UsageLog) {
        viewModelScope.launch {
            try {
                spoolRepository.updateSpoolUsageLog(usageLog)
            } catch (e: Exception) {
                Log.e("PrintHistoryViewModel", "Error updating print item: $e")
            }
        }
    }

    // Helper Function to get the id from the UI
    fun triggerId(spoolId: Int) {
        _spoolId.value = spoolId
    }
}