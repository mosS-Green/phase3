package com.phase3.tracker.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.phase3.tracker.model.PHDDoorConfig
import com.phase3.tracker.ui.screens.*
import com.phase3.tracker.viewmodel.DWViewModel
import com.phase3.tracker.viewmodel.MainViewModel
import com.phase3.tracker.viewmodel.PHDViewModel
import com.phase3.tracker.viewmodel.QSIViewModel

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Data : Screen("data")
    data object Tower : Screen("tower/{towerIndex}") {
        fun createRoute(towerIndex: Int) = "tower/$towerIndex"
    }
    data object Activity : Screen("activity/{towerIndex}/{activityIndex}") {
        fun createRoute(towerIndex: Int, activityIndex: Int) = "activity/$towerIndex/$activityIndex"
    }
    // DW screens
    data object DWHome : Screen("dw_home")
    data object DWColumn : Screen("dw_column/{towerIndex}") {
        fun createRoute(towerIndex: Int) = "dw_column/$towerIndex"
    }
    data object DWFlat : Screen("dw_flat/{towerIndex}/{flatNumber}") {
        fun createRoute(towerIndex: Int, flatNumber: Int) = "dw_flat/$towerIndex/$flatNumber"
    }
    // Unit Type screen
    data object UnitType : Screen("unit_type/{towerIndex}/{unitDigit}") {
        fun createRoute(towerIndex: Int, unitDigit: Int) = "unit_type/$towerIndex/$unitDigit"
    }
    // PHD screens
    data object PHDHome : Screen("phd_home")
    data object PHDTower : Screen("phd_tower/{towerIndex}") {
        fun createRoute(towerIndex: Int) = "phd_tower/$towerIndex"
    }
    data object PHDFlat : Screen("phd_flat/{towerIndex}/{flatNumber}") {
        fun createRoute(towerIndex: Int, flatNumber: Int) = "phd_flat/$towerIndex/$flatNumber"
    }
    data object PHDUnitType : Screen("phd_unit_type/{towerIndex}/{unitDigit}") {
        fun createRoute(towerIndex: Int, unitDigit: Int) = "phd_unit_type/$towerIndex/$unitDigit"
    }
    // QSI screens
    data object QSIDashboard : Screen("qsi_dashboard")
    data object QSIDetail : Screen("qsi_detail/{detailType}") {
        fun createRoute(detailType: String) = "qsi_detail/$detailType"
    }
}

@Composable
fun AppNavigation(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val towers by viewModel.towers.collectAsStateWithLifecycle()
    val isDownloading by viewModel.isDownloading.collectAsStateWithLifecycle()
    val isMainImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val syncDone by viewModel.syncDone.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val editMode by viewModel.editMode.collectAsStateWithLifecycle()
    val selectedStatusFilters by viewModel.selectedStatusFilters.collectAsStateWithLifecycle()
    val selectedCategories by viewModel.selectedCategories.collectAsStateWithLifecycle()
    val selectedContractor by viewModel.selectedContractor.collectAsStateWithLifecycle()

    val dwViewModel: DWViewModel = viewModel()
    val dwTypes by dwViewModel.dwTypes.collectAsStateWithLifecycle()
    val dwRooms by dwViewModel.rooms.collectAsStateWithLifecycle()
    val dwIsLoading by dwViewModel.isLoading.collectAsStateWithLifecycle()
    val dwIsSyncing by dwViewModel.isSyncing.collectAsStateWithLifecycle()
    val dwIsImporting by dwViewModel.isImporting.collectAsStateWithLifecycle()
    val allTowerRooms by dwViewModel.allTowerRooms.collectAsStateWithLifecycle()

    val phdViewModel: PHDViewModel = viewModel()
    val phdStatuses by phdViewModel.phdStatuses.collectAsStateWithLifecycle()
    val phdIsLoading by phdViewModel.isLoading.collectAsStateWithLifecycle()
    val phdIsSyncing by phdViewModel.isSyncing.collectAsStateWithLifecycle()

    val qsiViewModel: QSIViewModel = viewModel()

    val context = LocalContext.current

    val dwStatusMessage by dwViewModel.statusMessage.collectAsStateWithLifecycle()
    val phdStatusMessage by phdViewModel.statusMessage.collectAsStateWithLifecycle()
    val mainStatusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    // Snackbar for main status messages
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(mainStatusMessage) {
        if (!mainStatusMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(mainStatusMessage!!)
            viewModel.clearStatusMessage()
        }
    }
    LaunchedEffect(phdStatusMessage) {
        if (!phdStatusMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(phdStatusMessage!!)
            phdViewModel.clearStatusMessage()
        }
    }

    // File picker for DW Excel import
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                context.contentResolver.openInputStream(uri)?.let { stream ->
                    dwViewModel.importFromExcel(stream, towers)
                }
            }
        }
    }

    // File picker for Activities Excel import
    val activityImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                context.contentResolver.openInputStream(uri)?.let { stream ->
                    viewModel.importActivitiesFromExcel(stream)
                }
            }
        }
    }

    // Is any import in progress?
    val isAnyImporting = isMainImporting || dwIsImporting

    // Global overlay: NavHost + persistent sync indicator + loading overlay
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
                    allCategories = viewModel.getAllCategories(),
                    onNavigateToData = { navController.navigate(Screen.Data.route) },
                    onDownload = { viewModel.refreshFromSupabase() },
                    onSaveToDownloads = { viewModel.saveExcelToDownloads() },
                    onImportActivities = {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        }
                        activityImportLauncher.launch(intent)
                    },
                    onToggleStatusFilter = { viewModel.toggleStatusFilter(it) },
                    onToggleCategoryFilter = { viewModel.toggleCategoryFilter(it) },
                    onSetContractorFilter = { viewModel.setContractorFilter(it) },
                    onToggleEditMode = { viewModel.toggleEditMode() },
                    onSetEditMode = { viewModel.setEditMode(it) },
                    onActivityClick = { towerIndex, activityIndex ->
                        navController.navigate(Screen.Activity.createRoute(towerIndex, activityIndex))
                    },
                    onAddActivity = { towerIndex, name, contractor, categories, groupName, usePercentage, weightage ->
                        viewModel.addActivity(towerIndex, name, contractor, categories, groupName, usePercentage, weightage)
                    },
                    onRenameActivity = { towerIndex, actIndex, newName, contractor, categories, groupName, usePercentage, weightage ->
                        viewModel.renameActivity(towerIndex, actIndex, newName, contractor, categories, groupName, usePercentage, weightage)
                    },
                    onDeleteActivity = { towerIndex, activityIndex ->
                        viewModel.deleteActivity(towerIndex, activityIndex)
                    },
                    getFilteredActivities = { viewModel.getFilteredActivities(it) },
                    onNavigateToQSI = { navController.navigate(Screen.QSIDashboard.route) }
                )
            }

            composable(Screen.Data.route) {
                // Activity sync check dialog — shows after download completes
                var showSyncCheck by remember { mutableStateOf(false) }
                var wasDownloading by remember { mutableStateOf(false) }

                // Detect when download finishes (was true, now false)
                LaunchedEffect(isDownloading) {
                    if (wasDownloading && !isDownloading) {
                        showSyncCheck = true
                    }
                    wasDownloading = isDownloading
                }

                if (showSyncCheck) {
                    ActivitySyncCheckDialog(
                        viewModel = viewModel,
                        onDismiss = { showSyncCheck = false }
                    )
                }

                DataScreen(
                    towers = towers,
                    isDownloading = isDownloading,
                    onTowerClick = { towerIndex ->
                        navController.navigate(Screen.Tower.createRoute(towerIndex))
                    },
                    onDownload = {
                        viewModel.refreshFromSupabase()
                    },
                    onDWClick = { navController.navigate(Screen.DWHome.route) },
                    onPHDClick = { navController.navigate(Screen.PHDHome.route) },
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
                        allCategories = viewModel.getAllCategories(),
                        onActivityClick = { activityIndex ->
                            navController.navigate(Screen.Activity.createRoute(towerIndex, activityIndex))
                        },
                        onAddActivity = { name, contractor, categories, groupName, usePercentage, weightage ->
                            viewModel.addActivity(towerIndex, name, contractor, categories, groupName, usePercentage, weightage)
                        },
                        onRenameActivity = { actIndex, newName, contractor, categories, groupName, usePercentage, weightage ->
                            viewModel.renameActivity(towerIndex, actIndex, newName, contractor, categories, groupName, usePercentage, weightage)
                        },
                        onDeleteActivity = { actIndex ->
                            viewModel.deleteActivity(towerIndex, actIndex)
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

            // ── DW Screens ──────────────────────────────────────

            composable(Screen.DWHome.route) {
                DWHomeScreen(
                    towers = towers,
                    dwTypes = dwTypes,
                    onTowerClick = { towerIndex ->
                        navController.navigate(Screen.DWColumn.createRoute(towerIndex))
                    },
                    onUnitTypeClick = { towerIndex, unitDigit ->
                        navController.navigate(Screen.UnitType.createRoute(towerIndex, unitDigit))
                    },
                    onAddType = { name, kind, h, b -> dwViewModel.addType(name, kind, h, b) },
                    onUpdateType = { id, name, kind, h, b -> dwViewModel.updateType(id, name, kind, h, b) },
                    onDeleteType = { id -> dwViewModel.deleteType(id) },
                    onExportExcel = { dwViewModel.exportToExcel(towers) },
                    onImportExcel = {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        }
                        importLauncher.launch(intent)
                    },
                    onSyncToActivities = { viewModel.syncDWToActivities(dwViewModel) },
                    statusMessage = dwStatusMessage,
                    onStatusDismiss = { dwViewModel.clearStatusMessage() },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.DWColumn.route,
                arguments = listOf(navArgument("towerIndex") { type = NavType.IntType })
            ) { backStackEntry ->
                val towerIndex = backStackEntry.arguments?.getInt("towerIndex") ?: 0
                val tower = towers.getOrNull(towerIndex)

                if (tower != null) {
                    DWColumnScreen(
                        towerName = tower.name,
                        towerId = tower.id,
                        rooms = dwRooms,
                        dwTypes = dwTypes,
                        isLoading = dwIsLoading,
                        onLoadRooms = { colType -> dwViewModel.loadRooms(tower.id, colType) },
                        onAddRoom = { colType, name, typeIds ->
                            dwViewModel.addRoom(tower.id, colType, name, typeIds)
                        },
                        onUpdateRoom = { roomId, colType, name, typeIds ->
                            dwViewModel.updateRoom(roomId, tower.id, colType, name, typeIds)
                        },
                        onDeleteRoom = { roomId, colType ->
                            dwViewModel.deleteRoom(roomId, tower.id, colType)
                        },
                        onFlatClick = { flatNumber ->
                            navController.navigate(Screen.DWFlat.createRoute(towerIndex, flatNumber))
                        },
                        flatCompletion = { flatNumber -> dwViewModel.flatColumnCompletion(flatNumber) },
                        columnCompletion = { dwViewModel.columnCompletion() },
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            composable(
                route = Screen.DWFlat.route,
                arguments = listOf(
                    navArgument("towerIndex") { type = NavType.IntType },
                    navArgument("flatNumber") { type = NavType.IntType }
                )
            ) { backStackEntry ->
                val flatNumber = backStackEntry.arguments?.getInt("flatNumber") ?: 0

                DWFlatScreen(
                    flatNumber = flatNumber,
                    rooms = dwRooms,
                    onToggleStatus = { roomId, typeId ->
                        dwViewModel.toggleDWStatus(roomId, typeId, flatNumber)
                    },
                    flatCompletion = dwViewModel.flatColumnCompletion(flatNumber),
                    onBack = { navController.popBackStack() }
                )
            }

            // ── Unit Type Screen ────────────────────────────────

            composable(
                route = Screen.UnitType.route,
                arguments = listOf(
                    navArgument("towerIndex") { type = NavType.IntType },
                    navArgument("unitDigit") { type = NavType.IntType }
                )
            ) { backStackEntry ->
                val towerIndex = backStackEntry.arguments?.getInt("towerIndex") ?: 0
                val unitDigit = backStackEntry.arguments?.getInt("unitDigit") ?: 1
                val tower = towers.getOrNull(towerIndex)

                if (tower != null) {
                    LaunchedEffect(tower.id) {
                        dwViewModel.loadAllRoomsForTower(tower.id)
                    }

                    UnitTypeScreen(
                        towerName = tower.name,
                        unitDigit = unitDigit,
                        allTowerRooms = allTowerRooms,
                        isLoading = dwIsLoading,
                        onToggleStatus = { roomId, typeId, flatNumber ->
                            dwViewModel.toggleDWStatusInAllRooms(roomId, typeId, flatNumber)
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            // ── PHD Screens ────────────────────────────────────────

            composable(Screen.PHDHome.route) {
                PHDHomeScreen(
                    towers = towers,
                    onTowerClick = { towerIndex ->
                        navController.navigate(Screen.PHDTower.createRoute(towerIndex))
                    },
                    onUnitTypeClick = { towerIndex, unitDigit ->
                        navController.navigate(Screen.PHDUnitType.createRoute(towerIndex, unitDigit))
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.PHDTower.route,
                arguments = listOf(navArgument("towerIndex") { type = NavType.IntType })
            ) { backStackEntry ->
                val towerIndex = backStackEntry.arguments?.getInt("towerIndex") ?: 0
                val tower = towers.getOrNull(towerIndex)

                if (tower != null) {
                    LaunchedEffect(tower.id) {
                        phdViewModel.loadStatuses(tower.id)
                    }

                    PHDTowerScreen(
                        towerName = tower.name,
                        isLoading = phdIsLoading,
                        flatCompletion = { flatNumber ->
                            phdViewModel.flatCompletion(flatNumber, tower.sheetName)
                        },
                        onFlatClick = { flatNumber ->
                            navController.navigate(Screen.PHDFlat.createRoute(towerIndex, flatNumber))
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            composable(
                route = Screen.PHDFlat.route,
                arguments = listOf(
                    navArgument("towerIndex") { type = NavType.IntType },
                    navArgument("flatNumber") { type = NavType.IntType }
                )
            ) { backStackEntry ->
                val towerIndex = backStackEntry.arguments?.getInt("towerIndex") ?: 0
                val flatNumber = backStackEntry.arguments?.getInt("flatNumber") ?: 0
                val tower = towers.getOrNull(towerIndex)

                if (tower != null) {
                    val unitDigit = flatNumber % 100
                    val doorTypes = PHDDoorConfig.getDoorTypes(tower.sheetName, unitDigit)
                    val flatMap = phdStatuses[flatNumber] ?: emptyMap()

                    PHDFlatScreen(
                        flatNumber = flatNumber,
                        towerName = tower.name,
                        doorTypes = doorTypes,
                        statuses = flatMap,
                        flatCompletion = phdViewModel.flatCompletion(flatNumber, tower.sheetName),
                        onToggle = { doorType ->
                            phdViewModel.toggleStatus(tower.id, flatNumber, doorType)
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            composable(
                route = Screen.PHDUnitType.route,
                arguments = listOf(
                    navArgument("towerIndex") { type = NavType.IntType },
                    navArgument("unitDigit") { type = NavType.IntType }
                )
            ) { backStackEntry ->
                val towerIndex = backStackEntry.arguments?.getInt("towerIndex") ?: 0
                val unitDigit = backStackEntry.arguments?.getInt("unitDigit") ?: 1
                val tower = towers.getOrNull(towerIndex)

                if (tower != null) {
                    LaunchedEffect(tower.id) {
                        phdViewModel.loadStatuses(tower.id)
                    }

                    val doorTypes = PHDDoorConfig.getDoorTypes(tower.sheetName, unitDigit)

                    PHDUnitTypeScreen(
                        towerName = tower.name,
                        unitDigit = unitDigit,
                        doorTypes = doorTypes,
                        statuses = phdStatuses,
                        isLoading = phdIsLoading,
                        onToggle = { flatNumber, doorType ->
                            phdViewModel.toggleStatus(tower.id, flatNumber, doorType)
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            composable(Screen.QSIDashboard.route) {
                QSIDashboardScreen(
                    qsiViewModel = qsiViewModel,
                    onNavigateToDetail = { detailType ->
                        navController.navigate(Screen.QSIDetail.createRoute(detailType))
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.QSIDetail.route,
                arguments = listOf(navArgument("detailType") { type = NavType.StringType })
            ) { backStackEntry ->
                val detailType = backStackEntry.arguments?.getString("detailType") ?: "discipline"
                QSIDetailScreen(
                    detailType = detailType,
                    qsiViewModel = qsiViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        // ── Persistent global sync indicator ────────────────────────
        GlobalSyncIndicator(
            isSyncing = isSyncing || dwIsSyncing || phdIsSyncing,
            syncDone = syncDone,
            isDownloading = isDownloading,
            isOffline = !isOnline,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 12.dp, end = 12.dp)
                .zIndex(100f)
        )

        // ── Full-screen importing overlay ───────────────────────────
        AnimatedVisibility(
            visible = isAnyImporting,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(200f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 4.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Importing…",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Please wait while data is being\nimported and synced.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// ── Activity Sync Check Dialog ─────────────────────────────────────

@Composable
fun ActivitySyncCheckDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val towers by viewModel.towers.collectAsStateWithLifecycle()
    val unmatched = remember(towers) { viewModel.getUnmatchedActivities() }

    if (unmatched.isEmpty()) {
        // No unmatched activities — auto-dismiss or show success briefly
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    val t9Color = Color(0xFF42A5F5)  // Blue for Tower 9
    val t10Color = Color(0xFFFF9800)  // Orange for Tower 10

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Text("Activity Sync", style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "${unmatched.size} activities exist in only one tower:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(unmatched) { item ->
                        val color = if (item.towerIndex == 0) t9Color else t10Color
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = color.copy(alpha = 0.12f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Color indicator
                                Box(
                                    modifier = Modifier
                                        .size(4.dp, 28.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(color)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        item.activity.name,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Medium
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "Only in ${item.towerName}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            color = color
                                        )
                                    )
                                }
                                // Copy to other tower
                                IconButton(
                                    onClick = {
                                        viewModel.copyActivityToOtherTower(item.activity, item.towerIndex)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Copy to other tower",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                // Delete
                                IconButton(
                                    onClick = {
                                        viewModel.deleteActivityById(item.activity.id, item.towerIndex)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
fun GlobalSyncIndicator(
    isSyncing: Boolean,
    syncDone: Boolean,
    isDownloading: Boolean,
    isOffline: Boolean = false,
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

    val showIndicator = isSyncing || syncDone || isDownloading || isOffline

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
                isOffline -> {
                    Icon(
                        Icons.Default.WifiOff,
                        contentDescription = "Offline",
                        modifier = Modifier.size(18.dp),
                        tint = com.phase3.tracker.ui.theme.OfflineRed
                    )
                }
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
