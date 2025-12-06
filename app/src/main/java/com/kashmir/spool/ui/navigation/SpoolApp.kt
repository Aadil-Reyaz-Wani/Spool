package com.kashmir.spool.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.kashmir.spool.ui.screens.AppViewModelProvider
import com.kashmir.spool.ui.screens.dashboard.DashboardScreen
import com.kashmir.spool.ui.screens.dashboard.DashboardViewModel
import com.kashmir.spool.ui.screens.entry.SpoolEntryScreen

@Composable
fun MySpoolApp(modifier: Modifier = Modifier) {
    val dashboardViewModel: DashboardViewModel = viewModel(factory = AppViewModelProvider.Factory)




    val backStack = rememberNavBackStack(Routes.Dashboard)
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            // Dashboard Screen Entry
            entry<Routes.Dashboard> {
                DashboardScreen(
                    onFabClick = {
                        backStack.add(Routes.SpoolEntry)
                    }
                )
            }

            // Entry Screen Entry
            entry<Routes.SpoolEntry>{
                SpoolEntryScreen(
                    onNavigateUp = {
                        backStack.removeLastOrNull()
                    }
                )
            }

        }
    )
}