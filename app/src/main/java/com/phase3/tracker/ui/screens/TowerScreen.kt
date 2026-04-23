package com.phase3.tracker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phase3.tracker.model.Activity
import com.phase3.tracker.model.FlatStatus
import com.phase3.tracker.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TowerScreen(
    towerName: String,
    activities: List<Activity>,
    editMode: Boolean,
    allGroupNames: List<String>,
    onActivityClick: (Int) -> Unit,
    onAddActivity: (name: String, contractor: String, categories: List<String>, groupName: String, usePercentage: Boolean) -> Unit,
    onRenameActivity: (index: Int, newName: String, contractor: String, categories: List<String>, groupName: String, usePercentage: Boolean) -> Unit,
    onBack: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var addActivityName by remember { mutableStateOf("") }
    var addContractor by remember { mutableStateOf("") }
    var addCategories by remember { mutableStateOf<List<String>>(emptyList()) }
    var addGroupName by remember { mutableStateOf("") }
    var addUsePercentage by remember { mutableStateOf(false) }

    var showRenameDialog by remember { mutableStateOf<Triple<Int, String, Activity?>?>(null) }
    var renameText by remember { mutableStateOf("") }
    var renameContractor by remember { mutableStateOf("") }
    var renameCategories by remember { mutableStateOf<List<String>>(emptyList()) }
    var renameGroupName by remember { mutableStateOf("") }
    var renameUsePercentage by remember { mutableStateOf(false) }

    // Collapsible group state — all expanded by default
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }

    // Group activities by groupName
    val groupedActivities = remember(activities) {
        activities.withIndex().groupBy { it.value.groupName }
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
            usePercentage = addUsePercentage,
            onUsePercentageChange = { addUsePercentage = it },
            confirmLabel = "Add",
            onConfirm = {
                if (addActivityName.isNotBlank()) {
                    onAddActivity(addActivityName.trim(), addContractor.trim(), addCategories, addGroupName.trim(), addUsePercentage)
                    addActivityName = ""
                    addContractor = ""
                    addCategories = emptyList()
                    addGroupName = ""
                    addUsePercentage = false
                    showAddDialog = false
                }
            },
            onDismiss = {
                showAddDialog = false
                addActivityName = ""
                addContractor = ""
                addCategories = emptyList()
                addGroupName = ""
                addUsePercentage = false
            }
        )
    }

    // Rename Activity dialog
    showRenameDialog?.let { (index, currentName, activity) ->
        LaunchedEffect(currentName) {
            renameText = currentName
            renameContractor = activity?.contractor ?: ""
            renameCategories = activity?.categories ?: emptyList()
            renameGroupName = activity?.groupName ?: ""
            renameUsePercentage = activity?.usePercentage ?: false
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
            usePercentage = renameUsePercentage,
            onUsePercentageChange = { renameUsePercentage = it },
            confirmLabel = "Save",
            onConfirm = {
                if (renameText.isNotBlank()) {
                    onRenameActivity(index, renameText.trim(), renameContractor.trim(), renameCategories, renameGroupName.trim(), renameUsePercentage)
                    showRenameDialog = null
                }
            },
            onDismiss = { showRenameDialog = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        towerName,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (editMode) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Activity")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            groupedActivities.forEach { (groupName, indexedActivities) ->
                val groupIndex = indexedActivities.firstOrNull()?.value?.groupIndex ?: 0
                val isExpanded = expandedGroups.getOrPut(groupName) { true }
                val groupColor = GroupColors.getOrElse(groupIndex) { GroupApartments }

                // Group header
                item(key = "header_$groupName") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                expandedGroups[groupName] = !isExpanded
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            groupName.uppercase(),
                            style = MaterialTheme.typography.labelMedium.copy(
                                letterSpacing = 1.5.sp,
                                color = groupColor,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "${indexedActivities.size}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Icon(
                                if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Activities (collapsible)
                if (isExpanded) {
                    indexedActivities.forEach { (originalIndex, activity) ->
                        item(key = "activity_${originalIndex}") {
                            ActivityItem(
                                activity = activity,
                                groupColor = groupColor,
                                onClick = { onActivityClick(originalIndex) },
                                onLongClick = {
                                    if (editMode) {
                                        showRenameDialog = Triple(originalIndex, activity.name, activity)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActivityItem(
    activity: Activity,
    groupColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val completeCount = activity.statuses.values.count { it == FlatStatus.COMPLETE }
    val wipCount = activity.statuses.values.count { it == FlatStatus.WIP }
    val total = activity.statuses.size
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
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Group color indicator
            Box(
                modifier = Modifier
                    .size(4.dp, 28.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(groupColor)
            )

            // Activity name + contractor
            Column(modifier = Modifier.weight(1f)) {
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Status info
            if (activity.usePercentage) {
                // Show average percentage for percentage-based activities
                Text(
                    "${activity.completionPercent.toInt()}%",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = completeColor
                    )
                )
            } else {
                // Status dots
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (completeCount > 0) {
                        StatusDot(completeColor, "$completeCount")
                    }
                    if (wipCount > 0) {
                        StatusDot(wipColor, "$wipCount")
                    }
                    val emptyCount = total - completeCount - wipCount
                    if (emptyCount > 0) {
                        StatusDot(emptyColor.copy(alpha = 0.7f), "$emptyCount")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusDot(color: Color, count: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            count,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}
