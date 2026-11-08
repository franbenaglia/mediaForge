package com.fabapps.mediaforge.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.fabapps.mediaforge.ui.common.NotImplementedScreen
import com.fabapps.mediaforge.ui.converter.ConverterScreen
import com.fabapps.mediaforge.ui.downloads.DownloadsScreen
import com.fabapps.mediaforge.ui.history.HistoryScreen
import com.fabapps.mediaforge.ui.home.HomeScreen

@Composable
fun MediaForgeNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.START.route,
        modifier = modifier,
    ) {
        // Cada pantalla reemplaza su NotImplementedScreen en la etapa que la implementa.
        composable<HomeRoute> { HomeScreen() }
        composable<DownloadsRoute> { DownloadsScreen() }
        composable<ConverterRoute> { ConverterScreen() }
        composable<HistoryRoute> {
            HistoryScreen(onOpenHome = { navController.navigateToTopLevel(TopLevelDestination.HOME) })
        }
        composable<SettingsRoute> { NotImplementedScreen() }
    }
}

/** Cambia de pestaña conservando el estado de cada una y sin apilar copias. */
fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
