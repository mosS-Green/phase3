package com.phase3.tracker.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
    onActivityClick: (Int) -> Unit,
    onAddActivity: (name: String, contractor: String, categories: List<String>) -> Unit,
    onRenameActivity: (index: Int, newName: String, contractor: String, categories: List<String>) -> Unit,
    onBack: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var addActivityName by remember { mutableStateOf("") }
    var addContractor by remember { mutableStateOf("") }
    var addCategories by remember { mutableStateOf<List<String>>(emptyList()) }

    var showRenameDialog by remember { mutableStateOf<Triple<Int, String, Activity?>?>(null) }
    var renameText by remember { mutableStateOf("") }
    var renameContractor by remember { mutableStateOf("") }
    var renameCategories by remember { mutableStateOf<List<String>>(emptyList()) }

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
            confirmLabel = "Add",
            onConfirm = {
                if (addActivityName.isNotBlank()) {
                    onAddActivity(addActivityName.trim(), addContractor.trim(), addCategories)
                    addActivityName = ""
                    addContractor = ""
                    addCategories = emptyList()
                    showAddDialog = false
                }
            },
            onDismiss = {
                showAddDialog = false
                addActivityName = ""
                addContractor = ""
                addCategories = emptyList()
            }
        )
    }

    // Rename Activity dialog
    showRenameDialog?.let { (index, currentName, activity) ->
        LaunchedEffect(currentName) {
            renameText = currentName
            renameContractor = activity?.contractor ?: ""
            renameCategories = activity?.categories ?: emptyList()
        }
        ActivityFormDialog(
            title = "Edit Activity",
            nameValue = renameText,
            onNameChange = { renameText = it },
            contractorValue = renameContractor,
            onContractorChange = { renameContractor = it },
            categories = renameCategories,
            onCategoriesChange = { renameCategories = it },
            confirmLabel = "Save",
            onConfirm = {
                if (renameText.isNotBlank()) {
                    onRenameActivity(index, renameText.trim(), renameContractor.trim(), renameCategories)
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
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Activity")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            itemsIndexed(activities) { index, activity ->
                // Group header
                val prevGroup = activities.getOrNull(index - 1)?.groupName
                if (activity.groupName != prevGroup) {
                    if (index > 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Text(
                        activity.groupName.uppercase(),
                        style = MaterialTheme.typography.labelMedium.copy(
                            letterSpacing = 1.5.sp,
                            color = GroupColors.getOrElse(activity.groupIndex) { GroupApartments },
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                ActivityItem(
                    activity = activity,
                    groupColor = GroupColors.getOrElse(activity.groupIndex) { GroupApartments },
                    onClick = { onActivityClick(index) },
                    onLongClick = { showRenameDialog = Triple(index, activity.name, activity) }
                )
            }
        }
    }
}

@Composable
private fun ActivityFormDialog(
    title: String,
    nameValue: String,
    onNameChange: (String) -> Unit,
    contractorValue: String,
    onContractorChange: (String) -> Unit,
    categories: List<String>,
    onCategoriesChange: (List<String>) -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var categoryInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Activity Name
                OutlinedTextField(
                    value = nameValue,
                    onValueChange = onNameChange,
                    label = { Text("Activity name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Contractor
                OutlinedTextField(
                    value = contractorValue,
                    onValueChange = onContractorChange,
                    label = { Text("Contractor") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Category tags
                Text(
                    "Categories",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Current tags
                if (categories.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEach { cat ->
                            InputChip(
                                selected = true,
                                onClick = {
                                    onCategoriesChange(categories - cat)
                                },
                                label = { Text(cat, style = MaterialTheme.typography.labelSmall) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove $cat",
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }
                }

                // Preset category chips
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Activity.VALID_CATEGORIES.forEach { cat ->
                        val isSelected = cat in categories
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) {
                                    onCategoriesChange(categories - cat)
                                } else {
                                    onCategoriesChange(categories + cat)
                                }
                            },
                            label = { Text(cat, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(28.dp),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                // Custom category input
                OutlinedTextField(
                    value = categoryInput,
                    onValueChange = { categoryInput = it },
                    label = { Text("Custom category") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val trimmed = categoryInput.trim()
                            if (trimmed.isNotBlank() && trimmed !in categories) {
                                onCategoriesChange(categories + trimmed)
                                categoryInput = ""
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(28.dp)
    )
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
