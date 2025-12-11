package com.kashmir.spool.ui.screens.entry

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kashmir.spool.R
import com.kashmir.spool.ui.common.EntryFields
import com.kashmir.spool.ui.common.SpoolAppBar

@Composable
fun SpoolEntryScreen(
    uiState: SpoolEntryUiState,
    onNavigateUp: () -> Unit,
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
    isError: Boolean,
    isEditMode: Boolean,
    resetState: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            SpoolAppBar(
                title = stringResource(R.string.title_new_spool),
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
            onInitialWeightValueChange = onInitialWeightValueChange,
            onColorNameChange = onColorNameChange,
            onColorValueChange = onColorValueChange,
            onCurrentWeightValueChange = onCurrentWeightValueChange,
            onNozzleTempValueChange = onNozzleTempValueChange,
            onBedTempValueChange = onBedTempValueChange,
            onNoteValueChange = onNoteValueChange,
            selectedColor = selectedColor,
            onSaveOrUpdateClick = onSaveOrUpdateClick,
            isValid = isValid,
            isWeightValid = isError,
            isEditMode = isEditMode,
            resetState = resetState,
            modifier = Modifier.padding(paddingValues = paddingValues)
        )
    }
}