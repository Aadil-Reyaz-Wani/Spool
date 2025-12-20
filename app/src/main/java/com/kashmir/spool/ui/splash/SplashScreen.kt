package com.kashmir.spool.ui.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kashmir.spool.R
import com.kashmir.spool.ui.theme.BrandOrange
import com.kashmir.spool.ui.theme.Dimens

@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
    ) { paddingValues ->
        LogoCard(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        )
    }
}

@Composable
fun LogoCard(modifier: Modifier = Modifier) {

    Column(
        modifier = modifier,
    ) {

        Column(
            modifier = Modifier
                .weight(0.7f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
//                elevation = CardDefaults.cardElevation(Dimens.CardElevation),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                modifier = Modifier
                    .size(100.dp)
                    .shadow(
                        elevation = 15.dp,
                        ambientColor = BrandOrange,
                        spotColor = BrandOrange,
                        shape = MaterialTheme.shapes.medium
                    ),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.logo),
                        contentDescription = null,
                        modifier = Modifier
                            .size(70.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.app_name).uppercase(),
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                letterSpacing = 5.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.3f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            AppDetails(modifier = Modifier.padding(top = Dimens.PaddingMedium))
        }

    }
}

@Composable
fun AppDetails(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(bottom = Dimens.PaddingMedium),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            color = BrandOrange
        )
        Spacer(modifier = Modifier.height(Dimens.PaddingSmall))
        Text(
            text = stringResource(R.string.version_number),
            letterSpacing = Dimens.LetterSpacing,
            style = MaterialTheme.typography.bodySmall,
            color = BrandOrange.copy(alpha = 0.6f)
        )
    }
}


@Preview
@Composable
private fun SSP() {
    SplashScreen()
}

//@Preview
//@Composable
//private fun LogoCardPrev() {
//    LogoCard()
//}