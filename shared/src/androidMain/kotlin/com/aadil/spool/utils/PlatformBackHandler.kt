package com.aadil.spool.utils

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun PlatformBackHandler(enabled: Boolean) {
    val activity = LocalContext.current as? Activity
    BackHandler(enabled = enabled) {
        activity?.moveTaskToBack(true)
    }
}
