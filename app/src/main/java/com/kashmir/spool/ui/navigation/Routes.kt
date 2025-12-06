package com.kashmir.spool.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Routes : NavKey {
    @Serializable
    object Dashboard : Routes

    @Serializable
    object SpoolEntry : Routes

    @Serializable
    data class SpoolDetails(val id: Int) : Routes
}

