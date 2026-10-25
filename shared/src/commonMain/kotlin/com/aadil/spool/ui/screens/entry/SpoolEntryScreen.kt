package com.aadil.spool.ui.screens.entry

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import spool.shared.generated.resources.*
import com.aadil.spool.ui.common.EntryFields
import com.aadil.spool.ui.common.SpoolAppBar
import com.aadil.spool.feature.entry.SpoolEntryUiState
import com.aadil.spool.feature.entry.SpoolEntryError

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
    error: SpoolEntryError?,
    isSaving: Boolean,
    isLoading: Boolean,
    isEditMode: Boolean,
    modifier: Modifier = Modifier,
    selectedCurrency: String
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            SpoolAppBar(
                title = if (!isEditMode) stringResource(Res.string.title_new_spool) else stringResource(Res.string.title_update_spool),
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
            isFieldsFilled = error == SpoolEntryError.INVALID_INPUT,
            isEditMode = isEditMode,
            isSaving = isSaving,
            isLoading = isLoading,
            restockError = error == SpoolEntryError.INVALID_RESTOCK,
            errorMessage = when (error) {
                SpoolEntryError.INVALID_INPUT -> stringResource(Res.string.entry_invalid_fields)
                SpoolEntryError.INVALID_RESTOCK -> stringResource(Res.string.entry_invalid_restock)
                SpoolEntryError.LOAD_FAILED -> stringResource(Res.string.entry_load_failed)
                SpoolEntryError.SAVE_FAILED -> stringResource(Res.string.entry_save_failed)
                null -> null
            },
            modifier = Modifier.padding(paddingValues = paddingValues),
            selectedCurrency = selectedCurrency
        )
    }
}