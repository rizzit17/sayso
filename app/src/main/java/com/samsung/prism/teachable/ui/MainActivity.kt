package com.samsung.prism.teachable.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.material.icons.filled.Settings
import com.samsung.prism.teachable.ui.screens.HomeScreen
import com.samsung.prism.teachable.ui.screens.LearnedFlowsScreen
import com.samsung.prism.teachable.ui.screens.RunHistoryScreen
import com.samsung.prism.teachable.ui.screens.SettingsScreen
import com.samsung.prism.teachable.ui.screens.WorkflowDetailScreen
import com.samsung.prism.teachable.ui.theme.PrismTheme
import com.samsung.prism.teachable.ui.theme.SaysoOnPrimaryContainer
import com.samsung.prism.teachable.ui.theme.SaysoOnSurface
import com.samsung.prism.teachable.ui.theme.SaysoOnSurfaceVariant
import com.samsung.prism.teachable.ui.theme.SaysoPrimaryContainer
import com.samsung.prism.teachable.ui.theme.SaysoSurface
import com.samsung.prism.teachable.ui.theme.SaysoSurfaceContainer

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Home : Screen("home", "Home", Icons.Default.Home)
    data object Workflows : Screen("workflows", "Flows", Icons.Default.AutoMode)
    data object History : Screen("history", "History", Icons.Default.History)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PrismTheme {
                MainAppContent()
            }
        }
    }
}

@Composable
fun MainAppContent(viewModel: MainViewModel = viewModel()) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomNavItems = listOf(
        Screen.Home,
        Screen.Workflows,
        Screen.History,
        Screen.Settings
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(SaysoSurface),
        containerColor = SaysoSurface,
        bottomBar = {
            // Only show bottom bar on top-level screens
            if (currentRoute in bottomNavItems.map { it.route }) {
                NavigationBar(
                    containerColor = SaysoSurfaceContainer,
                    contentColor = SaysoOnSurface
                ) {
                    bottomNavItems.forEach { screen ->
                        val isSelected = currentRoute == screen.route
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(imageVector = screen.icon, contentDescription = screen.title) },
                            label = {
                                Text(
                                    text = screen.title,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = SaysoOnPrimaryContainer,
                                selectedTextColor = SaysoOnSurface,
                                indicatorColor = SaysoPrimaryContainer,
                                unselectedIconColor = SaysoOnSurfaceVariant,
                                unselectedTextColor = SaysoOnSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToWorkflowDetail = { id ->
                        navController.navigate("workflow_detail/$id")
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route)
                    }
                )
            }

            composable(Screen.Workflows.route) {
                LearnedFlowsScreen(
                    viewModel = viewModel,
                    onNavigateToWorkflowDetail = { id ->
                        navController.navigate("workflow_detail/$id")
                    }
                )
            }

            composable(Screen.History.route) {
                RunHistoryScreen(viewModel = viewModel)
            }

            composable(Screen.Settings.route) {
                SettingsScreen(viewModel = viewModel)
            }

            composable(
                route = "workflow_detail/{workflowId}",
                arguments = listOf(navArgument("workflowId") { type = NavType.StringType })
            ) { backStackEntry ->
                val workflowId = backStackEntry.arguments?.getString("workflowId") ?: ""
                WorkflowDetailScreen(
                    workflowId = workflowId,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
