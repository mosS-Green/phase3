package com.phase3.tracker.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phase3.tracker.model.Activity
import com.phase3.tracker.model.Tower
import com.phase3.tracker.ui.theme.*
import com.phase3.tracker.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    towers: List<Tower>,
    isDownloading: Boolean,
    editMode: Boolean,
    selectedStatusFilters: Set<MainViewModel.StatusFilter>,
    selectedCategories: Set<String>,
    selectedContractor: String,
    allContractors: List<String>,
    allGroupNames: List<String>,
    allCategories: List<String>,
    onNavigateToData: () -> Unit,
    onDownload: () -> Unit,
    onSaveToDownloads: () -> Unit,
    onImportActivities: () -> Unit,
    onToggleStatusFilter: (MainViewModel.StatusFilter) -> Unit,
    onToggleCategoryFilter: (String) -> Unit,
    onSetContractorFilter: (String) -> Unit,
    onToggleEditMode: () -> Unit,
    onSetEditMode: (Boolean) -> Unit,
    onActivityClick: (towerIndex: Int, activityIndex: Int) -> Unit,
    onAddActivity: (towerIndex: Int, name: String, contractor: String, categories: List<String>, groupName: String, usePercentage: Boolean, weightage: Int) -> Unit,
    onRenameActivity: (towerIndex: Int, activityIndex: Int, newName: String, contractor: String, categories: List<String>, groupName: String, usePercentage: Boolean, weightage: Int) -> Unit,
    onDeleteActivity: (towerIndex: Int, activityIndex: Int) -> Unit,
    getFilteredActivities: (Tower) -> List<Activity>
) {
    var selectedTowerIndex by rememberSaveable { mutableIntStateOf(0) }
    var showStatusFilterMenu by remember { mutableStateOf(false) }
    var showCategoryFilterMenu by remember { mutableStateOf(false) }
    var showContractorFilterMenu by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Add Activity dialog state
    var showAddDialog by remember { mutableStateOf(false) }
    var addActivityName by remember { mutableStateOf("") }
    var addContractor by remember { mutableStateOf("") }
    var addCategories by remember { mutableStateOf<List<String>>(emptyList()) }
    var addGroupName by remember { mutableStateOf("") }
    var addUsePercentage by remember { mutableStateOf(false) }
    var addWeightage by remember { mutableIntStateOf(5) }

    // Rename Activity dialog state
    var showRenameDialog by remember { mutableStateOf<Triple<Int, String, Activity?>?>(null) }
    var renameText by remember { mutableStateOf("") }
    var renameContractor by remember { mutableStateOf("") }
    var renameCategories by remember { mutableStateOf<List<String>>(emptyList()) }
    var renameGroupName by remember { mutableStateOf("") }
    var renameUsePercentage by remember { mutableStateOf(false) }
    var renameWeightage by remember { mutableIntStateOf(5) }



    // Settings bottom sheet
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "Edit Mode",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            if (editMode) "Editing enabled — tap cells to change status"
                            else "View only — long press here to enable editing",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = editMode,
                        onCheckedChange = { onSetEditMode(it) },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        }
    }

    // Add Activity dialog
    if (showAddDialog) {
        ActivityFormDialog(
            title = "Add Activity",
            nameValue = addActivityName,
            onNameChange = { addActivityName = it },
            contractorValue = addContractor,
            onContractorChange = { addContractor = it },
            categories = addCategories,
            onCategoriesChange = { addCategories = it },
            groupName = addGroupName,
            onGroupNameChange = { addGroupName = it },
            allGroupNames = allGroupNames,
            allCategories = allCategories,
            usePercentage = addUsePercentage,
            onUsePercentageChange = { addUsePercentage = it },
            weightage = addWeightage,
            onWeightageChange = { addWeightage = it },
            confirmLabel = "Add",
            onConfirm = {
                if (addActivityName.isNotBlank()) {
                    onAddActivity(
                        selectedTowerIndex,
                        addActivityName.trim(),
                        addContractor.trim(),
                        addCategories,
                        addGroupName.trim(),
                        addUsePercentage,
                        addWeightage
                    )
                    addActivityName = ""; addContractor = ""; addCategories = emptyList()
                    addGroupName = ""; addUsePercentage = false; addWeightage = 5
                    showAddDialog = false
                }
            },
            onDismiss = {
                showAddDialog = false
                addActivityName = ""; addContractor = ""; addCategories = emptyList()
                addGroupName = ""; addUsePercentage = false; addWeightage = 5
            }
        )
    }

    // Rename Activity dialog
    showRenameDialog?.let { (activityIndex, currentName, activity) ->
        LaunchedEffect(currentName) {
            renameText = currentName
            renameContractor = activity?.contractor ?: ""
            renameCategories = activity?.categories ?: emptyList()
            renameGroupName = activity?.groupName ?: ""
            renameUsePercentage = activity?.usePercentage ?: false
            renameWeightage = activity?.weightage ?: 5
        }
        ActivityFormDialog(
            title = "Edit Activity",
            nameValue = renameText,
            onNameChange = { renameText = it },
            contractorValue = renameContractor,
            onContractorChange = { renameContractor = it },
            categories = renameCategories,
            onCategoriesChange = { renameCategories = it },
            groupName = renameGroupName,
            onGroupNameChange = { renameGroupName = it },
            allGroupNames = allGroupNames,
            allCategories = allCategories,
            usePercentage = renameUsePercentage,
            onUsePercentageChange = { renameUsePercentage = it },
            weightage = renameWeightage,
            onWeightageChange = { renameWeightage = it },
            confirmLabel = "Save",
            onConfirm = {
                if (renameText.isNotBlank()) {
                    onRenameActivity(
                        selectedTowerIndex,
                        activityIndex,
                        renameText.trim(),
                        renameContractor.trim(),
                        renameCategories,
                        renameGroupName.trim(),
                        renameUsePercentage,
                        renameWeightage
                    )
                    showRenameDialog = null
                }
            },
            onDismiss = { showRenameDialog = null },
            onDelete = {
                onDeleteActivity(selectedTowerIndex, activityIndex)
                showRenameDialog = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Phase 3",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = { showSettingsSheet = true }
                        )
                    )
                },
                actions = {
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
                            allCategories.forEach { category ->
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
                                        RadioButton(selected = selectedContractor == "All", onClick = null)
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
                                            RadioButton(selected = selectedContractor == contractor, onClick = null)
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
                    // ── Import XLSX ──────────────────────────────
                    IconButton(onClick = onImportActivities) {
                        Icon(
                            Icons.Default.FileOpen,
                            contentDescription = "Import XLSX from file",
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
                        Icon(Icons.Default.CloudDownload, contentDescription = "Sync from Google Sheets")
                    }
                }

                // ── Add Activity FAB (only in edit mode) ────────
                if (editMode) {
                    SmallFloatingActionButton(
                        onClick = { showAddDialog = true },
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Activity")
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
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                towers.forEachIndexed { index, tower ->
                    FilterChip(
                        selected = selectedTowerIndex == index,
                        onClick = { selectedTowerIndex = index },
                        label = {
                            Text(tower.name, style = MaterialTheme.typography.labelLarge)
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

            // ── Search bar ─────────────────────────────────────
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        "Search activities…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear search",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

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

            // Apply search on top of status/category/contractor filters
            val displayedActivities = if (searchQuery.isBlank()) filteredActivities
            else filteredActivities.filter { it.name.contains(searchQuery, ignoreCase = true) }

            if (displayedActivities.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (searchQuery.isNotBlank()) "No activities matching \"$searchQuery\""
                        else "No matching activities",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    items(displayedActivities, key = { it.id }) { activity ->
                        val activityIndex = tower!!.activities.indexOf(activity)

                        ActivityProgressCard(
                            activity = activity,
                            editMode = editMode,
                            onClick = { onActivityClick(selectedTowerIndex, activityIndex) },
                            onLongClick = {
                                if (editMode) {
                                    showRenameDialog = Triple(activityIndex, activity.name, activity)
                                }
                            }
                        )
                    }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActivityProgressCard(
    activity: Activity,
    editMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
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
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(14.dp)
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (activity.usePercentage) {
                        Text(
                            "%",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                fontSize = 9.sp
                            )
                        )
                    }
                    // Weightage badge (only show non-default values)
                    if (activity.weightage != 5) {
                        Text(
                            "W${activity.weightage}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                fontSize = 9.sp
                            )
                        )
                    }
                    Text(
                        "${activity.completionPercent.toInt()}%",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = completeColor
                        )
                    )
                }
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
