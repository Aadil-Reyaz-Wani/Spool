package com.aadil.spool.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LineWeight
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import spool.shared.generated.resources.Res
import spool.shared.generated.resources.btn_cancel
import spool.shared.generated.resources.heading_weigh_in
import spool.shared.generated.resources.hint_total_wight
import spool.shared.generated.resources.weigh_in_apply
import spool.shared.generated.resources.weigh_in_custom_tare
import spool.shared.generated.resources.weigh_in_hint
import spool.shared.generated.resources.weigh_in_low_warning
import spool.shared.generated.resources.weigh_in_remaining
import spool.shared.generated.resources.weigh_in_scale_reading
import spool.shared.generated.resources.weigh_in_stored
import spool.shared.generated.resources.weigh_in_tare
import com.aadil.spool.core.model.SpoolLists
import com.aadil.spool.core.model.computeRemainingWeight
import com.aadil.spool.core.model.isWeighInSuspicious
import com.aadil.spool.core.model.toParseLocalizedDouble
import com.aadil.spool.ui.components.SpoolDropDownMenu
import com.aadil.spool.ui.components.SpoolOutlinedTextField
import com.aadil.spool.ui.theme.Dimens

@Composable
fun ScaleWeightDialog(
    initialTareGrams: Double,
    onTareChange: (Double) -> Unit,
    onApply: (remainingGrams: Double, tareGrams: Double) -> Unit,
    onDismiss: () -> Unit,
    expectedRemainingGrams: Double? = null
) {
    val presets = SpoolLists.emptySpoolWeights
    val matchedPreset = presets.firstOrNull { it.grams == initialTareGrams }
    var selectedTareName by remember { mutableStateOf(matchedPreset?.name ?: SpoolLists.CUSTOM_TARE_LABEL) }
    var customTare by remember {
        mutableStateOf(if (matchedPreset == null) initialTareGrams.toWeightText() else "")
    }
    var gross by remember { mutableStateOf("") }

    val isCustom = selectedTareName == SpoolLists.CUSTOM_TARE_LABEL
    val tareGrams = if (isCustom) {
        customTare.toParseLocalizedDouble() ?: 0.0
    } else {
        presets.firstOrNull { it.name == selectedTareName }?.grams ?: 0.0
    }
    val remaining = computeRemainingWeight(gross.toParseLocalizedDouble() ?: 0.0, tareGrams)

    AlertDialog(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium
            ),
        shape = MaterialTheme.shapes.medium,
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Scale,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Dimens.IconLarge)
            )
        },
        title = {
            Text(
                text = stringResource(Res.string.heading_weigh_in),
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                SpoolOutlinedTextField(
                    value = gross,
                    onValueChange = { gross = it },
                    label = stringResource(Res.string.weigh_in_scale_reading),
                    placeholder = stringResource(Res.string.hint_total_wight),
                    leadingIcon = Icons.Outlined.MonitorWeight,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(modifier = Modifier.height(Dimens.PaddingTiny))
                SpoolDropDownMenu(
                    value = selectedTareName,
                    onValueChange = { name ->
                        selectedTareName = name
                        presets.firstOrNull { it.name == name }?.let { onTareChange(it.grams) }
                    },
                    label = stringResource(Res.string.weigh_in_tare),
                    leadingIcon = Icons.Outlined.LineWeight,
                    options = presets.map { it.name } + SpoolLists.CUSTOM_TARE_LABEL
                )
                if (isCustom) {
                    SpoolOutlinedTextField(
                        value = customTare,
                        onValueChange = { value ->
                            customTare = value
                            onTareChange(value.toParseLocalizedDouble() ?: 0.0)
                        },
                        label = stringResource(Res.string.weigh_in_custom_tare),
                        leadingIcon = Icons.Outlined.LineWeight,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                Spacer(modifier = Modifier.height(Dimens.PaddingMedium))
                Text(
                    text = if (remaining != null) {
                        stringResource(Res.string.weigh_in_remaining, remaining.toWeightText())
                    } else {
                        stringResource(Res.string.weigh_in_hint)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                if (expectedRemainingGrams != null) {
                    Spacer(modifier = Modifier.height(Dimens.PaddingTiny))
                    Text(
                        text = stringResource(Res.string.weigh_in_stored, expectedRemainingGrams.toWeightText()),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (remaining != null && isWeighInSuspicious(expectedRemainingGrams, remaining)) {
                        Spacer(modifier = Modifier.height(Dimens.PaddingTiny))
                        Text(
                            text = stringResource(
                                Res.string.weigh_in_low_warning,
                                (expectedRemainingGrams - remaining).toWeightText(),
                                expectedRemainingGrams.toWeightText()
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    remaining?.let { onApply(it, tareGrams) }
                    onDismiss()
                },
                enabled = remaining != null && remaining > 0,
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Scale,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(Res.string.weigh_in_apply),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(Res.string.btn_cancel),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    )
}

internal fun Double.toWeightText(): String =
    if (this % 1.0 == 0.0) this.toLong().toString() else this.toString()
