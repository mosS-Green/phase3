package com.phase3.tracker.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
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
    val isDownloading by viewModel.isDownloading.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val syncDone by viewModel.syncDone.collectAsStateWithLifecycle()
    val editMode by viewModel.editMode.collectAsStateWithLifecycle()
    val selectedStatusFilters by viewModel.selectedStatusFilters.collectAsStateWithLifecycle()
    val selectedCategories by viewModel.selectedCategories.collectAsStateWithLifecycle()
    val selectedContractor by viewModel.selectedContractor.collectAsStateWithLifecycle()

    // Global overlay: NavHost + persistent sync indicator
    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = Screen.Home.route) {

            composable(Screen.Home.route) {
                HomeScreen(
                    towers = towers,
                    isDownloading = isDownloading,
                    editMode = editMode,
                    selectedStatusFilters = selectedStatusFilters,
                    selectedCategories = selectedCategories,
                    selectedContractor = selectedContractor,
                    allContractors = viewModel.getAllContractors(),
                    allGroupNames = viewModel.getAllGroupNames(),
                    onNavigateToData = { navController.navigate(Screen.Data.route) },
                    onDownload = { viewModel.downloadFromGoogleSheets() },
                    onSaveToDownloads = { viewModel.saveExcelToDownloads() },
                    onToggleStatusFilter = { viewModel.toggleStatusFilter(it) },
                    onToggleCategoryFilter = { viewModel.toggleCategoryFilter(it) },
                    onSetContractorFilter = { viewModel.setContractorFilter(it) },
                    onToggleEditMode = { viewModel.toggleEditMode() },
                    onSetEditMode = { viewModel.setEditMode(it) },
                    onActivityClick = { towerIndex, activityIndex ->
                        navController.navigate(Screen.Activity.createRoute(towerIndex, activityIndex))
                    },
                    onAddActivity = { towerIndex, name, contractor, categories, groupName, usePercentage ->
                        viewModel.addActivity(towerIndex, name, contractor, categories, groupName, usePercentage)
                    },
                    onRenameActivity = { towerIndex, actIndex, newName, contractor, categories, groupName, usePercentage ->
                        viewModel.renameActivity(towerIndex, actIndex, newName, contractor, categories, groupName, usePercentage)
                    },
                    getFilteredActivities = { viewModel.getFilteredActivities(it) }
                )
            }

            composable(Screen.Data.route) {
                DataScreen(
                    towers = towers,
                    isDownloading = isDownloading,
                    onTowerClick = { towerIndex ->
                        navController.navigate(Screen.Tower.createRoute(towerIndex))
                    },
                    onDownload = { viewModel.downloadFromGoogleSheets() },
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
                        editMode = editMode,
                        allGroupNames = viewModel.getAllGroupNames(),
                        onActivityClick = { activityIndex ->
                            navController.navigate(Screen.Activity.createRoute(towerIndex, activityIndex))
                        },
                        onAddActivity = { name, contractor, categories, groupName, usePercentage ->
                            viewModel.addActivity(towerIndex, name, contractor, categories, groupName, usePercentage)
                        },
                        onRenameActivity = { actIndex, newName, contractor, categories, groupName, usePercentage ->
                            viewModel.renameActivity(towerIndex, actIndex, newName, contractor, categories, groupName, usePercentage)
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
                        editMode = editMode,
                        onToggleFlat = { flatNumber ->
                            viewModel.toggleFlatStatus(towerIndex, activityIndex, flatNumber)
                        },
                        onToggleFloor = { floor ->
                            viewModel.toggleFloorStatus(towerIndex, activityIndex, floor)
                        },
                        onUpdatePercentage = { flatNumber, percentage ->
                            viewModel.updateFlatPercentage(towerIndex, activityIndex, flatNumber, percentage)
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }

        // ── Persistent global sync indicator ────────────────────────
        GlobalSyncIndicator(
            isSyncing = isSyncing,
            syncDone = syncDone,
            isDownloading = isDownloading,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 12.dp, end = 12.dp)
                .zIndex(100f)
        )
    }
}

@Composable
fun GlobalSyncIndicator(
    isSyncing: Boolean,
    syncDone: Boolean,
    isDownloading: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sync_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val showIndicator = isSyncing || syncDone || isDownloading

    AnimatedVisibility(
        visible = showIndicator,
        enter = fadeIn(tween(200)) + scaleIn(tween(200)),
        exit = fadeOut(tween(200)) + scaleOut(tween(200)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center
        ) {
            when {
                isDownloading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                isSyncing -> {
                    Icon(
                        Icons.Default.Sync,
                        contentDescription = "Syncing",
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(rotation),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                syncDone -> {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Synced",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
