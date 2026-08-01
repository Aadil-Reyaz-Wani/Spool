package com.aadil.spool.ui.screens.entry

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aadil.spool.R
import com.aadil.spool.ui.common.EntryFields
import com.aadil.spool.ui.common.SpoolAppBar
import com.aadil.spool.feature.entry.SpoolEntryUiState

@Composable
fun SpoolEntryScreen(
    uiState: SpoolEntryUiState,
    onNavigateUp: () -> Unit,
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
    isError: Boolean,
    isEditMode: Boolean,
    resetState: () -> Unit,
    modifier: Modifier = Modifier,
    selectedCurrency: String
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            SpoolAppBar(
                title = if (!isEditMode) stringResource(R.string.title_new_spool) else stringResource(R.string.title_update_spool),
                canNavigateBack = true,
                navigateUp = onNavigateUp,
                modifier = modifier
            )
        }
    ) { paddingValues ->
        EntryFields(
            uiState = uiState,
            onBrandValueChange = onBrandValueChange,
            onMaterialValueChange = onMaterialValueChange,
            onPriceValueChange = onPriceValueChange,
            onInitialWeightValueChange = onInitialWeightValueChange,
            onColorNameChange = onColorNameChange,
            onColorValueChange = onColorValueChange,
            onCurrentWeightValueChange = onCurrentWeightValueChange,
            onNozzleTempValueChange = onNozzleTempValueChange,
            onBedTempValueChange = onBedTempValueChange,
            onNoteValueChange = onNoteValueChange,
            onAddedWeightValueChange = onAddedWeightValueChange,
            onAddedPriceValueChange = onAddedPriceValueChange,
            selectedColor = selectedColor,
            onSaveOrUpdateClick = onSaveOrUpdateClick,
            isFieldsFilled = isError,
            isEditMode = isEditMode,
            resetState = resetState,
            modifier = Modifier.padding(paddingValues = paddingValues),
            selectedCurrency = selectedCurrency
        )
    }
}