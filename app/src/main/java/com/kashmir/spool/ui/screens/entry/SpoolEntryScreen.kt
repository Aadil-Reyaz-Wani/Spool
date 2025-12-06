package com.kashmir.spool.ui.screens.entry

import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kashmir.spool.ui.common.SpoolAppBar
import com.kashmir.spool.R

@Composable
fun SpoolEntryScreen(
    onNavigateUp:() -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            SpoolAppBar(
                title = stringResource(R.string.title_new_spool),
                canNavigateBack = true,
                navigateUp = onNavigateUp,
                modifier = modifier
            )
        }
    ) { }
}