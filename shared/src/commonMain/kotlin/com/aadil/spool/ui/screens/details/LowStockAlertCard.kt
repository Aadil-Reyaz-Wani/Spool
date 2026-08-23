package com.aadil.spool.ui.screens.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.aadil.spool.core.data.repository.NotificationPreferencesRepository
import com.aadil.spool.core.model.preferences.SpoolAlertConfig
import com.aadil.spool.ui.components.SpoolButton
import com.aadil.spool.ui.components.SpoolHeadingText
import com.aadil.spool.ui.components.SpoolHorizontalDivider
import com.aadil.spool.ui.components.SpoolOutlinedTextField
import com.aadil.spool.ui.theme.Dimens
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
import spool.shared.generated.resources.low_stock_threshold_placeholder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LowStockAlertSheet(
    spoolId: Int,
    showSheet: Boolean,
    onDismiss: () -> Unit,
    repository: NotificationPreferencesRepository = koinInject(),
) {
    if (!showSheet) return

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

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.PaddingMedium)
                .padding(bottom = Dimens.PaddingMedium),
            verticalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SpoolHeadingText(
                    text = stringResource(Res.string.low_stock_alerts),
                    icon = Icons.Outlined.Notifications,
                )
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
            SpoolHorizontalDivider(
                modifier = Modifier.padding(vertical = Dimens.PaddingSmall),
            )
            AnimatedVisibility(
                visible = enabled,
                enter = expandVertically(animationSpec = tween(150)) + fadeIn(animationSpec = tween(100)),
                exit = shrinkVertically(animationSpec = tween(150)) + fadeOut(animationSpec = tween(100)),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall),
                ) {
                    SpoolOutlinedTextField(
                        value = threshold,
                        onValueChange = { threshold = it },
                        label = stringResource(Res.string.low_stock_threshold_hint),
                        placeholder = stringResource(Res.string.low_stock_threshold_placeholder),
                        leadingIcon = Icons.Outlined.Speed,
                        singleLine = true,
                    )
                    SpoolOutlinedTextField(
                        value = reorderUrl,
                        onValueChange = { reorderUrl = it },
                        label = stringResource(Res.string.low_stock_reorder_url),
                        leadingIcon = Icons.Outlined.Link,
                        singleLine = true,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall),
                    ) {
                        SpoolButton(
                            text = stringResource(Res.string.action_save),
                            icon = Icons.Outlined.Save,
                            contentDescription = stringResource(Res.string.action_save),
                            onClick = {
                                scope.launch {
                                    repository.saveSpoolConfig(currentConfig(base, enabled, threshold, reorderUrl))
                                    justSaved = true
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        if (justSaved) {
                            Text(
                                stringResource(Res.string.low_stock_saved),
                                style = MaterialTheme.typography.bodySmall,
                            )
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
}

/** Preserves cooldown/edge-trigger flags; only the user-editable fields change. */
private fun currentConfig(base: SpoolAlertConfig, enabled: Boolean, threshold: String, reorderUrl: String): SpoolAlertConfig =
    base.copy(
        enabled = enabled,
        thresholdGrams = threshold.toParseLocalizedDouble(),
        reorderUrl = reorderUrl.trim().takeIf { it.isNotEmpty() },
    )
