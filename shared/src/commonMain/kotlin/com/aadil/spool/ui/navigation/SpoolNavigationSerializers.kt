package com.aadil.spool.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

internal val spoolNavigationSerializers = SerializersModule {
    polymorphic(baseClass = NavKey::class) {
        subclass(serializer = Routes.Splash.serializer())
        subclass(serializer = Routes.Dashboard.serializer())
        subclass(serializer = Routes.SpoolEntry.serializer())
        subclass(serializer = Routes.SpoolDetails.serializer())
        subclass(serializer = Routes.PrintHistory.serializer())
        subclass(serializer = Routes.Filter.serializer())
        subclass(serializer = Routes.About.serializer())
        subclass(serializer = Routes.Account.serializer())
        subclass(serializer = Routes.InventoryConflicts.serializer())
        subclass(serializer = Routes.InventoryRecovery.serializer())
        subclass(serializer = Routes.OpenSourceLicenses.serializer())
        subclass(serializer = Routes.Help.serializer())
        subclass(serializer = Routes.FitCheck.serializer())
    }
}
