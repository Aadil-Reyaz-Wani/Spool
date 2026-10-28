package com.aadil.spool.feature.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aadil.spool.core.data.repository.InventoryScope
import com.aadil.spool.core.data.sync.*
import com.aadil.spool.core.model.defaultCurrencyCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

enum class InventoryConflictError { Changed, Unavailable }
data class InventoryConflictItem(val uuid: String, val brand: String?)
data class InventoryConflictsUiState(
    val conflicts: List<InventoryConflictItem> = emptyList(),
    val selected: String? = null,
    val review: InventoryConflictReview? = null,
    val confirmation: InventoryConflictChoice? = null,
    val busy: Boolean = false,
    val error: InventoryConflictError? = null,
    val currency: String = defaultCurrencyCode(),
)

class InventoryConflictsViewModel(
    private val repository: InventoryConflictRepository,
    private val scope: InventoryScope,
    private val savedStateHandle: SavedStateHandle,
    currencyCodes: Flow<String>,
    private val retrySync: () -> Unit,
) : ViewModel() {
    private val mutableState = MutableStateFlow(InventoryConflictsUiState(selected = savedStateHandle["conflict_spool"]))
    val state = mutableState.asStateFlow()
    private var operation: Job? = null
    private var generation = 0

    init {
        viewModelScope.launch {
            repository.observeConflicts(scope).collect { rows ->
                val items = rows.map { row ->
                    val brand = runCatching { Json.parseToJsonElement(row.request).jsonObject["p_spool"]?.jsonObject?.get("brand")?.jsonPrimitive?.content }.getOrNull()
                    InventoryConflictItem(row.spoolUuid, brand)
                }
                mutableState.update { it.copy(conflicts = items) }
                if (!state.value.busy && state.value.selected != null && rows.none { it.spoolUuid == state.value.selected }) closeReview()
            }
        }
        viewModelScope.launch { currencyCodes.collect { code -> mutableState.update { it.copy(currency = code) } } }
        state.value.selected?.let(::open)
    }

    fun open(uuid: String) {
        if (state.value.busy) return
        val request = ++generation
        operation?.cancel()
        savedStateHandle["conflict_spool"] = uuid
        mutableState.update { it.copy(selected = uuid, review = null, confirmation = null, error = null, busy = true) }
        operation = viewModelScope.launch {
            try {
                val result = repository.review(scope, uuid)
                if (request != generation) return@launch
                mutableState.update { current -> when (result) {
                    is InventoryConflictReviewResult.Ready -> current.copy(review = result.review)
                    InventoryConflictReviewResult.Changed -> current.copy(error = InventoryConflictError.Changed)
                    InventoryConflictReviewResult.Unavailable -> current.copy(error = InventoryConflictError.Unavailable)
                    InventoryConflictReviewResult.StaleScope -> current.copy(selected = null, review = null, confirmation = null)
                } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (request == generation) mutableState.update { it.copy(error = InventoryConflictError.Unavailable) }
            } finally {
                if (request == generation) mutableState.update { it.copy(busy = false) }
            }
        }
    }

    fun requestChoice(choice: InventoryConflictChoice) {
        if (state.value.busy || state.value.review == null) return
        mutableState.update { it.copy(confirmation = choice, error = null) }
    }
    fun dismissConfirmation() { mutableState.update { it.copy(confirmation = null) } }
    fun refresh() { state.value.selected?.let(::open) }

    fun confirm() {
        val review = state.value.review ?: return
        val choice = state.value.confirmation ?: return
        if (state.value.busy) return
        val request = ++generation
        mutableState.update { it.copy(confirmation = null, busy = true, error = null) }
        operation = viewModelScope.launch {
            try {
                val result = repository.resolve(review, choice)
                if (request != generation) return@launch
                when (result) {
                    InventoryConflictResolveResult.Resolved -> {
                        savedStateHandle["conflict_spool"] = null
                        mutableState.update { it.copy(selected = null, review = null) }
                        retrySync()
                    }
                    InventoryConflictResolveResult.Changed -> mutableState.update { it.copy(review = null, error = InventoryConflictError.Changed) }
                    InventoryConflictResolveResult.Unavailable -> mutableState.update { it.copy(error = InventoryConflictError.Unavailable) }
                    InventoryConflictResolveResult.StaleScope -> closeReview()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (request == generation) mutableState.update { it.copy(error = InventoryConflictError.Unavailable) }
            } finally {
                if (request == generation) mutableState.update { it.copy(busy = false) }
            }
        }
    }

    fun closeReview() {
        generation++
        operation?.cancel()
        savedStateHandle["conflict_spool"] = null
        mutableState.update { it.copy(selected = null, review = null, confirmation = null, error = null, busy = false) }
    }
}
