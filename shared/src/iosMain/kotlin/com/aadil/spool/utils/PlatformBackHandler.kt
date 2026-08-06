package com.aadil.spool.utils

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean) {
    // No-op on iOS as there is no system hardware back button
}
