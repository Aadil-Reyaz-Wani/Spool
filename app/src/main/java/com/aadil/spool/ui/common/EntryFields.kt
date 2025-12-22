package com.aadil.spool.ui.common

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Factory
import androidx.compose.material.icons.outlined.LineWeight
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aadil.spool.R
import com.aadil.spool.ui.components.SpoolHeadingText
import com.aadil.spool.ui.components.SpoolButton
import com.aadil.spool.ui.components.SpoolDropDownMenu
import com.aadil.spool.ui.components.SpoolOutlinedTextField
import com.aadil.spool.ui.screens.entry.ColorSelectionGrid
import com.aadil.spool.ui.screens.entry.SpoolEntryUiState
import com.aadil.spool.ui.theme.Dimens

@Composable
fun EntryFields(
    uiState: SpoolEntryUiState,
    onBrandValueChange: (String) -> Unit,
    onMaterialValueChange: (String) -> Unit,
    onInitialWeightValueChange: (String) -> Unit,
    onColorNameChange: (String) -> Unit,
    onColorValueChange: (Long) -> Unit,
    onCurrentWeightValueChange: (String) -> Unit,
    onNozzleTempValueChange: (String) -> Unit,
    onBedTempValueChange: (String) -> Unit,
    onNoteValueChange: (String) -> Unit,
    selectedColor: Long,
    onSaveOrUpdateClick: () -> Unit,
    isFieldsFilled: Boolean,
    isEditMode: Boolean,
    resetState: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        if (!isEditMode) resetState()
    }

    val scrollState = rememberScrollState()

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
            label = stringResource(R.string.label_brand),
            placeholder = stringResource(R.string.hint_brand),
            leadingIcon = Icons.Outlined.Factory,
            isError = isFieldsFilled,
            supportingText = stringResource(R.string.brand_error_message),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
        )
        Spacer(modifier = Modifier.height(Dimens.PaddingTiny))

        // Here is ongoing work on dropdown menu
        SpoolDropDownMenu(
            value = uiState.material,
            onValueChange = onMaterialValueChange,
            label = stringResource(R.string.label_material),
            placeholder = stringResource(R.string.hint_material),
            leadingIcon = Icons.Outlined.Circle,
            isError = isFieldsFilled,
            supportingText = stringResource(R.string.material_error_message),
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
            label = stringResource(R.string.label_color),
            placeholder = stringResource(R.string.hint_color),
            leadingIcon = Icons.Outlined.Colorize,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )
        Spacer(modifier = Modifier.height(Dimens.PaddingLarge))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(Dimens.PaddingLarge))
        SpoolHeadingText(text = "Specs", icon = Icons.Outlined.PointOfSale)
        Spacer(modifier = Modifier.height(Dimens.PaddingMedium))
        SpoolOutlinedTextField(
            value = uiState.totalWeight,
            onValueChange = onInitialWeightValueChange,
            label = stringResource(R.string.label_initial_weight),
            placeholder = stringResource(R.string.hint_total_wight),
            leadingIcon = Icons.Outlined.MonitorWeight,
            singleLine = true,
            isError = isFieldsFilled,
            supportingText = stringResource(R.string.weight_error_message),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        if (isEditMode) {
            Spacer(modifier = Modifier.height(Dimens.PaddingTiny))
            SpoolOutlinedTextField(
                value = uiState.currentWeight,
                onValueChange = onCurrentWeightValueChange,
                label = stringResource(R.string.label_current_weight),
                placeholder = stringResource(R.string.hint_total_wight),
                leadingIcon = Icons.Outlined.LineWeight,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Spacer(modifier = Modifier.height(Dimens.PaddingTiny))
            SpoolOutlinedTextField(
                value = uiState.tempNozzle,
                onValueChange = onNozzleTempValueChange,
                label = stringResource(R.string.label_temp_nozzle),
                placeholder = stringResource(R.string.hint_nozzle),
                leadingIcon = Icons.Outlined.Thermostat,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            Spacer(modifier = Modifier.height(Dimens.PaddingTiny))
            SpoolOutlinedTextField(
                value = uiState.tempBed,
                onValueChange = onBedTempValueChange,
                label = stringResource(R.string.label_temp_bed),
                placeholder = stringResource(R.string.hint_bed),
                leadingIcon = Icons.Outlined.LocalFireDepartment,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Spacer(modifier = Modifier.height(Dimens.PaddingLarge))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(Dimens.PaddingLarge))
            SpoolHeadingText(text = "Notes (Optional)", icon = Icons.Outlined.StickyNote2)
            Spacer(modifier = Modifier.height(Dimens.PaddingMedium))
            SpoolOutlinedTextField(
                value = uiState.note,
                onValueChange = onNoteValueChange,
                label = stringResource(R.string.label_note),
                placeholder = stringResource(R.string.hint_note),
                leadingIcon = Icons.Outlined.EditNote,
                singleLine = false,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )

        }
        Spacer(modifier = Modifier.height(Dimens.PaddingExtraLarge))
        SpoolButton(
            text = if (!isEditMode) stringResource(R.string.btn_save) else stringResource(R.string.btn_update),
            icon = if (!isEditMode) Icons.Outlined.Save else Icons.Outlined.Update,
            contentDescription = if (!isEditMode) stringResource(R.string.btn_save) else stringResource(
                R.string.btn_update
            ),
            onClick = onSaveOrUpdateClick,
            buttonContainerColor = MaterialTheme.colorScheme.primary,
            buttonContentColor = MaterialTheme.colorScheme.onPrimary,
            enabled = true, // this is where i get changes
            hasBorder = false,
            modifier = Modifier.padding(bottom = Dimens.PaddingExtraLarge)
        )
    }
}

@Preview(showBackground = true)
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
        onInitialWeightValueChange = {},
        onColorNameChange = {},
        onColorValueChange = {},
        onCurrentWeightValueChange = {},
        onNozzleTempValueChange = {},
        onBedTempValueChange = {},
        onNoteValueChange = {},
        selectedColor = 0xFF000000,
        onSaveOrUpdateClick = {},
        isEditMode = true,
        isFieldsFilled = true,
        resetState = {},
        modifier = Modifier
    )
}