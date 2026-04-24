package com.aadil.spool.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aadil.spool.R
import com.aadil.spool.ui.screens.entry.ColorCircle
import com.aadil.spool.ui.theme.Dimens
import kotlin.math.max

@Composable
fun FilterCard(
    modifier: Modifier = Modifier,
    headerName: String,
    listOfStrings: List<String>,
    selectedOption: String,
    onFilterStringClick: (String) -> Unit,
    isColorGrid: Boolean = false
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.PaddingTiny),
        shape = MaterialTheme.shapes.small
    ) {
        var showFilterOptions by rememberSaveable { mutableStateOf(false) }
        // Top header row -> Brand & Icon
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.PaddingSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = headerName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            IconButton(
                onClick = { showFilterOptions = !showFilterOptions },
            ) {
                Icon(
                    imageVector = if (!showFilterOptions) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                    contentDescription = stringResource(R.string.filter_by_label),
                    modifier = Modifier.size(Dimens.IconLarge)
                )
            }
        }


        if (showFilterOptions) {
            SpoolHorizontalDivider(
                modifier = Modifier.padding(horizontal = Dimens.PaddingSmall),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // This empty spool message is not yet in action because we are hiding the icon itself
            // when the there is no filament to filter or there is an empty table in the db.
            if (listOfStrings.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Dimens.PaddingSmall),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.empty_filter_card_message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!isColorGrid){
                LazyColumn(
                    modifier = Modifier
                        .heightIn(max = Dimens.ScrollableCardHeight)
                        .padding(vertical = Dimens.PaddingTiny, horizontal = Dimens.PaddingSmall)
                ) {
                    items(listOfStrings) { option ->
                        val isSelected = option == selectedOption

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Dimens.PaddingTiny)
                                .clip(MaterialTheme.shapes.small)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = MaterialTheme.shapes.small
                                )
                                .clickable(onClick = {
                                    onFilterStringClick(option)
                                }
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = option,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            vertical = Dimens.PaddingTiny,
                                            horizontal = Dimens.PaddingSmall
                                        )
                                        .weight(1f)
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Outlined.Done,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .padding(
                                                vertical = Dimens.PaddingTiny,
                                                horizontal = Dimens.PaddingSmall
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }else {
                LazyRow(
                    modifier = Modifier
                        .heightIn(max = Dimens.ScrollableColorCardHeight)
                        .padding(vertical = Dimens.PaddingSmall, horizontal = Dimens.PaddingTiny)
                ) {
                    items(listOfStrings) { option ->
                        val isSelected = option == selectedOption
                        ColorCircle(
                            colorHex = option.toLong(),
                            isSelected = isSelected,
                            onClick = { onFilterStringClick(option) },
                            modifier = Modifier.padding(horizontal = Dimens.PaddingSmall)
                        )
                    }
                }
            }


        }
    }
}