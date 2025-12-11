package com.kashmir.spool.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.kashmir.spool.R
import com.kashmir.spool.ui.screens.entry.ColorSelectionGrid
import com.kashmir.spool.ui.screens.entry.SpoolEntryUiState
import com.kashmir.spool.ui.theme.Dimens

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
    isValid: Boolean,
    isWeightValid: Boolean,
    isEditMode: Boolean,
    resetState: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        if(!isEditMode) resetState()
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .padding(horizontal = Dimens.PaddingMedium)
            .verticalScroll(scrollState)
            .imePadding()
    ) {
        OutlinedTextField(
            value = uiState.brand,
            onValueChange = onBrandValueChange,
            label = {
                Text(
                    text = stringResource(R.string.label_brand),
                    style = MaterialTheme.typography.labelMedium
                )
            },
            placeholder = { Text(stringResource(R.string.hint_brand)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(Dimens.PaddingSmall))
        OutlinedTextField(
            value = uiState.material,
            onValueChange = onMaterialValueChange,
            label = {
                Text(
                    text = stringResource(R.string.label_material),
                    style = MaterialTheme.typography.labelMedium
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(Dimens.PaddingSmall))
        OutlinedTextField(
            value = uiState.colorName,
            onValueChange = onColorNameChange,
            label = {
                Text(
                    text = stringResource(R.string.label_color),
                    style = MaterialTheme.typography.labelMedium
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(Dimens.PaddingSmall))
        OutlinedTextField(
            value = uiState.totalWeight,
            onValueChange = onInitialWeightValueChange,
            label = {
                Text(
                    text = stringResource(R.string.label_initial_weight),
                    style = MaterialTheme.typography.labelMedium
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = { if (isWeightValid) Text(text = stringResource(R.string.weight_error_message)) },
            isError = isWeightValid,
            modifier = Modifier.fillMaxWidth()
        )
        if (isEditMode) {
            Spacer(modifier = Modifier.height(Dimens.PaddingSmall))
            OutlinedTextField(
                value = uiState.currentWeight,
                onValueChange = onCurrentWeightValueChange,
                label = {
                    Text(
                        text = stringResource(R.string.label_current_weight),
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(Dimens.PaddingSmall))
            OutlinedTextField(
                value = uiState.tempNozzle,
                onValueChange = onNozzleTempValueChange,
                label = {
                    Text(
                        text = stringResource(R.string.label_temp_nozzle),
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(Dimens.PaddingSmall))
            OutlinedTextField(
                value = uiState.tempBed,
                onValueChange = onBedTempValueChange,
                label = {
                    Text(
                        text = stringResource(R.string.label_temp_bed),
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(Dimens.PaddingSmall))
            OutlinedTextField(
                value = uiState.note,
                onValueChange = onNoteValueChange,
                label = {
                    Text(
                        text = stringResource(R.string.label_note),
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(Dimens.PaddingSmall))
        ColorSelectionGrid(
            selectedColor = selectedColor,
            onSelectedColor = onColorValueChange
        )
        Spacer(modifier = Modifier.height(Dimens.PaddingSmall))

        Button(
            onClick = onSaveOrUpdateClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Dimens.BorderRadius),
            enabled = isValid
        ) {
            Text(
                text = if (!isEditMode)stringResource(R.string.btn_save) else stringResource(R.string.btn_update),
                style = MaterialTheme.typography.labelLarge,
                color = if (isValid) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.inversePrimary,
                fontWeight = FontWeight.Bold
            )
        }
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
        isValid = true,
        isEditMode = true,
        isWeightValid = true,
        resetState = {},
        modifier = Modifier
    )
}