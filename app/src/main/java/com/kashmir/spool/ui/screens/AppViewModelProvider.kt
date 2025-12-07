package com.kashmir.spool.ui.screens

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kashmir.spool.SpoolApplication
import com.kashmir.spool.ui.screens.dashboard.DashboardViewModel
import com.kashmir.spool.ui.screens.entry.SpoolEntryViewModel


object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            DashboardViewModel(
                toSpoolApplication().container.spoolRepository
            )
        }

        initializer {
            SpoolEntryViewModel(
                toSpoolApplication().container.spoolRepository
            )
        }
    }
}

fun CreationExtras.toSpoolApplication() : SpoolApplication =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as SpoolApplication)