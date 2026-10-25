package com.aadil.shared

import androidx.compose.runtime.Composable
import com.aadil.spool.ui.navigation.MySpoolApp
import com.aadil.spool.ui.theme.SpoolTheme

@Composable
fun SharedApp(initialSpoolId: Int? = null) {
    SpoolTheme {
        MySpoolApp(initialSpoolId = initialSpoolId)
    }
}
