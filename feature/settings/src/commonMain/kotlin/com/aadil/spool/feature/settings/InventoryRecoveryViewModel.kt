package com.aadil.spool.feature.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aadil.spool.core.data.repository.InventoryScope
import com.aadil.spool.core.data.sync.*
import com.aadil.spool.core.model.defaultCurrencyCode
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

data class InventoryRecoveryUiState(
    val items: List<InventoryRecoveryItem> = emptyList(),
    val selected: String? = null,
    val preview: InventoryRecoveryPreview? = null,
    val confirm: Boolean = false,
    val busy: Boolean = false,
    val error: Boolean = false,
    val restored: Boolean = false,
    val removed: Boolean = false,
    val removal: InventoryRecoveryItem? = null,
    val currency: String = defaultCurrencyCode(),
)

class InventoryRecoveryViewModel(
    private val repository: InventoryRecoveryRepository,
    private val scope: InventoryScope,
    private val saved: SavedStateHandle,
    currencyCodes: Flow<String>,
    private val retrySync: () -> Unit,
) : ViewModel() {
    private val mutableState = MutableStateFlow(InventoryRecoveryUiState(selected = saved["recovery_source"]))
    val state = mutableState.asStateFlow()
    private var operation: Job? = null
    private var generation = 0

    init {
        viewModelScope.launch {
            repository.observe(scope).catch { mutableState.update { it.copy(error = true) } }.collect { items ->
                mutableState.update { it.copy(items = items, removal = it.removal?.takeIf { selected -> items.any { item -> item.id == selected.id } }) }
                if (!state.value.busy && state.value.selected != null && items.none { it.id == state.value.selected }) closePreview()
            }
        }
        viewModelScope.launch { currencyCodes.collect { code -> mutableState.update { it.copy(currency = code) } } }
        state.value.selected?.let(::open)
    }

    fun open(id: String) {
        if (state.value.busy) return
        val request = ++generation
        operation?.cancel()
        saved["recovery_source"] = id
        mutableState.update { it.copy(selected = id, preview = null, confirm = false, busy = true, error = false, restored = false, removed = false, removal = null) }
        operation = viewModelScope.launch {
            try {
                val preview = repository.preview(scope, id)
                if (request == generation) mutableState.update { it.copy(preview = preview, error = preview == null) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (request == generation) mutableState.update { it.copy(error = true) } }
            finally { if (request == generation) mutableState.update { it.copy(busy = false) } }
        }
    }

    fun requestRestore() {
        if (!state.value.busy && state.value.preview?.source?.restoredUuid == null && state.value.preview != null) {
            mutableState.update { it.copy(confirm = true, removal = null) }
        }
    }
    fun dismissConfirmation() { mutableState.update { it.copy(confirm = false) } }

    fun confirmRestore() {
        val preview = state.value.preview ?: return
        if (!state.value.confirm || state.value.busy) return
        val request = ++generation
        mutableState.update { it.copy(confirm = false, busy = true, error = false) }
        operation = viewModelScope.launch {
            try {
                val result = repository.restore(scope, preview)
                if (request != generation) return@launch
                if (result == InventoryRecoveryResult.Restored) {
                    saved["recovery_source"] = null
                    mutableState.update { it.copy(selected = null, preview = null, restored = true) }
                    runCatching(retrySync)
                } else if (result == InventoryRecoveryResult.StaleScope) closePreview()
                else mutableState.update { it.copy(preview = null, error = true) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (request == generation) mutableState.update { it.copy(error = true) } }
            finally { if (request == generation) mutableState.update { it.copy(busy = false) } }
        }
    }

    fun requestRemoval(id: String) {
        if (state.value.busy) return
        val item = state.value.items.firstOrNull { it.id == id } ?: return
        mutableState.update { it.copy(removal = item, confirm = false, error = false) }
    }

    fun dismissRemoval() { mutableState.update { it.copy(removal = null) } }

    fun confirmRemoval() {
        val item = state.value.removal ?: return
        if (state.value.busy) return
        val request = ++generation
        mutableState.update { it.copy(removal = null, busy = true, error = false, restored = false, removed = false) }
        operation = viewModelScope.launch {
            try {
                val result = repository.remove(scope, item.id)
                if (request != generation) return@launch
                when (result) {
                    InventoryRecoveryRemovalResult.Removed -> {
                        saved["recovery_source"] = null
                        mutableState.update { it.copy(selected = null, preview = null, removed = true) }
                    }
                    InventoryRecoveryRemovalResult.StaleScope -> closePreview()
                    InventoryRecoveryRemovalResult.Unavailable -> mutableState.update { it.copy(error = true) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (request == generation) mutableState.update { it.copy(error = true) } }
            finally { if (request == generation) mutableState.update { it.copy(busy = false) } }
        }
    }

    fun closePreview() {
        ++generation
        operation?.cancel()
        saved["recovery_source"] = null
        mutableState.update { it.copy(selected = null, preview = null, confirm = false, busy = false, error = false, removal = null) }
    }
}
