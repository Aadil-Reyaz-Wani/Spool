package com.aadil.spool.ui.screens.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aadil.spool.R
import com.aadil.spool.data.entity.UsageLog
import com.aadil.spool.ui.common.SpoolAppBar
import com.aadil.spool.ui.components.SpoolTag
import com.aadil.spool.ui.theme.Dimens

@Composable
fun PrintHistoryScreen(
    navigateUp: () -> Unit,
    usageLog: List<UsageLog>,
    modifier: Modifier = Modifier
) {

    Scaffold(
        modifier = modifier,
        topBar = {
            SpoolAppBar(
                title = stringResource(R.string.spool_history),
                navigateUp = navigateUp,
                canNavigateBack = true
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier.padding(paddingValues)
        ) {
            items(usageLog) { log ->
                PrintItemViewCard(
                    title = log.title,
                    date = log.timestamp,
                    usedGrams = log.gramsUsed.toString(),
                    price = "0.25",
                    status = log.isFailure,
                    modifier = Modifier.padding(horizontal = Dimens.PaddingMedium, vertical = Dimens.PaddingSmall)
                )
            }
        }
    }
}

@Composable
fun PrintItemViewCard(
    title: String,
    date: Long,
    usedGrams: String,
    price: String,
    status: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(Dimens.CardElevation / 2),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.PaddingMedium)
        ) {

            SpoolTag(
                text = if (!status) "Success" else "Failed",
                surfaceColor = if (!status) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
                textColor = if (!status) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }

            )
            Spacer(modifier = Modifier.height(Dimens.HeightOrWidth))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = date.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(Dimens.HeightOrWidth))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Used:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(Dimens.HeightOrWidth))
                Text(
                    text = "${usedGrams}g",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(Dimens.HeightOrWidth))
                Text(
                    text = "($$price)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(Dimens.HeightOrWidth))
            HorizontalDivider(modifier = Modifier.height(Dimens.PaddingMedium))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    border = null,
                    content = {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = null,
                            modifier = Modifier.padding(Dimens.PaddingTiny)
                        )
                    }
                )
                Spacer(modifier = Modifier.padding(Dimens.PaddingSmall))
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.error,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    border = null,
                    content = {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.padding(Dimens.PaddingTiny)
                        )
                    }
                )

            }

        }

    }
}


//@Preview
//@Composable
//private fun PrintHistoryPrev() {
//    PrintHistoryScreen(
//        navigateUp = {}
//    )
//}

@Preview
@Composable
private fun PrintItemViewCardPrev() {
    PrintItemViewCard(
        title = "Iron Man Helmet",
        date = 231243521,
        usedGrams = "100",
        price = "$0.25",
        status = true
    )
}