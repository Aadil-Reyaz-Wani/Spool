package com.aadil.spool.ui.screens.settings

import android.R.attr.phoneNumber
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Whatsapp
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Whatsapp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aadil.spool.R
import com.aadil.spool.core.model.FAQ
import com.aadil.spool.core.model.SpoolLists
import com.aadil.spool.ui.common.SpoolAppBar
import com.aadil.spool.ui.theme.Dimens
import com.aadil.spool.ui.theme.SpoolTheme
import androidx.core.net.toUri

@Composable
fun HelpScreen(
    navigateUp: () -> Unit,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val supportEmail = stringResource(R.string.support_email)
    val supportPhone = stringResource(R.string.support_whatsapp)

    Scaffold(
        topBar = {
            SpoolAppBar(
                title = stringResource(R.string.topbar_label),
                canNavigateBack = true,
                navigateUp = navigateUp
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(paddingValues)
                .padding(
                    horizontal = Dimens.PaddingLarge,
                    vertical = Dimens.PaddingMedium
                ),
            verticalArrangement = Arrangement.spacedBy(Dimens.PaddingLarge)
        ) {

            // 1. Support Card Section
            SupportCard(
                onEmailClick = { sendSupportEmail(context, supportEmail) },
                onWhatsappClick = {sendWhatsappMessage(context, supportPhone)}
            )

            // 2. FAQ Section Header
            Text(
                text = stringResource(R.string.faq_label),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = Dimens.PaddingSmall)
            )

            // 3. FAQ List
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium)
            ) {
                SpoolLists.faqItem.forEach { faq ->
                    FaqItem(
                        question = faq.question, answer = faq.answer
                    )
                }
            }
        }
    }
}

@Composable
fun SupportCard(onEmailClick: () -> Unit, onWhatsappClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(Dimens.CornerRadius)
    ) {
        Column(
            modifier = Modifier.padding(Dimens.PaddingLarge),
            verticalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.PaddingTiny)
            ) {
                Text(
                    text = stringResource(R.string.need_assistance_header),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.need_assistance_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall)
            ) {
                OutlinedButton(
                    onClick = onEmailClick,
                    modifier = Modifier
                        .weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Email,
                        contentDescription = stringResource(R.string.email_support_button),
                        modifier = Modifier
                            .size(Dimens.IconSmall)
                            .padding(end = Dimens.PaddingTiny)
                    )
                    Text(text = stringResource(R.string.email_support_button))
                }

                // WhatsApp Button
                Button(
                    onClick = onWhatsappClick,
                    modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF25D366)
                )
                ) {
                    Icon(
                        imageVector = Icons.Default.Whatsapp,
                        contentDescription = stringResource(R.string.whatsapp_support_button),
                        modifier = Modifier
                            .size(Dimens.IconSmall)
                            .padding(end = Dimens.PaddingTiny)
                    )
                    Text(text = stringResource(R.string.whatsapp_support_button))
                }
            }

        }
    }
}

@Composable
fun FaqItem(question: String, answer: String) {
    var isExpanded by remember { mutableStateOf(false) }

    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "arrow_rotation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.CornerRadius))
            .clickable { isExpanded = !isExpanded },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(Dimens.CornerRadius)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Question Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.PaddingMedium),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = question,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Collapse And Expand",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = Dimens.PaddingSmall)
                        .rotate(rotationAngle)
                )
            }

            // Animated Answer Section
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(animationSpec = tween(200)) + expandVertically(animationSpec = tween(200)),
                exit = fadeOut(animationSpec = tween(200)) + shrinkVertically(animationSpec = tween(200))
            ) {
                Column {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = Dimens.PaddingMedium),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = answer,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Dimens.PaddingMedium),
                    )
                }
            }
        }
    }
}


// Intent Helper Functions
private fun sendSupportEmail(context: Context, email: String) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = "mailto:$email".toUri()
        putExtra(Intent.EXTRA_SUBJECT, "Spool App Support Request")
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(
            context,
            "No email app installed.",
            Toast.LENGTH_SHORT
        ).show()
    }
}

private fun sendWhatsappMessage(context: Context, phoneNumber: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = "https://api.whatsapp.com/send?phone=$phoneNumber".toUri()
        setPackage("com.whatsapp")
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(
            context,
            "WhatsApp is not installed on this device.",
            Toast.LENGTH_SHORT
        ).show()
    }
}


@Preview(showBackground = true, backgroundColor = 0xFFFFF8F5)
@Composable
fun HelpScreenPreview() {
    SpoolTheme {
        HelpScreen(navigateUp = {})
    }
}