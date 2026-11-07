package com.fabapps.mediaforge.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fabapps.mediaforge.navigation.MediaForgeNavHost
import com.fabapps.mediaforge.navigation.TopLevelDestination
import com.fabapps.mediaforge.ui.theme.MediaForgeTheme

/** Estructura de la app: barra superior con el título del destino, contenido y barra inferior. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaForgeApp(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = backStackEntry?.destination.toTopLevel()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(current.titleRes)) },
            )
        },
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = destination == current,
                        onClick = { navController.navigateToTopLevel(destination) },
                        icon = { Icon(painterResource(destination.iconRes), contentDescription = null) },
                        label = { Text(stringResource(destination.titleRes)) },
                    )
                }
            }
        },
    ) { padding ->
        MediaForgeNavHost(navController = navController, modifier = Modifier.padding(padding))
    }
}

/** Destino de la barra al que pertenece la pantalla actual; el inicial mientras no hay ninguna. */
private fun NavDestination?.toTopLevel(): TopLevelDestination = TopLevelDestination.entries.firstOrNull { top ->
    this?.hierarchy?.any { it.hasRoute(top.route::class) } == true
} ?: TopLevelDestination.START

/** Cambia de pestaña conservando el estado de cada una y sin apilar copias. */
private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Preview
@Composable
private fun MediaForgeAppPreview() {
    MediaForgeTheme { MediaForgeApp() }
}
