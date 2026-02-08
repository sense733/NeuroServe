package com.neuroserve.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.res.stringResource
import com.neuroserve.R

sealed class Screen(val route: String, val titleResId: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    data object Dashboard : Screen("dashboard", R.string.nav_dashboard, Icons.Default.Dashboard)
    data object Chat : Screen("chat", R.string.nav_playground, Icons.Default.SportsEsports)
    data object ModelHub : Screen("model_hub", R.string.nav_models, Icons.Default.Hub)
}

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val items = listOf(
        Screen.Dashboard,
        Screen.Chat,
        Screen.ModelHub
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                
                items.forEach { screen ->
                    val title = stringResource(screen.titleResId)
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = title) },
                        label = { Text(title) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    viewModel = viewModel,
                    onSettingsClick = { navController.navigate("settings") }
                )
            }
            composable(Screen.Chat.route) {
                ChatScreen(viewModel)
            }
            composable(Screen.ModelHub.route) {
                ModelHubScreen(viewModel)
            }
            composable("settings") {
                com.neuroserve.ui.settings.SettingsScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}


