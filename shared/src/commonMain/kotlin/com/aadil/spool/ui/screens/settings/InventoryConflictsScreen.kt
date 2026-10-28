package com.aadil.spool.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aadil.spool.core.data.sync.InventoryConflictChoice
import com.aadil.spool.core.model.RemoteInventoryLog
import com.aadil.spool.core.model.RemoteInventorySpool
import com.aadil.spool.feature.settings.InventoryConflictError
import com.aadil.spool.feature.settings.InventoryConflictsViewModel
import com.aadil.spool.ui.common.SpoolAppBar
import com.aadil.spool.ui.components.SpoolButton
import com.aadil.spool.ui.components.SpoolHeadingText
import com.aadil.spool.ui.theme.Dimens
import com.aadil.spool.utils.formatAsCurrency
import com.aadil.spool.utils.toReadableDate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import spool.shared.generated.resources.*

@Composable
fun InventoryConflictsScreen(navigateUp: () -> Unit) {
    val viewModel: InventoryConflictsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val review = state.review
    val listState = rememberSaveable(state.selected, saver = LazyListState.Saver) { LazyListState() }
    Scaffold(topBar = {
        SpoolAppBar(title = stringResource(Res.string.conflict_title), canNavigateBack = true,
            navigateUp = { if (state.selected != null) viewModel.closeReview() else navigateUp() })
    }) { padding ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Dimens.PaddingLarge),
            contentPadding = PaddingValues(vertical = Dimens.PaddingLarge),
            verticalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium)) {
            item { Text(stringResource(Res.string.conflict_intro), style = MaterialTheme.typography.bodyLarge) }
            if (state.busy) item { CircularProgressIndicator() }
            state.error?.let { error -> item {
                Text(stringResource(if (error == InventoryConflictError.Changed) Res.string.conflict_changed else Res.string.conflict_unavailable),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            } }
            if (state.selected == null) {
                if (state.conflicts.isEmpty()) item { Text(stringResource(Res.string.conflict_none), style = MaterialTheme.typography.bodyMedium) }
                items(state.conflicts, key = { it.uuid }) { item ->
                    SpoolButton(text = item.brand?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.conflict_unnamed),
                        enabled = !state.busy, onClick = { viewModel.open(item.uuid) })
                }
            } else {
                item { SpoolButton(text = stringResource(Res.string.conflict_refresh), enabled = !state.busy, onClick = viewModel::refresh) }
                if (review != null) {
                    item { Text(stringResource(Res.string.conflict_compare_notice), style = MaterialTheme.typography.bodyMedium) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium)) {
                            Column(Modifier.weight(1f)) {
                                SpoolHeadingText(text = stringResource(Res.string.conflict_local), icon = Icons.Outlined.PhoneAndroid)
                                Text(stringResource(if (review.local.spool.deletedAt == null) Res.string.conflict_available else Res.string.conflict_deleted), style = MaterialTheme.typography.bodyMedium)
                            }
                            Column(Modifier.weight(1f)) {
                                SpoolHeadingText(text = stringResource(Res.string.conflict_cloud), icon = Icons.Outlined.Cloud)
                                Text(stringResource(if (review.cloud.spool.deletedAt == null) Res.string.conflict_available else Res.string.conflict_deleted), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    item { SpoolComparison(review.local.spool, review.cloud.spool, state.currency) }
                    item { Text(stringResource(Res.string.conflict_archive_notice), style = MaterialTheme.typography.bodySmall) }
                    item { SpoolButton(text = stringResource(Res.string.conflict_keep_local), enabled = !state.busy,
                        onClick = { viewModel.requestChoice(InventoryConflictChoice.Local) }) }
                    item { SpoolButton(text = stringResource(Res.string.conflict_keep_cloud), enabled = !state.busy,
                        onClick = { viewModel.requestChoice(InventoryConflictChoice.Cloud) }) }
                    item { SpoolHeadingText(text = stringResource(Res.string.conflict_local_history), icon = Icons.Outlined.History) }
                    item { Text(stringResource(Res.string.conflict_history_counts, review.local.logs.count { it.deletedAt == null }, review.local.logs.count { it.deletedAt != null }), style = MaterialTheme.typography.bodyMedium) }
                    items(review.local.logs, key = { "local_" + it.uuid }) { PrintVersion(it, state.currency) }
                    item { SpoolHeadingText(text = stringResource(Res.string.conflict_cloud_history), icon = Icons.Outlined.History) }
                    item { Text(stringResource(Res.string.conflict_history_counts, review.cloud.logs.count { it.deletedAt == null }, review.cloud.logs.count { it.deletedAt != null }), style = MaterialTheme.typography.bodyMedium) }
                    items(review.cloud.logs, key = { "cloud_" + it.uuid }) { PrintVersion(it, state.currency) }

                }

            }
        }
    }
    val choice = state.confirmation
    if (choice != null && review != null) {
        val localDeleted = review.local.spool.deletedAt != null
        val cloudDeleted = review.cloud.spool.deletedAt != null
        val message = when {
            localDeleted && cloudDeleted -> Res.string.conflict_both_deleted_body
            choice == InventoryConflictChoice.Cloud && cloudDeleted -> Res.string.conflict_cloud_deleted_body
            choice == InventoryConflictChoice.Cloud -> Res.string.conflict_confirm_cloud_body
            localDeleted -> Res.string.conflict_local_deleted_body
            cloudDeleted -> Res.string.conflict_copy_spool_body
            else -> Res.string.conflict_confirm_local_body
        }
        AlertDialog(onDismissRequest = viewModel::dismissConfirmation, shape = MaterialTheme.shapes.medium,
            title = { Text(stringResource(if (choice == InventoryConflictChoice.Local) Res.string.conflict_confirm_local else Res.string.conflict_confirm_cloud)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium)) {
                    Text(stringResource(message), style = MaterialTheme.typography.bodyMedium)
                    if (choice == InventoryConflictChoice.Local && !localDeleted && !cloudDeleted &&
                        review.local.logs.any { local -> local.deletedAt == null && review.cloud.logs.any { it.uuid == local.uuid && it.deletedAt != null } }) {
                        Text(stringResource(Res.string.conflict_copy_prints_notice), style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(stringResource(Res.string.conflict_archive_notice), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = viewModel::confirm) { Text(stringResource(Res.string.conflict_confirm)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissConfirmation) { Text(stringResource(Res.string.btn_cancel)) } })
    }
}

@Composable
private fun SpoolComparison(local: RemoteInventorySpool, cloud: RemoteInventorySpool, currency: String) {
    val empty = stringResource(Res.string.conflict_not_set)
    @Composable fun grams(value: Double?) = value?.let { stringResource(Res.string.conflict_grams, it.toString()) } ?: empty
    @Composable fun temperature(value: Int) = stringResource(Res.string.conflict_temperature, value)
    val fields = listOf(
        Triple(stringResource(Res.string.conflict_brand), local.brand.ifBlank { empty }, cloud.brand.ifBlank { empty }),
        Triple(stringResource(Res.string.conflict_material), local.material.ifBlank { empty }, cloud.material.ifBlank { empty }),
        Triple(stringResource(Res.string.conflict_color), local.colorName.ifBlank { empty }, cloud.colorName.ifBlank { empty }),
        Triple(stringResource(Res.string.conflict_color_code), local.colorHex.toULong().toString(16), cloud.colorHex.toULong().toString(16)),
        Triple(stringResource(Res.string.conflict_capacity), grams(local.totalWeight), grams(cloud.totalWeight)),
        Triple(stringResource(Res.string.conflict_weight), grams(local.currentWeight), grams(cloud.currentWeight)),
        Triple(stringResource(Res.string.conflict_price), local.price.formatAsCurrency(currency), cloud.price.formatAsCurrency(currency)),
        Triple(stringResource(Res.string.conflict_nozzle), temperature(local.tempNozzle), temperature(cloud.tempNozzle)),
        Triple(stringResource(Res.string.conflict_bed), temperature(local.tempBed), temperature(cloud.tempBed)),
        Triple(stringResource(Res.string.conflict_notes), local.note.ifBlank { empty }, cloud.note.ifBlank { empty }),
        Triple(stringResource(Res.string.conflict_created), local.timestamp.toReadableDate(), cloud.timestamp.toReadableDate()),
        Triple(stringResource(Res.string.conflict_dry_weight), grams(local.dryBaselineWeight), grams(cloud.dryBaselineWeight)),
        Triple(stringResource(Res.string.conflict_dry_tare), grams(local.dryBaselineTareGrams), grams(cloud.dryBaselineTareGrams)),
        Triple(stringResource(Res.string.conflict_last_tare), grams(local.lastWeighedTareGrams), grams(cloud.lastWeighedTareGrams)),
        Triple(stringResource(Res.string.conflict_last_dried), local.lastDriedAt?.toReadableDate() ?: empty, cloud.lastDriedAt?.toReadableDate() ?: empty),
    )
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium)) {
        fields.forEach { (label, left, right) ->
            SpoolHeadingText(text = label, icon = Icons.Outlined.Info)
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium)) {
                Text(left, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(right, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun PrintVersion(log: RemoteInventoryLog, currency: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall)) {
        Text(log.title.ifBlank { stringResource(Res.string.conflict_untitled_print) }, style = MaterialTheme.typography.titleMedium)
        Text(stringResource(if (log.deletedAt != null) Res.string.conflict_deleted else Res.string.conflict_available), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(if (log.isFailure) Res.string.conflict_failure else Res.string.conflict_success), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(Res.string.conflict_print_values, stringResource(Res.string.conflict_grams, log.gramsUsed.toString()), log.pricePerPrint.formatAsCurrency(currency)), style = MaterialTheme.typography.bodyMedium)
        Text(log.timestamp.toReadableDate(), style = MaterialTheme.typography.bodySmall)
    }
}
