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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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
    onActivityClick: (Int) -> Unit,
    onAddActivity: (String) -> Unit,
    onRenameActivity: (Int, String) -> Unit,
    onBack: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var addActivityName by remember { mutableStateOf("") }
    var showRenameDialog by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var renameText by remember { mutableStateOf("") }

    // Add Activity dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Activity") },
            text = {
                OutlinedTextField(
                    value = addActivityName,
                    onValueChange = { addActivityName = it },
                    label = { Text("Activity name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (addActivityName.isNotBlank()) {
                            onAddActivity(addActivityName.trim())
                            addActivityName = ""
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false; addActivityName = "" }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(28.dp)
        )
    }

    // Rename Activity dialog
    showRenameDialog?.let { (index, currentName) ->
        LaunchedEffect(currentName) { renameText = currentName }
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("Rename Activity") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("New name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            onRenameActivity(index, renameText.trim())
                            showRenameDialog = null
                        }
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(28.dp)
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
                    onLongClick = { showRenameDialog = Pair(index, activity.name) }
                )
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

            // Activity name
            Text(
                activity.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

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
