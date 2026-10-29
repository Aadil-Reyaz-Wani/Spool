package com.aadil.spool.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aadil.spool.feature.settings.InventoryRecoveryViewModel
import com.aadil.spool.ui.common.SpoolAppBar
import com.aadil.spool.ui.components.SpoolButton
import com.aadil.spool.ui.components.SpoolHeadingText
import com.aadil.spool.ui.components.SpoolHorizontalDivider
import com.aadil.spool.ui.theme.Dimens
import com.aadil.spool.utils.PlatformBackHandler
import com.aadil.spool.utils.toReadableDate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import spool.shared.generated.resources.*

@Composable
fun InventoryRecoveryScreen(navigateUp: () -> Unit) {
    val viewModel: InventoryRecoveryViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    PlatformBackHandler(enabled = state.selected != null, onBack = viewModel::closePreview)
    val preview = state.preview
    val listState = rememberSaveable(state.selected, saver = LazyListState.Saver) { LazyListState() }
    var infoExpanded by rememberSaveable { mutableStateOf(false) }
    Scaffold(topBar = {
        SpoolAppBar(title = stringResource(Res.string.recovery_title), canNavigateBack = true,
            navigateUp = { if (state.selected != null) viewModel.closePreview() else navigateUp() })
    }) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Dimens.PaddingLarge),
            contentPadding = PaddingValues(vertical = Dimens.PaddingMedium),
            verticalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium),
        ) {
            if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (state.error) item {
                Text(stringResource(Res.string.recovery_error), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium)
            }
            if (state.restored) item {
                Text(stringResource(Res.string.recovery_success), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
            if (state.removed) item {
                Text(stringResource(Res.string.recovery_removed), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
            if (state.selected == null) {
                item {
                    Text(stringResource(Res.string.recovery_summary), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.items.isEmpty() && !state.busy) item {
                    SettingsSection {
                        Icon(Icons.Outlined.Restore, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(Dimens.IconLarge))
                        Text(stringResource(Res.string.recovery_empty), style = MaterialTheme.typography.titleMedium)
                    }
                }
                items(state.items, key = { it.id }) { item ->
                    SettingsSection {
                        SettingsActionRow(
                            text = item.brand?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.conflict_unnamed),
                            icon = Icons.Outlined.Restore, enabled = !state.busy, onClick = { viewModel.open(item.id) },
                        )
                        Text(recoveryKind(item.kind), style = MaterialTheme.typography.bodyMedium)
                        RecoverySavedAt(item.savedAt)
                        if (item.restored) Text(stringResource(Res.string.recovery_restored_label),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        SpoolHorizontalDivider()
                        RecoveryRemoveAction(enabled = !state.busy, onClick = { viewModel.requestRemoval(item.id) })
                    }
                }
            } else if (preview == null) {
                if (!state.busy) item {
                    SettingsSection {
                        SettingsActionRow(text = stringResource(Res.string.recovery_reload), icon = Icons.Outlined.Restore,
                            enabled = !state.busy, onClick = { state.selected?.let(viewModel::open) })
                        RecoveryRemoveAction(enabled = !state.busy, onClick = { state.selected?.let(viewModel::requestRemoval) })
                    }
                }
            } else {
                item {
                    SettingsSection {
                        Text(preview.version.spool.brand.ifBlank { stringResource(Res.string.conflict_unnamed) },
                            style = MaterialTheme.typography.titleLarge)
                        Text(recoveryKind(preview.source.kind), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        RecoverySavedAt(preview.source.createdAt)
                        if (preview.source.restoredUuid != null) {
                            Text(stringResource(Res.string.recovery_already_restored),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                item {
                    Text(stringResource(Res.string.recovery_history_notice), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    SpoolButton(text = stringResource(Res.string.recovery_restore),
                        enabled = !state.busy && preview.source.restoredUuid == null, onClick = viewModel::requestRestore)
                }
                item {
                    SettingsSection {
                        SpoolHeadingText(text = stringResource(Res.string.recovery_spool_details), icon = Icons.Outlined.Info)
                        inventoryVersionFields(preview.version.spool, state.currency).forEach { (label, value) ->
                            SpoolHorizontalDivider()
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium)) {
                                Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.End)
                            }
                        }
                    }
                }
                item {
                    Text(stringResource(Res.string.recovery_print_count, preview.version.logs.size),
                        style = MaterialTheme.typography.titleMedium)
                }
                items(preview.version.logs, key = { it.uuid }) { log ->
                    SettingsSection { InventoryPrintVersion(log, state.currency) }
                }
                item {
                    RecoveryRemoveAction(enabled = !state.busy, onClick = { viewModel.requestRemoval(preview.source.id) })
                }
            }
            item {
                SettingsSection {
                    val infoState = stringResource(if (infoExpanded) Res.string.account_info_expanded else Res.string.account_info_collapsed)
                    SettingsActionRow(text = stringResource(Res.string.recovery_help_title), icon = Icons.Outlined.Info,
                        trailingIcon = if (infoExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        modifier = Modifier.semantics { stateDescription = infoState },
                        onClick = { infoExpanded = !infoExpanded })
                    if (infoExpanded) {
                        Text(stringResource(Res.string.recovery_intro), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(Res.string.recovery_retention), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if (state.confirm && preview != null) {
        AlertDialog(onDismissRequest = viewModel::dismissConfirmation, shape = MaterialTheme.shapes.medium,
            title = { Text(stringResource(Res.string.recovery_confirm_title)) },
            text = { Text(stringResource(Res.string.recovery_confirm_body, preview.version.logs.size),
                modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = viewModel::confirmRestore) { Text(stringResource(Res.string.recovery_restore)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissConfirmation) { Text(stringResource(Res.string.btn_cancel)) } })
    }
    state.removal?.let { item ->
        AlertDialog(onDismissRequest = viewModel::dismissRemoval, shape = MaterialTheme.shapes.medium,
            title = { Text(stringResource(Res.string.recovery_remove_title)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium)) {
                    Text(item.brand?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.conflict_unnamed))
                    Text(recoveryKind(item.kind))
                    Text(stringResource(Res.string.recovery_saved_at, item.savedAt.toReadableDate()))
                    Text(stringResource(Res.string.recovery_remove_body))
                }
            },
            confirmButton = { TextButton(onClick = viewModel::confirmRemoval) { Text(stringResource(Res.string.recovery_remove)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissRemoval) { Text(stringResource(Res.string.btn_cancel)) } })
    }
}

@Composable
private fun RecoverySavedAt(timestamp: Long) {
    Text(stringResource(Res.string.recovery_saved_at, timestamp.toReadableDate()),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun RecoveryRemoveAction(enabled: Boolean, onClick: () -> Unit) {
    TextButton(enabled = enabled, onClick = onClick) {
        Text(stringResource(Res.string.recovery_remove), color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun recoveryKind(kind: String): String = stringResource(when (kind) {
    "conflict_local" -> Res.string.recovery_conflict_local
    "conflict_cloud" -> Res.string.recovery_conflict_cloud
    "deleted_cloud" -> Res.string.recovery_deleted_cloud
    else -> Res.string.recovery_deleted_device
})
