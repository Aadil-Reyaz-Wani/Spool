package com.aadil.spool.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Routes : NavKey {

    @Serializable
    object Splash : Routes

    @Serializable
    object Dashboard : Routes

    @Serializable
    data class SpoolEntry(val id: Int) : Routes

    @Serializable
    data class SpoolDetails(val id: Int) : Routes
}

