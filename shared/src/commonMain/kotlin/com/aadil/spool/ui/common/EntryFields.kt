package com.aadil.spool.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Factory
import androidx.compose.material.icons.outlined.LineWeight
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import spool.shared.generated.resources.*
import com.aadil.spool.ui.components.SpoolHeadingText
import com.aadil.spool.ui.components.SpoolButton
import com.aadil.spool.ui.components.SpoolDropDownMenu
import com.aadil.spool.ui.components.SpoolOutlinedTextField
import com.aadil.spool.ui.screens.entry.ColorSelectionGrid
import com.aadil.spool.feature.entry.SpoolEntryUiState
import com.aadil.spool.ui.theme.Dimens
import com.aadil.spool.utils.formatAsCurrency
import com.aadil.spool.core.model.SpoolLists
import com.aadil.spool.core.model.computeRemainingWeight
import com.aadil.spool.core.model.toParseLocalizedDouble

@Composable
fun EntryFields(
    uiState: SpoolEntryUiState,
    onBrandValueChange: (String) -> Unit,
    onMaterialValueChange: (String) -> Unit,
    onPriceValueChange: (String) -> Unit,
    onInitialWeightValueChange: (String) -> Unit,
    onColorNameChange: (String) -> Unit,
    onColorValueChange: (Long) -> Unit,
    onCurrentWeightValueChange: (String) -> Unit,
    onNozzleTempValueChange: (String) -> Unit,
    onBedTempValueChange: (String) -> Unit,
    onNoteValueChange: (String) -> Unit,
    onAddedWeightValueChange: (String) -> Unit,
    onAddedPriceValueChange: (String) -> Unit,
    selectedColor: Long,
    onSaveOrUpdateClick: () -> Unit,
    isFieldsFilled: Boolean,
    isEditMode: Boolean,
    resetState: () -> Unit,
    modifier: Modifier = Modifier,
    selectedCurrency: String,
) {
    var hasResetForm by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!isEditMode && !hasResetForm) {
            hasResetForm = true
            resetState()
        }
    }

    val scrollState = rememberScrollState()
    var showScaleDialog by remember { mutableStateOf(false) }
    var tareGrams by remember { mutableStateOf(SpoolLists.DEFAULT_TARE_GRAMS) }

    Column(
        modifier = modifier
            .padding(horizontal = Dimens.PaddingMedium)
            .verticalScroll(scrollState)
            .imePadding()
    ) {

        Spacer(modifier = Modifier.height(Dimens.PaddingLarge))
        SpoolHeadingText(
            text = "Spool Details",
            icon = Icons.Outlined.AddBox
        )
        Spacer(modifier = Modifier.height(Dimens.PaddingMedium))
        SpoolOutlinedTextField(
            value = uiState.brand,
            onValueChange = onBrandValueChange,
            label = stringResource(Res.string.label_brand),
            placeholder = stringResource(Res.string.hint_brand),
            leadingIcon = Icons.Outlined.Factory,
            isError = isFieldsFilled,
            supportingText = stringResource(Res.string.brand_error_message),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
        )
        Spacer(modifier = Modifier.height(Dimens.PaddingTiny))
        SpoolDropDownMenu(
            value = uiState.material,
            onValueChange = onMaterialValueChange,
            label = stringResource(Res.string.label_material),
            placeholder = stringResource(Res.string.hint_material),
            leadingIcon = Icons.Outlined.Circle,
            isError = isFieldsFilled,
            supportingText = stringResource(Res.string.material_error_message),
        )
        // Price
        Spacer(modifier = Modifier.height(Dimens.PaddingMedium))
        val placeholderPrice = 200.0
        val formattedPlaceholderPrice = placeholderPrice.formatAsCurrency(selectedCurrency)
        SpoolOutlinedTextField(
            value = uiState.price,
            onValueChange = onPriceValueChange,
            label = stringResource(Res.string.label_spool_price),
            placeholder = formattedPlaceholderPrice,
            leadingIcon = Icons.Outlined.CurrencyExchange,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(modifier = Modifier.height(Dimens.PaddingLarge))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(Dimens.PaddingLarge))

        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            SpoolHeadingText(
                text = "Filament Color",
                icon = Icons.Outlined.ColorLens,
            )
                Icon(
                    imageVector = Icons.Filled.Circle,
                    contentDescription = null,
                    tint = Color(selectedColor),
                    modifier = Modifier
                        .align(alignment = Alignment.CenterEnd)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = MaterialTheme.shapes.extraLarge
                        )

                )
        }

        Spacer(modifier = Modifier.height(Dimens.PaddingMedium))
        ColorSelectionGrid(
            selectedColor = selectedColor,
            onSelectedColor = onColorValueChange
        )
        Spacer(modifier = Modifier.height(Dimens.PaddingMedium))
        SpoolOutlinedTextField(
            value = uiState.colorName,
            onValueChange = onColorNameChange,
            label = stringResource(Res.string.label_color),
            placeholder = stringResource(Res.string.hint_color),
            leadingIcon = Icons.Outlined.Colorize,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )
        Spacer(modifier = Modifier.height(Dimens.PaddingLarge))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(Dimens.PaddingLarge))
        SpoolHeadingText(text = "Specs", icon = Icons.Outlined.PointOfSale)
        Spacer(modifier = Modifier.height(Dimens.PaddingMedium))
        if (!isEditMode) {
            SpoolOutlinedTextField(
                value = uiState.totalWeight,
                onValueChange = onInitialWeightValueChange,
                label = stringResource(Res.string.label_initial_weight),
                placeholder = stringResource(Res.string.hint_total_wight),
                leadingIcon = Icons.Outlined.MonitorWeight,
                singleLine = true,
                isError = isFieldsFilled,
                supportingText = stringResource(Res.string.weight_error_message),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Spacer(modifier = Modifier.height(Dimens.PaddingTiny))
            WeighInHeadingRow(
                headingText = stringResource(Res.string.heading_remaining_weight),
                headingIcon = Icons.Outlined.MonitorWeight,
                tareGrams = tareGrams,
                onWeighInClick = { showScaleDialog = true }
            )
            SpoolOutlinedTextField(
                value = uiState.currentWeight,
                onValueChange = onCurrentWeightValueChange,
                label = stringResource(Res.string.label_remaining_weight),
                placeholder = stringResource(Res.string.hint_total_wight),
                leadingIcon = Icons.Outlined.LineWeight,
                singleLine = true,
                isError = isFieldsFilled,
                supportingText = stringResource(Res.string.weight_error_message),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        if (isEditMode) {
            Spacer(modifier = Modifier.height(Dimens.PaddingTiny))
            WeighInHeadingRow(
                headingText = stringResource(Res.string.heading_current_weight),
                headingIcon = Icons.Outlined.LineWeight,
                tareGrams = tareGrams,
                onWeighInClick = { showScaleDialog = true }
            )
            SpoolOutlinedTextField(
                value = uiState.currentWeight,
                onValueChange = onCurrentWeightValueChange,
                label = stringResource(Res.string.label_current_weight),
                placeholder = stringResource(Res.string.hint_total_wight),
                leadingIcon = Icons.Outlined.LineWeight,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Spacer(modifier = Modifier.height(Dimens.PaddingTiny))
            SpoolOutlinedTextField(
                value = uiState.tempNozzle,
                onValueChange = onNozzleTempValueChange,
                label = stringResource(Res.string.label_temp_nozzle),
                placeholder = stringResource(Res.string.hint_nozzle),
                leadingIcon = Icons.Outlined.Thermostat,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            Spacer(modifier = Modifier.height(Dimens.PaddingTiny))
            SpoolOutlinedTextField(
                value = uiState.tempBed,
                onValueChange = onBedTempValueChange,
                label = stringResource(Res.string.label_temp_bed),
                placeholder = stringResource(Res.string.hint_bed),
                leadingIcon = Icons.Outlined.LocalFireDepartment,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Spacer(modifier = Modifier.height(Dimens.PaddingLarge))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(Dimens.PaddingLarge))
            SpoolHeadingText(text = stringResource(Res.string.restock_filament), icon = Icons.Outlined.Update)

            SpoolOutlinedTextField(
                value = uiState.addedWeight,
                onValueChange = onAddedWeightValueChange,
                label = stringResource(Res.string.additional_weight_label),
                placeholder = stringResource(Res.string.additional_weight_placeholder),
                leadingIcon = Icons.Outlined.MonitorWeight,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Spacer(modifier = Modifier.height(Dimens.PaddingTiny))
            SpoolOutlinedTextField(
                value = uiState.addedPrice,
                onValueChange = onAddedPriceValueChange,
                label = stringResource(Res.string.additional_price_label),
                placeholder = formattedPlaceholderPrice,
                leadingIcon = Icons.Outlined.CurrencyExchange,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Spacer(modifier = Modifier.height(Dimens.PaddingLarge))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(Dimens.PaddingLarge))
            SpoolHeadingText(text = stringResource(Res.string.notes_heading), icon = Icons.AutoMirrored.Outlined.StickyNote2)
            Spacer(modifier = Modifier.height(Dimens.PaddingMedium))
            SpoolOutlinedTextField(
                value = uiState.note,
                onValueChange = onNoteValueChange,
                label = stringResource(Res.string.label_note),
                placeholder = stringResource(Res.string.hint_note),
                leadingIcon = Icons.Outlined.EditNote,
                singleLine = false,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        }
        Spacer(modifier = Modifier.height(Dimens.PaddingExtraLarge))
        SpoolButton(
            text = if (!isEditMode) stringResource(Res.string.btn_save) else stringResource(Res.string.btn_update),
                icon = Icons.Outlined.Save,
                contentDescription = if (!isEditMode) stringResource(Res.string.btn_save) else stringResource(
                    Res.string.btn_update
                ),
            onClick = onSaveOrUpdateClick,
            buttonContainerColor = MaterialTheme.colorScheme.primary,
            buttonContentColor = MaterialTheme.colorScheme.onPrimary,
            enabled = true,
            hasBorder = false,
            modifier = Modifier.padding(bottom = Dimens.PaddingExtraLarge)
        )
    }

    if (showScaleDialog) {
        ScaleWeightDialog(
            initialTareGrams = tareGrams,
            onTareChange = { tareGrams = it },
            onApply = { remaining ->
                onCurrentWeightValueChange(remaining.toWeightText())
                showScaleDialog = false
            },
            onDismiss = { showScaleDialog = false }
        )
    }
}

@Composable
private fun WeighInHeadingRow(
    headingText: String,
    headingIcon: ImageVector,
    tareGrams: Double,
    onWeighInClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        SpoolHeadingText(
            text = headingText,
            icon = headingIcon,
        )
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .clickable { onWeighInClick() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.HeightOrWidth)
        ) {
            Icon(
                imageVector = Icons.Outlined.Scale,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Dimens.IconMedium)
            )
            Text(
                text = stringResource(Res.string.tare_indicator, tareGrams.toWeightText()),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ScaleWeightDialog(
    initialTareGrams: Double,
    onTareChange: (Double) -> Unit,
    onApply: (Double) -> Unit,
    onDismiss: () -> Unit
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
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    remaining?.let { onApply(it) }
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

private fun Double.toWeightText(): String =
    if (this % 1.0 == 0.0) this.toLong().toString() else this.toString()

@Preview
@Composable
fun EntryFieldsPreview() {
    val uiState = SpoolEntryUiState(
        brand = "Brand",
        material = "PLA",
        colorName = "Galaxy Must Green",
        totalWeight = "1000"
    )
    EntryFields(
        uiState = uiState,
        onBrandValueChange = {},
        onMaterialValueChange = {},
        onPriceValueChange = {},
        onInitialWeightValueChange = {},
        onColorNameChange = {},
        onColorValueChange = {},
        onCurrentWeightValueChange = {},
        onNozzleTempValueChange = {},
        onBedTempValueChange = {},
        onNoteValueChange = {},
        onAddedWeightValueChange = {},
        onAddedPriceValueChange = {},
        selectedColor = 0xFF000000,
        onSaveOrUpdateClick = {},
        isEditMode = true,
        isFieldsFilled = true,
        resetState = {},
        modifier = Modifier,
        selectedCurrency = "USD"
    )
}