package com.phase3.tracker.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phase3.tracker.model.Activity
import com.phase3.tracker.model.Tower
import com.phase3.tracker.ui.theme.*
import com.phase3.tracker.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    towers: List<Tower>,
    isDownloading: Boolean,
    isSyncing: Boolean,
    syncDone: Boolean,
    selectedStatusFilters: Set<MainViewModel.StatusFilter>,
    selectedCategories: Set<String>,
    selectedContractor: String,
    allContractors: List<String>,
    onNavigateToData: () -> Unit,
    onDownload: () -> Unit,
    onSaveToDownloads: () -> Unit,
    onToggleStatusFilter: (MainViewModel.StatusFilter) -> Unit,
    onToggleCategoryFilter: (String) -> Unit,
    onSetContractorFilter: (String) -> Unit,
    onActivityClick: (towerIndex: Int, activityIndex: Int) -> Unit,
    getFilteredActivities: (Tower) -> List<Activity>
) {
    var selectedTowerIndex by remember { mutableIntStateOf(0) }
    var showStatusFilterMenu by remember { mutableStateOf(false) }
    var showCategoryFilterMenu by remember { mutableStateOf(false) }
    var showContractorFilterMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Phase 3",
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                actions = {
                    // ── Sync status indicator ────────────────────
                    SyncStatusIndicator(isSyncing = isSyncing, syncDone = syncDone)

                    // ── Status filter ────────────────────────────
                    Box {
                        IconButton(onClick = { showStatusFilterMenu = true }) {
                            BadgedBox(
                                badge = {
                                    if (selectedStatusFilters.isNotEmpty() && selectedStatusFilters != setOf(MainViewModel.StatusFilter.ONGOING)) {
                                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                            Text("${selectedStatusFilters.size}")
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.FilterList,
                                    contentDescription = "Status Filter",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showStatusFilterMenu,
                            onDismissRequest = { showStatusFilterMenu = false }
                        ) {
                            MainViewModel.StatusFilter.entries.forEach { filter ->
                                val label = when (filter) {
                                    MainViewModel.StatusFilter.COMPLETED -> "Completed"
                                    MainViewModel.StatusFilter.ONGOING -> "Ongoing"
                                    MainViewModel.StatusFilter.EMPTY -> "Empty"
                                }
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Checkbox(
                                                checked = filter in selectedStatusFilters,
                                                onCheckedChange = null
                                            )
                                            Text(label)
                                        }
                                    },
                                    onClick = { onToggleStatusFilter(filter) }
                                )
                            }
                        }
                    }

                    // ── Category filter ──────────────────────────
                    Box {
                        IconButton(onClick = { showCategoryFilterMenu = true }) {
                            BadgedBox(
                                badge = {
                                    if (selectedCategories.isNotEmpty()) {
                                        Badge(containerColor = MaterialTheme.colorScheme.tertiary) {
                                            Text("${selectedCategories.size}")
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.Category,
                                    contentDescription = "Category Filter",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showCategoryFilterMenu,
                            onDismissRequest = { showCategoryFilterMenu = false }
                        ) {
                            Activity.VALID_CATEGORIES.forEach { category ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Checkbox(
                                                checked = category in selectedCategories,
                                                onCheckedChange = null
                                            )
                                            Text(category)
                                        }
                                    },
                                    onClick = { onToggleCategoryFilter(category) }
                                )
                            }
                        }
                    }

                    // ── Contractor filter ────────────────────────
                    Box {
                        IconButton(onClick = { showContractorFilterMenu = true }) {
                            BadgedBox(
                                badge = {
                                    if (selectedContractor != "All") {
                                        Badge(containerColor = MaterialTheme.colorScheme.secondary) {
                                            Text("1")
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.Engineering,
                                    contentDescription = "Contractor Filter",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showContractorFilterMenu,
                            onDismissRequest = { showContractorFilterMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        RadioButton(
                                            selected = selectedContractor == "All",
                                            onClick = null
                                        )
                                        Text("All", fontWeight = FontWeight.Medium)
                                    }
                                },
                                onClick = {
                                    onSetContractorFilter("All")
                                    showContractorFilterMenu = false
                                }
                            )
                            HorizontalDivider()
                            allContractors.forEach { contractor ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            RadioButton(
                                                selected = selectedContractor == contractor,
                                                onClick = null
                                            )
                                            Text(contractor)
                                        }
                                    },
                                    onClick = {
                                        onSetContractorFilter(contractor)
                                        showContractorFilterMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // ── Save XLSX ────────────────────────────────
                    IconButton(onClick = onSaveToDownloads) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = "Save XLSX to Downloads",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── Download FAB (small) ────────────────────────
                SmallFloatingActionButton(
                    onClick = onDownload,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    } else {
                        Icon(
                            Icons.Default.CloudDownload,
                            contentDescription = "Sync from Google Sheets"
                        )
                    }
                }

                // ── Edit / Data FAB (primary) ───────────────────
                FloatingActionButton(
                    onClick = onNavigateToData,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Data")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // ── Tower selector chips ────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                towers.forEachIndexed { index, tower ->
                    FilterChip(
                        selected = selectedTowerIndex == index,
                        onClick = { selectedTowerIndex = index },
                        label = {
                            Text(
                                tower.name,
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = MaterialTheme.colorScheme.outline,
                            selectedBorderColor = MaterialTheme.colorScheme.secondaryContainer,
                            enabled = true,
                            selected = selectedTowerIndex == index
                        )
                    )
                }
            }

            // ── Dynamic section label ──────────────────────────
            val filterLabel = buildFilterLabel(selectedStatusFilters)
            Text(
                filterLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            val tower = towers.getOrNull(selectedTowerIndex)
            val filteredActivities = tower?.let { getFilteredActivities(it) } ?: emptyList()

            if (filteredActivities.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No matching activities",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    items(filteredActivities) { activity ->
                        val activityIndex = tower!!.activities.indexOf(activity)
                        ActivityProgressCard(
                            activity = activity,
                            onClick = { onActivityClick(selectedTowerIndex, activityIndex) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncStatusIndicator(isSyncing: Boolean, syncDone: Boolean) {
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

    Box(
        modifier = Modifier.size(40.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = when {
                isSyncing -> "syncing"
                syncDone -> "done"
                else -> "idle"
            },
            transitionSpec = {
                fadeIn(tween(200)) togetherWith fadeOut(tween(200))
            },
            label = "sync_status"
        ) { state ->
            when (state) {
                "syncing" -> {
                    Icon(
                        Icons.Default.Sync,
                        contentDescription = "Syncing",
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(rotation),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                "done" -> {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Synced",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                else -> {
                    // Idle: show nothing or a subtle indicator
                }
            }
        }
    }
}

private fun buildFilterLabel(filters: Set<MainViewModel.StatusFilter>): String {
    if (filters.isEmpty()) return "ALL ACTIVITIES"
    val parts = mutableListOf<String>()
    if (MainViewModel.StatusFilter.COMPLETED in filters) parts.add("COMPLETED")
    if (MainViewModel.StatusFilter.ONGOING in filters) parts.add("ONGOING")
    if (MainViewModel.StatusFilter.EMPTY in filters) parts.add("EMPTY")
    return parts.joinToString(" · ") + " ACTIVITIES"
}

@Composable
private fun ActivityProgressCard(
    activity: Activity,
    onClick: () -> Unit
) {
    val animatedComplete by animateFloatAsState(
        targetValue = activity.completionPercent / 100f,
        animationSpec = tween(600),
        label = "complete"
    )
    val animatedWip by animateFloatAsState(
        targetValue = activity.wipPercent / 100f,
        animationSpec = tween(600),
        label = "wip"
    )

    val groupColor = GroupColors.getOrElse(activity.groupIndex) { GroupApartments }
    val isDark = isSystemInDarkTheme()
    val completeColor = if (isDark) StatusCompleteDark else StatusComplete
    val wipColor = if (isDark) StatusWipDark else StatusWip
    val emptyColor = if (isDark) StatusEmptyDark else StatusEmpty

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(groupColor)
                    )
                    Column {
                        Text(
                            activity.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (activity.contractor.isNotBlank()) {
                            Text(
                                activity.contractor,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Text(
                    "${activity.completionPercent.toInt()}%",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = completeColor
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── Stacked progress bar ────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(emptyColor.copy(alpha = 0.35f))
            ) {
                if (animatedComplete > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(animatedComplete.coerceAtLeast(0.001f))
                            .fillMaxHeight()
                            .background(completeColor)
                    )
                }
                if (animatedWip > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(animatedWip.coerceAtLeast(0.001f))
                            .fillMaxHeight()
                            .background(wipColor)
                    )
                }
                val emptyFraction = 1f - animatedComplete - animatedWip
                if (emptyFraction > 0.001f) {
                    Box(
                        modifier = Modifier
                            .weight(emptyFraction)
                            .fillMaxHeight()
                    )
                }
            }

            // Category tags
            if (activity.categories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    activity.categories.take(4).forEach { cat ->
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(
                                    cat,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp)
                                )
                            },
                            modifier = Modifier.height(22.dp),
                            shape = RoundedCornerShape(6.dp)
                        )
                    }
                }
            }
        }
    }
}
