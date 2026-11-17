package com.aadil.spool.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aadil.spool.core.data.analytics.UsageAnalytics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AnalyticsViewModel(private val analytics: UsageAnalytics) : ViewModel() {
    val consent = analytics.state
    private val mutableSaving = MutableStateFlow(false)
    val saving = mutableSaving.asStateFlow()
    private val mutableError = MutableStateFlow(false)
    val error = mutableError.asStateFlow()
    private val mutablePromptDismissed = MutableStateFlow(false)
    val promptDismissed = mutablePromptDismissed.asStateFlow()
    private var pendingChoice: Boolean? = null

    init { analytics.start() }

    fun choose(enabled: Boolean) {
        if (saving.value) return
        pendingChoice = enabled
        if (!enabled) mutablePromptDismissed.value = true
        mutableSaving.value = true
        mutableError.value = false
        viewModelScope.launch {
            try {
                analytics.setConsent(enabled)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableError.value = true
            } finally {
                mutableSaving.value = false
            }
        }
    }

    fun retryChoice() { pendingChoice?.let(::choose) }
}
