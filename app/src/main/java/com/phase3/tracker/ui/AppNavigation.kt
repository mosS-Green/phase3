package com.phase3.tracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.phase3.tracker.ui.screens.ActivityScreen
import com.phase3.tracker.ui.screens.DataScreen
import com.phase3.tracker.ui.screens.HomeScreen
import com.phase3.tracker.ui.screens.TowerScreen
import com.phase3.tracker.viewmodel.MainViewModel

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Data : Screen("data")
    data object Tower : Screen("tower/{towerIndex}") {
        fun createRoute(towerIndex: Int) = "tower/$towerIndex"
    }
    data object Activity : Screen("activity/{towerIndex}/{activityIndex}") {
        fun createRoute(towerIndex: Int, activityIndex: Int) = "activity/$towerIndex/$activityIndex"
    }
}

@Composable
fun AppNavigation(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val towers by viewModel.towers.collectAsStateWithLifecycle()
    val isUploading by viewModel.isUploading.collectAsStateWithLifecycle()
    val isDownloading by viewModel.isDownloading.collectAsStateWithLifecycle()

    NavHost(navController = navController, startDestination = Screen.Home.route) {

        composable(Screen.Home.route) {
            HomeScreen(
                towers = towers,
                isDownloading = isDownloading,
                onNavigateToData = { navController.navigate(Screen.Data.route) },
                onDownload = { viewModel.downloadFromTelegram() },
                onActivityClick = { towerIndex, activityIndex ->
                    navController.navigate(Screen.Activity.createRoute(towerIndex, activityIndex))
                }
            )
        }

        composable(Screen.Data.route) {
            DataScreen(
                towers = towers,
                isUploading = isUploading,
                isDownloading = isDownloading,
                onTowerClick = { towerIndex ->
                    navController.navigate(Screen.Tower.createRoute(towerIndex))
                },
                onUpload = { viewModel.uploadToTelegram() },
                onDownload = { viewModel.downloadFromTelegram() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Tower.route,
            arguments = listOf(navArgument("towerIndex") { type = NavType.IntType })
        ) { backStackEntry ->
            val towerIndex = backStackEntry.arguments?.getInt("towerIndex") ?: 0
            val tower = towers.getOrNull(towerIndex)

            if (tower != null) {
                TowerScreen(
                    towerName = tower.name,
                    activities = tower.activities,
                    onActivityClick = { activityIndex ->
                        navController.navigate(Screen.Activity.createRoute(towerIndex, activityIndex))
                    },
                    onAddActivity = { name -> viewModel.addActivity(towerIndex, name) },
                    onRenameActivity = { actIndex, newName ->
                        viewModel.renameActivity(towerIndex, actIndex, newName)
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable(
            route = Screen.Activity.route,
            arguments = listOf(
                navArgument("towerIndex") { type = NavType.IntType },
                navArgument("activityIndex") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val towerIndex = backStackEntry.arguments?.getInt("towerIndex") ?: 0
            val activityIndex = backStackEntry.arguments?.getInt("activityIndex") ?: 0
            val tower = towers.getOrNull(towerIndex)
            val activity = tower?.activities?.getOrNull(activityIndex)

            if (activity != null) {
                ActivityScreen(
                    activityName = activity.name,
                    activity = activity,
                    onToggleFlat = { flatNumber ->
                        viewModel.toggleFlatStatus(towerIndex, activityIndex, flatNumber)
                    },
                    onToggleFloor = { floor ->
                        viewModel.toggleFloorStatus(towerIndex, activityIndex, floor)
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
