package com.aadil.spool.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.aadil.spool.core.model.RemoteInventoryLog
import com.aadil.spool.core.model.RemoteInventorySpool
import com.aadil.spool.ui.theme.Dimens
import com.aadil.spool.utils.formatAsCurrency
import com.aadil.spool.utils.toReadableDate
import org.jetbrains.compose.resources.stringResource
import spool.shared.generated.resources.*

@Composable
internal fun inventoryVersionFields(spool: RemoteInventorySpool, currency: String): List<Pair<String, String>> {
    val empty = stringResource(Res.string.conflict_not_set)
    @Composable fun grams(value: Double?) = value?.let { stringResource(Res.string.conflict_grams, it.toString()) } ?: empty
    @Composable fun temperature(value: Int) = stringResource(Res.string.conflict_temperature, value)
    return listOf(
        stringResource(Res.string.conflict_brand) to spool.brand.ifBlank { empty },
        stringResource(Res.string.conflict_material) to spool.material.ifBlank { empty },
        stringResource(Res.string.conflict_color) to spool.colorName.ifBlank { empty },
        stringResource(Res.string.conflict_color_code) to spool.colorHex.toULong().toString(16),
        stringResource(Res.string.conflict_capacity) to grams(spool.totalWeight),
        stringResource(Res.string.conflict_weight) to grams(spool.currentWeight),
        stringResource(Res.string.conflict_price) to spool.price.formatAsCurrency(currency),
        stringResource(Res.string.conflict_nozzle) to temperature(spool.tempNozzle),
        stringResource(Res.string.conflict_bed) to temperature(spool.tempBed),
        stringResource(Res.string.conflict_notes) to spool.note.ifBlank { empty },
        stringResource(Res.string.conflict_created) to spool.timestamp.toReadableDate(),
        stringResource(Res.string.conflict_dry_weight) to grams(spool.dryBaselineWeight),
        stringResource(Res.string.conflict_dry_tare) to grams(spool.dryBaselineTareGrams),
        stringResource(Res.string.conflict_last_tare) to grams(spool.lastWeighedTareGrams),
        stringResource(Res.string.conflict_last_dried) to (spool.lastDriedAt?.toReadableDate() ?: empty),
    )
}

@Composable
internal fun InventoryPrintVersion(log: RemoteInventoryLog, currency: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall)) {
        Text(log.title.ifBlank { stringResource(Res.string.conflict_untitled_print) }, style = MaterialTheme.typography.titleMedium)
        Text(stringResource(if (log.deletedAt != null) Res.string.conflict_deleted else Res.string.conflict_available), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(if (log.isFailure) Res.string.conflict_failure else Res.string.conflict_success), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(Res.string.conflict_print_values, stringResource(Res.string.conflict_grams, log.gramsUsed.toString()), log.pricePerPrint.formatAsCurrency(currency)), style = MaterialTheme.typography.bodyMedium)
        Text(log.timestamp.toReadableDate(), style = MaterialTheme.typography.bodySmall)
    }
}
