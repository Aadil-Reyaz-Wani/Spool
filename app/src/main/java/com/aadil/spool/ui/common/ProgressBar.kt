package com.aadil.spool.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.aadil.spool.ui.components.SpoolProgressBar
import com.aadil.spool.ui.theme.BrandOrange
import com.aadil.spool.ui.theme.Dimens

@Composable
fun WeightProgressBar(
    totalWeight: String,
    currentWeight: String,
    modifier: Modifier = Modifier
) {
    val percentage = ((currentWeight.toDouble() / totalWeight.toDouble()) * 100)

    Column(
        modifier = modifier
    ) {
        Spacer(modifier = Modifier.height(Dimens.gapHeight))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = "Remaining",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${currentWeight}g",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = if (percentage > 40) {
                    MaterialTheme.colorScheme.onSurface
                } else if (percentage < 20) {
                    MaterialTheme.colorScheme.error
                } else {
                    BrandOrange
                },
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)

            )
        }
        Spacer(modifier = Modifier.height(Dimens.gapHeight))

        SpoolProgressBar(
            percentage = percentage,
            currentWeight = currentWeight,
            totalWeight = totalWeight
        )
        Text(
            text = "of ${totalWeight}g",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth()

        )
    }
}