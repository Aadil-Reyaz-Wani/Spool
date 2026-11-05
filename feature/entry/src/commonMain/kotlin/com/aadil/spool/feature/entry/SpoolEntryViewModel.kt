package com.aadil.spool.feature.entry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aadil.spool.core.data.repository.SpoolRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

open class SpoolEntryViewModel(
    private val spoolRepository: SpoolRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val json = Json { ignoreUnknownKeys = true }
    private val restoredDraft = savedStateHandle.get<String>("draft")?.let {
        runCatching { json.decodeFromString<SpoolEntryUiState>(it) }.getOrNull()
    }
    private val _spoolEntryUiState = MutableStateFlow(restoredDraft ?: SpoolEntryUiState())
    val spoolEntryUiState = _spoolEntryUiState.asStateFlow()
    private val _error = MutableStateFlow<SpoolEntryError?>(null)
    val error = _error.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _isSaving = MutableStateFlow(false)
    val isSaving = _isSaving.asStateFlow()
    val saveCompleted = savedStateHandle.getStateFlow("saveCompleted", false)
    private var loadedFor: Int? = null

    private fun setDraft(state: SpoolEntryUiState) {
        savedStateHandle["draft"] = json.encodeToString(state)
        _spoolEntryUiState.value = state
    }

    fun update(transform: SpoolEntryUiState.() -> SpoolEntryUiState) {
        if (_isLoading.value || _isSaving.value || saveCompleted.value) return
        setDraft(_spoolEntryUiState.value.transform())
        _error.value = null
    }

    fun loadSpool(id: Int) {
        if (loadedFor == id || _isLoading.value || saveCompleted.value) return
        if (id == 0) {
            loadedFor = id
            return
        }
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val spool = spoolRepository.getSpoolStream(id).first()
                if (spool == null) {
                    _error.value = SpoolEntryError.LOAD_FAILED
                } else {
                    // Reload the record after process death, but retain the user's saved draft.
                    if (restoredDraft?.id != id) {
                        val baseline = spool.toSpoolEntryUiState()
                        setDraft(baseline)
                        savedStateHandle["editBaseline"] = json.encodeToString(baseline)
                    }
                    loadedFor = id
                    _error.value = null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = SpoolEntryError.LOAD_FAILED
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun saveOrUpdateSpool(id: Int) {
        if (_isLoading.value || _isSaving.value || saveCompleted.value) return
        val state = _spoolEntryUiState.value
        val validationError = state.validationError(isEdit = id > 0)
        if (validationError != null) {
            _error.value = validationError
            return
        }
        if (id > 0 && (loadedFor != id || state.id != id)) {
            _error.value = SpoolEntryError.LOAD_FAILED
            return
        }
        _isSaving.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                if (id > 0) {
                    val existing = spoolRepository.getSpoolStream(id).first()
                    if (existing == null) {
                        _error.value = SpoolEntryError.LOAD_FAILED
                        return@launch
                    }
                    val baseline = savedStateHandle.get<String>("editBaseline")
                    if (baseline != json.encodeToString(existing.toSpoolEntryUiState())) {
                        _error.value = SpoolEntryError.CONCURRENT_UPDATE
                        return@launch
                    }
                    // Copy editable values onto the stored entity to retain drying metadata and timestamp.
                    val edited = state.toFilament(existing)
                    val extraWeight = state.addedWeight.entryNumber() ?: 0.0
                    val extraPrice = state.addedPrice.entryNumber() ?: 0.0
                    val updated = edited.copy(
                        totalWeight = edited.totalWeight + extraWeight,
                        currentWeight = edited.currentWeight + extraWeight,
                        price = edited.price + extraPrice,
                    )
                    // Added filament changes the stock being measured. Its dryness and tare
                    // cannot be inferred from measurements taken before the restock.
                    val toSave = if (extraWeight > 0.0) updated.copy(
                        dryBaselineWeight = null,
                        dryBaselineTareGrams = null,
                        lastWeighedTareGrams = null,
                        lastDriedAt = null,
                    ) else updated
                    if (!spoolRepository.updateSpoolFromDraft(toSave, existing)) {
                        _error.value = SpoolEntryError.CONCURRENT_UPDATE
                        return@launch
                    }
                } else {
                    spoolRepository.insertSpool(state.toFilament())
                }
                savedStateHandle["saveCompleted"] = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = SpoolEntryError.SAVE_FAILED
            } finally {
                _isSaving.value = false
            }
        }
    }
}
