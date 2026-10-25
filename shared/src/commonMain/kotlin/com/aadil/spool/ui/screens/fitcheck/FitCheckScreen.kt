package com.aadil.spool.ui.screens.fitcheck

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aadil.spool.data.entity.Filament
import com.aadil.spool.ui.common.SpoolAppBar
import com.aadil.spool.ui.components.SpoolButton
import com.aadil.spool.ui.components.SpoolOutlinedTextField
import com.aadil.spool.ui.theme.Dimens
import org.jetbrains.compose.resources.stringResource
import spool.shared.generated.resources.Res
import spool.shared.generated.resources.fit_check
import spool.shared.generated.resources.fit_check_check
import spool.shared.generated.resources.fit_check_closest
import spool.shared.generated.resources.fit_check_estimated_cost
import spool.shared.generated.resources.fit_check_grams_needed
import spool.shared.generated.resources.fit_check_na
import spool.shared.generated.resources.fit_check_no_match
import spool.shared.generated.resources.fit_check_results
import spool.shared.generated.resources.fit_check_subtitle
import kotlin.math.round


data class FitCheckResult(
    val spool: Filament,
    val fits: Boolean,
    val excess: Double,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FitCheckScreen(
    gramsNeededInitial: Double = 0.0,
    listOfSpools: List<Filament>,
    navigateUp: () -> Unit,
    onSpoolClick: (Int) -> Unit = {},
) {
    var gramsValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(
            if (gramsNeededInitial > 0.0) {
                val text = gramsNeededInitial.toString()
                TextFieldValue(text = text, selection = TextRange(text.length))
            } else {
                TextFieldValue("")
            }
        )
    }
    var hasAutoFocused by rememberSaveable { mutableStateOf(false) }
    var results by rememberSaveable { mutableStateOf<List<FitCheckResult>>(emptyList()) }
    var hasChecked by rememberSaveable { mutableStateOf(gramsNeededInitial > 0.0) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (gramsNeededInitial == 0.0 && !hasAutoFocused) {
            hasAutoFocused = true
            focusRequester.requestFocus()
            keyboardController?.show()
        } else if (gramsNeededInitial > 0.0) {
            hasAutoFocused = true
        }
    }

    fun runCheck() {
        keyboardController?.hide()
        val needed = gramsValue.text.toDoubleOrNull() ?: 0.0
        if (needed <= 0.0) {
            results = emptyList()
            hasChecked = false
            return
        }
        val all = listOfSpools.map { spool ->
            FitCheckResult(
                spool = spool,
                fits = spool.currentWeight >= needed,
                excess = spool.currentWeight - needed,
            )
        }
        results = all.sortedWith(
            compareByDescending<FitCheckResult> { it.fits }
                .thenBy { if (it.fits) it.excess else -it.excess }
        )
        hasChecked = true
    }

    LaunchedEffect(gramsNeededInitial) {
        if (gramsNeededInitial > 0.0) {
            runCheck()
        }
    }

    Scaffold(
        topBar = {
            SpoolAppBar(
                title = stringResource(Res.string.fit_check),
                canNavigateBack = true,
                navigateUp = navigateUp,
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(Dimens.PaddingMedium),
            verticalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall)) {
                    Text(
                        text = stringResource(Res.string.fit_check_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SpoolOutlinedTextField(
                            value = gramsValue,
                            onValueChange = { gramsValue = it },
                            label = stringResource(Res.string.fit_check_grams_needed),
                            placeholder = stringResource(Res.string.fit_check_grams_needed),
                            modifier = Modifier.weight(1f).focusRequester(focusRequester),
                            leadingIcon = Icons.Outlined.Scale,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }
                    SpoolButton(
                        text = stringResource(Res.string.fit_check_check),
                        icon = Icons.Outlined.Search,
                        onClick = { runCheck() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (hasChecked) {
                val fitsList = results.filter { it.fits }
                val noFits = results.filter { !it.fits }
                if (fitsList.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(Res.string.fit_check_results),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(fitsList) { result ->
                        FitCheckCard(result = result, gramsText = gramsValue.text, onSpoolClick = onSpoolClick)
                    }
                }
                if (noFits.isNotEmpty()) {
                    item {
                        Text(
                            text = if (fitsList.isEmpty()) stringResource(Res.string.fit_check_no_match) else stringResource(Res.string.fit_check_closest),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(noFits.take(5)) { result ->
                        FitCheckCard(result = result, gramsText = gramsValue.text, onSpoolClick = onSpoolClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun FitCheckCard(
    result: FitCheckResult,
    gramsText: String,
    onSpoolClick: (Int) -> Unit,
) {
    val needed = gramsText.toDoubleOrNull() ?: 0.0
    Card(
        onClick = { onSpoolClick(result.spool.id) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(Dimens.PaddingMedium),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${result.spool.brand} • ${result.spool.material}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${result.spool.currentWeight.toInt()}g left",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = result.spool.colorName,
                style = MaterialTheme.typography.bodyMedium
            )
            if (result.spool.price > 0.0 && needed > 0.0 && result.spool.totalWeight > 0.0) {
                val cost = (needed / result.spool.totalWeight) * result.spool.price
                val currency = ""
                Text(
                    text = stringResource(Res.string.fit_check_estimated_cost, "$currency ${kotlin.math.round(cost * 100.0) / 100.0}"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (needed > 0.0) {
                Text(
                    text = stringResource(Res.string.fit_check_estimated_cost, stringResource(Res.string.fit_check_na)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
