package com.fabapps.mediaforge.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.fabapps.mediaforge.R
import kotlinx.serialization.Serializable

/** Rutas tipadas de Navigation Compose. Una ruta nueva con argumentos pasa a ser `data class`. */
@Serializable
data object HomeRoute

@Serializable
data object DownloadsRoute

@Serializable
data object ConverterRoute

@Serializable
data object HistoryRoute

@Serializable
data object SettingsRoute

/** Destinos de la barra inferior, en el orden en que se muestran. */
enum class TopLevelDestination(val route: Any, @StringRes val titleRes: Int, @DrawableRes val iconRes: Int) {
    HOME(HomeRoute, R.string.nav_home, R.drawable.ic_nav_home),
    DOWNLOADS(DownloadsRoute, R.string.nav_downloads, R.drawable.ic_nav_downloads),
    CONVERTER(ConverterRoute, R.string.nav_converter, R.drawable.ic_nav_converter),
    HISTORY(HistoryRoute, R.string.nav_history, R.drawable.ic_nav_history),
    SETTINGS(SettingsRoute, R.string.nav_settings, R.drawable.ic_nav_settings),
    ;

    companion object {
        val START = HOME
    }
}
