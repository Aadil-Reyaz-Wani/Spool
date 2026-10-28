package com.aadil.spool

import androidx.navigation3.runtime.NavKey
import com.aadil.spool.ui.navigation.Routes
import com.aadil.spool.ui.navigation.spoolNavigationSerializers
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class NavigationSerializationTest {
    @Test fun actualNavigationModuleRoundTripsBackStackIncludingConflictScreen() {
        val routes: List<NavKey> = listOf(Routes.Splash, Routes.Dashboard, Routes.SpoolEntry(7),
            Routes.SpoolDetails(7), Routes.PrintHistory(7), Routes.Filter, Routes.About, Routes.Account,
            Routes.InventoryConflicts, Routes.OpenSourceLicenses, Routes.Help, Routes.FitCheck(12.5))
        val serializer = ListSerializer(PolymorphicSerializer(NavKey::class))
        val json = Json { serializersModule = spoolNavigationSerializers }
        assertEquals(routes, json.decodeFromString(serializer, json.encodeToString(serializer, routes)))
    }
}
