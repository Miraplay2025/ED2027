package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.EditorScreen
import com.example.ui.viewmodel.DashboardViewModel
import com.example.ui.viewmodel.EditorViewModel

object Destinations {
    const val DASHBOARD = "dashboard"
    const val EDITOR = "editor/{projectId}"

    fun editorRoute(projectId: Long) = "editor/$projectId"
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String = Destinations.DASHBOARD
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Destinations.DASHBOARD) {
            val dashboardViewModel: DashboardViewModel = viewModel()
            DashboardScreen(
                viewModel = dashboardViewModel,
                onNavigateToProject = { projectId ->
                    navController.navigate(Destinations.editorRoute(projectId))
                }
            )
        }

        composable(
            route = Destinations.EDITOR,
            arguments = listOf(
                navArgument("projectId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getLong("projectId") ?: -1L
            val editorViewModel: EditorViewModel = viewModel()
            EditorScreen(
                projectId = projectId,
                viewModel = editorViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
