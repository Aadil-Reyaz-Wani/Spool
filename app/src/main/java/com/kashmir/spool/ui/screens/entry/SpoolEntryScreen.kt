package com.kashmir.spool.ui.screens.entry

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.kashmir.spool.R
import com.kashmir.spool.ui.common.SpoolAppBar
import com.kashmir.spool.ui.theme.Dimens

@Composable
fun SpoolEntryScreen(
    onNavigateUp: () -> Unit,
    uiState: SpoolEntryUiState,
    onBrandValueChange: (String) -> Unit,
    onMaterialValueChange: (String) -> Unit,
    onInitialWeightValueChange: (String) -> Unit,
    onColorValueChange: (Long) -> Unit,
    selectedColor: Long,
    onSaveClick: () -> Unit,
    isValid: Boolean,
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
            modifier = Modifier.padding(paddingValues = paddingValues),
            onBrandValueChange = onBrandValueChange,
            onMaterialValueChange = onMaterialValueChange,
            onInitialWeightValueChange = onInitialWeightValueChange,
            onColorValueChange = onColorValueChange,
            selectedColor = selectedColor,
            onSaveClick = onSaveClick,
            isValid = isValid
        )
    }
}


@Composable
fun EntryFields(
    uiState: SpoolEntryUiState,
    onBrandValueChange: (String) -> Unit,
    onMaterialValueChange: (String) -> Unit,
    onInitialWeightValueChange: (String) -> Unit,
    onColorValueChange: (Long) -> Unit,
    selectedColor: Long,
    onSaveClick: () -> Unit,
    isValid: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(horizontal = Dimens.PaddingMedium)
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
            singleLine = true,
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
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(Dimens.PaddingSmall))
        OutlinedTextField(
            value = uiState.totalWeight.toString(),
            onValueChange = onInitialWeightValueChange,
            label = {
                Text(
                    text = stringResource(R.string.label_initial_weight),
                    style = MaterialTheme.typography.labelMedium
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(Dimens.PaddingSmall))
        ColorSelectionGrid(
            selectedColor = selectedColor,
            onSelectedColor = onColorValueChange
        )
        Spacer(modifier = Modifier.height(Dimens.PaddingSmall))

        Button(
            onClick = onSaveClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Dimens.BorderRadius),
            enabled = isValid
        ) {
            Text(
                text = stringResource(R.string.btn_save),
                style = MaterialTheme.typography.labelLarge,
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
        totalWeight = 1000
    )
    EntryFields(
        uiState = uiState,
        onBrandValueChange = {},
        onMaterialValueChange = {},
        onInitialWeightValueChange = {},
        onColorValueChange = {},
        selectedColor = 0xFF000000,
        onSaveClick = {},
        isValid = true
    )
}
