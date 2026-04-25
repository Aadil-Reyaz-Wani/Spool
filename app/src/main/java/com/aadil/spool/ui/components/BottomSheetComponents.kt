package com.aadil.spool.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateBounds
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aadil.spool.R
import com.aadil.spool.ui.common.verticalScrollbar
import com.aadil.spool.ui.screens.dashboard.FilterType
import com.aadil.spool.ui.screens.entry.ColorCircle
import com.aadil.spool.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    modifier: Modifier = Modifier,
    listOfUniqueBrandStrings: List<String>,
    listOfUniqueMaterialTypeStrings: List<String>,
    listOfUniqueColorHex: List<Long>,
    onFilterStringClick: (String, FilterType) -> Unit,
    selectedOption: String,
) {
    var expandBrand by rememberSaveable { mutableStateOf(false) }
    var expandMaterial by rememberSaveable { mutableStateOf(false) }
    var expandColor by rememberSaveable { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val clearAllFilter = stringResource(R.string.clear_all_filters_button_label)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = Dimens.PaddingMedium)
            .verticalScrollbar(scrollState)
    ) {

        // TOP HEADER
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SpoolHeadingText(
                text = stringResource(R.string.filter_by_label),
                icon = Icons.Outlined.FilterList,
            )
            OutlinedButton(
                onClick = { onFilterStringClick(clearAllFilter, FilterType.ALL) },
                modifier = Modifier.height(Dimens.ClearAllButtonHeight),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = stringResource(R.string.clear_all_button_label),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

        }
        SpoolHorizontalDivider(
            modifier = Modifier.padding(vertical = Dimens.PaddingMedium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )


        // BRAND SECTION
        FilterSectionHeader(
            headerName = stringResource(R.string.brand_card_label),
            isExpanded = expandBrand,
            onToggle = { expandBrand = !expandBrand }
        )

        FilterOptionArea(
            listOfUniqueStrings = listOfUniqueBrandStrings,
            expandOptions = expandBrand,
            selectedOption = selectedOption,
            onFilterStringClick = {brand-> onFilterStringClick(brand, FilterType.BRAND) }
        )


        // MATERIAL SECTION
        FilterSectionHeader(
            headerName = stringResource(R.string.material_card_label),
            isExpanded = expandMaterial,
            onToggle = { expandMaterial = !expandMaterial })
        FilterOptionArea(
            listOfUniqueStrings = listOfUniqueMaterialTypeStrings,
            expandOptions = expandMaterial,
            selectedOption = selectedOption,
            onFilterStringClick = {material-> onFilterStringClick(material, FilterType.MATERIAL) }
        )

        // COLOR SECTION
        FilterSectionHeader(
            headerName = stringResource(R.string.color_card_label),
            isExpanded = expandColor,
            onToggle = { expandColor = !expandColor })
        AnimatedVisibility(
            visible = expandColor,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            LazyRow(
                modifier = Modifier
                    .heightIn(max = Dimens.ScrollableColorCardHeight)
                    .padding(vertical = Dimens.PaddingSmall, horizontal = Dimens.PaddingTiny)
            ) {
                items(listOfUniqueColorHex, key = { colorHex -> colorHex.toString() }) { colorHex ->
                    val formattedColor = colorHex.toString()
                    val isSelected = formattedColor == selectedOption
                    ColorCircle(
                        colorHex = colorHex,
                        isSelected = isSelected,
                        onClick = { onFilterStringClick(formattedColor, FilterType.COLOR) },
                        modifier = Modifier
                            .padding(horizontal = Dimens.PaddingSmall)
                            .animateItem()
                            .animateContentSize()
                    )
                }
            }
        }
    }

}

@Composable
fun FilterSectionHeader(
    modifier: Modifier = Modifier, headerName: String, isExpanded: Boolean, onToggle: () -> Unit
) {

    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "arrow_rotation"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.PaddingTiny)
            .clickable(onClick = { onToggle() }),
        shape = MaterialTheme.shapes.small
    ) {
        // Top header row -> Brand & Icon
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.PaddingSmall, vertical = Dimens.PaddingTiny),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = headerName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            IconButton(
                onClick = { onToggle() },
            ) {
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = stringResource(R.string.filter_by_label),
                    modifier = Modifier
                        .size(Dimens.IconLarge)
                        .rotate(rotationAngle)
                )
            }
        }
    }
}


@Composable
fun FilterOptionArea(
    modifier: Modifier = Modifier,
    listOfUniqueStrings: List<String>,
    expandOptions: Boolean,
    selectedOption: String,
    onFilterStringClick: (String) -> Unit
) {

    AnimatedVisibility(
        visible = expandOptions,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        FlowRow(
            modifier = Modifier
                .padding(Dimens.PaddingSmall)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall),
            verticalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall)
        ) {
            listOfUniqueStrings.forEach { option ->
                FilterOptionRow(
                    modifier = Modifier,
                    option = option,
                    isSelected = option == selectedOption,
                    onClick = { onFilterStringClick(option) }
                )
            }
        }
    }
}

@Composable
fun FilterOptionRow(
    modifier: Modifier = Modifier,
    option: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val color = if (isSelected) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        Color.Transparent
    }

    Box(
        modifier = modifier
            .border(
                width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant, shape = CircleShape
            )
            .clip(CircleShape)
            .clickable { onClick() }
            .background(color)
            .padding(vertical = Dimens.PaddingTiny, horizontal = Dimens.FilterPillHorizontalPadding)
            .widthIn(max = Dimens.FilterPillMaxWidth)

    ) {
        Text(
            text = option,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
        )
    }
}