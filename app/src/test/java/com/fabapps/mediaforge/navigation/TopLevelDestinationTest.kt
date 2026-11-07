package com.fabapps.mediaforge.navigation

import com.fabapps.mediaforge.R
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.junit.Assert.assertEquals
import org.junit.Test

class TopLevelDestinationTest {

    @Test
    fun barHasTheFiveSectionsInOrder() {
        assertEquals(
            listOf(HomeRoute, DownloadsRoute, ConverterRoute, HistoryRoute, SettingsRoute),
            TopLevelDestination.entries.map { it.route },
        )
    }

    @Test
    fun startDestinationIsHome() {
        assertEquals(HomeRoute, TopLevelDestination.START.route)
    }

    @Test
    fun routesAreUnique() {
        val classes = TopLevelDestination.entries.map { it.route::class }
        assertEquals(classes.size, classes.toSet().size)
    }

    @Test
    fun titlesAndIconsAreDistinctResources() {
        val titles = TopLevelDestination.entries.map { it.titleRes }
        val icons = TopLevelDestination.entries.map { it.iconRes }
        assertEquals(titles.size, titles.toSet().size)
        assertEquals(icons.size, icons.toSet().size)
        assertEquals(R.string.nav_home, TopLevelDestination.HOME.titleRes)
    }

    /** Navigation Compose necesita que cada ruta sea serializable; si falta @Serializable, falla acá. */
    @OptIn(InternalSerializationApi::class)
    @Test
    fun everyRouteIsSerializable() {
        TopLevelDestination.entries.forEach { destination ->
            val serializer = destination.route::class.serializer()

            @Suppress("UNCHECKED_CAST")
            val encoded = Json.encodeToString(serializer as kotlinx.serialization.KSerializer<Any>, destination.route)
            assertEquals(destination.route, Json.decodeFromString(serializer, encoded))
        }
    }
}
