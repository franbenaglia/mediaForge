package com.fabapps.mediaforge.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.fabapps.mediaforge.ui.common.NotImplementedScreen

@Composable
fun MediaForgeNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.START.route,
        modifier = modifier,
    ) {
        // Cada pantalla reemplaza su NotImplementedScreen en la etapa que la implementa.
        composable<HomeRoute> { NotImplementedScreen() }
        composable<DownloadsRoute> { NotImplementedScreen() }
        composable<ConverterRoute> { NotImplementedScreen() }
        composable<HistoryRoute> { NotImplementedScreen() }
        composable<SettingsRoute> { NotImplementedScreen() }
    }
}
