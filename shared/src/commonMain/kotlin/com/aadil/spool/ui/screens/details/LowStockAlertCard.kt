package com.aadil.spool.ui.screens.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aadil.spool.core.data.repository.NotificationPreferencesRepository
import com.aadil.spool.core.model.preferences.SpoolAlertConfig
import com.aadil.spool.utils.toParseLocalizedDouble
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import spool.shared.generated.resources.Res
import spool.shared.generated.resources.action_save
import spool.shared.generated.resources.low_stock_alerts
import spool.shared.generated.resources.low_stock_reorder_url
import spool.shared.generated.resources.low_stock_saved
import spool.shared.generated.resources.low_stock_threshold_hint

@Composable
fun LowStockAlertCard(
    spoolId: Int,
    repository: NotificationPreferencesRepository = koinInject(),
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var base by remember { mutableStateOf(SpoolAlertConfig(spoolId)) }
    var enabled by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var threshold by remember { mutableStateOf("") }
    var reorderUrl by remember { mutableStateOf("") }
    var justSaved by remember { mutableStateOf(false) }

    LaunchedEffect(spoolId) {
        val config = repository.loadSpoolConfig(spoolId)
        base = config
        enabled = config.enabled
        threshold = config.thresholdGrams?.toString()?.removeSuffix(".0") ?: ""
        reorderUrl = config.reorderUrl.orEmpty()
        loaded = true
    }

    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(Res.string.low_stock_alerts), style = MaterialTheme.typography.titleMedium)
            Switch(
                checked = enabled,
                onCheckedChange = {
                    if (!loaded) return@Switch
                    enabled = it
                    scope.launch {
                        repository.saveSpoolConfig(currentConfig(base, enabled, threshold, reorderUrl))
                    }
                },
                enabled = loaded,
            )
        }

        if (enabled) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = threshold,
                    onValueChange = { threshold = it },
                    label = { Text(stringResource(Res.string.low_stock_threshold_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = reorderUrl,
                    onValueChange = { reorderUrl = it },
                    label = { Text(stringResource(Res.string.low_stock_reorder_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    androidx.compose.material3.Button(onClick = {
                        scope.launch {
                            repository.saveSpoolConfig(currentConfig(base, enabled, threshold, reorderUrl))
                            justSaved = true
                        }
                    }) {
                        Text(stringResource(Res.string.action_save))
                    }
                    if (justSaved) {
                        Text(stringResource(Res.string.low_stock_saved), style = MaterialTheme.typography.bodySmall)
                        LaunchedEffect(justSaved) {
                            delay(2000)
                            justSaved = false
                        }
                    }
                }
            }
        }
    }
}

/** Preserves cooldown/edge-trigger flags; only the user-editable fields change. */
private fun currentConfig(base: SpoolAlertConfig, enabled: Boolean, threshold: String, reorderUrl: String): SpoolAlertConfig =
    base.copy(
        enabled = enabled,
        thresholdGrams = threshold.toParseLocalizedDouble(),
        reorderUrl = reorderUrl.trim().takeIf { it.isNotEmpty() },
    )
