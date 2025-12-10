package com.kashmir.spool.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kashmir.spool.ui.theme.Dimens

@Composable
fun WeightProgressBar(
    totalWeight: String,
    colorHex: Long,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
    ) {
        Spacer(modifier = Modifier.height(Dimens.gapHeight))
        LinearProgressIndicator(
            progress = { 20f / 100 },
            modifier = Modifier
                .fillMaxWidth()
                .size(Dimens.ProgressBarHeight),
            color = Color(colorHex),
            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
        )
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "750g",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${totalWeight}g",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)

            )
        }
    }

}
