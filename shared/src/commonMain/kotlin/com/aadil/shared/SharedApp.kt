package com.aadil.shared

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.aadil.spool.core.data.repository.InventorySession
import com.aadil.spool.ui.navigation.MySpoolApp
import com.aadil.spool.ui.theme.SpoolTheme
import org.koin.compose.koinInject

@Composable
fun SharedApp(initialSpoolId: Int? = null) {
    val session = koinInject<InventorySession>()
    val scope by session.scope.collectAsStateWithLifecycle()
    SpoolTheme {
        val active = scope
        if (active == null) {
            Surface(modifier = Modifier.fillMaxSize()) {
                Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        } else {
            // Navigation3 supplies a saved-state-aware ViewModelStore per inventory session.
            // Replacing it clears old details, filters, drafts and navigation on account changes.
            NavDisplay(
                backStack = listOf("${active.ownerId}:${active.revision}"),
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                popTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                entryProvider = { key -> NavEntry(key) { MySpoolApp(initialSpoolId = initialSpoolId) } },
            )
        }
    }
}
