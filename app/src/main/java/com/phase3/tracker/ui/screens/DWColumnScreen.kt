package com.phase3.tracker.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phase3.tracker.model.Activity
import com.phase3.tracker.model.DWRoom
import com.phase3.tracker.model.DWType
import com.phase3.tracker.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DWColumnScreen(
    towerName: String,
    towerId: Int,
    rooms: List<DWRoom>,
    dwTypes: List<DWType>,
    isLoading: Boolean,
    onLoadRooms: (columnType: String) -> Unit,
    onAddRoom: (columnType: String, name: String, typeIds: List<Int>) -> Unit,
    onUpdateRoom: (roomId: Int, columnType: String, name: String, typeIds: List<Int>) -> Unit,
    onDeleteRoom: (roomId: Int, columnType: String) -> Unit,
    onFlatClick: (flatNumber: Int) -> Unit,
    flatCompletion: (flatNumber: Int) -> Float,
    columnCompletion: () -> Float,
    onBack: () -> Unit
) {
    val columns = listOf("frame", "shutter", "glass")
    val columnLabels = listOf("Frame", "Shutter", "Glass")
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddRoomDialog by remember { mutableStateOf(false) }
    var editingRoom by remember { mutableStateOf<DWRoom?>(null) }

    val isDark = isSystemInDarkTheme()
    val completeColor = if (isDark) StatusCompleteDark else StatusComplete
    val wipColor = if (isDark) StatusWipDark else StatusWip
    val emptyColor = if (isDark) StatusEmptyDark else StatusEmpty

    // Load rooms when tab changes
    LaunchedEffect(selectedTab) {
        onLoadRooms(columns[selectedTab])
    }

    // Add/Edit room dialog
    if (showAddRoomDialog || editingRoom != null) {
        DWRoomDialog(
            initial = editingRoom,
            allTypes = dwTypes,
            onConfirm = { name, typeIds ->
                val colType = columns[selectedTab]
                if (editingRoom != null) {
                    onUpdateRoom(editingRoom!!.id, colType, name, typeIds)
                } else {
                    onAddRoom(colType, name, typeIds)
                }
                showAddRoomDialog = false
                editingRoom = null
            },
            onDismiss = {
                showAddRoomDialog = false
                editingRoom = null
            },
            onDelete = if (editingRoom != null) {
                {
                    onDeleteRoom(editingRoom!!.id, columns[selectedTab])
                    editingRoom = null
                }
            } else null
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$towerName — DW") },
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
                onClick = { showAddRoomDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Room")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                columnLabels.forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(label) }
                    )
                }
            }

            // Completion bar
            val completion = columnCompletion()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${rooms.size} rooms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${completion.toInt()}% complete",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = completeColor
                )
            }

            // Room chips (tap to edit)
            if (rooms.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rooms.forEach { room ->
                        SuggestionChip(
                            onClick = { editingRoom = room },
                            label = {
                                Text(
                                    "${room.name} (${room.types.size})",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier.height(28.dp),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                // Flat grid — same layout as ActivityScreen
                DWFlatGrid(
                    flatCompletion = flatCompletion,
                    completeColor = completeColor,
                    wipColor = wipColor,
                    emptyColor = emptyColor,
                    onFlatClick = onFlatClick
                )
            }
        }
    }
}

@Composable
private fun DWFlatGrid(
    flatCompletion: (Int) -> Float,
    completeColor: Color,
    wipColor: Color,
    emptyColor: Color,
    onFlatClick: (Int) -> Unit
) {
    val isDark = isSystemInDarkTheme()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        contentPadding = PaddingValues(bottom = 88.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Box(modifier = Modifier.width(40.dp), contentAlignment = Alignment.Center) {
                    Text("FL", style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold, fontSize = 10.sp
                    ))
                }
                for (unit in 1..4) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("0$unit", style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp
                        ))
                    }
                }
            }
        }

        // Floors 34→2
        items(33) { reverseIndex ->
            val floor = 34 - reverseIndex
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 1.5.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp).height(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$floor", style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ))
                }

                for (unit in 1..4) {
                    val flatNumber = floor * 100 + unit
                    val pct = flatCompletion(flatNumber)
                    val bgColor = when {
                        pct >= 85f -> if (isDark) PctLevel5Dark else PctLevel5
                        pct >= 51f -> if (isDark) PctLevel4Dark else PctLevel4
                        pct >= 26f -> if (isDark) PctLevel3Dark else PctLevel3
                        pct >= 1f  -> if (isDark) PctLevel2Dark else PctLevel2
                        else       -> if (isDark) PctLevel1Dark else PctLevel1
                    }
                    val animColor by animateColorAsState(bgColor, tween(200), label = "dw_bg")
                    val textColor = when {
                        pct >= 85f -> Color(0xFF1B3417)
                        pct >= 51f -> Color(0xFF2B3D1F)
                        pct >= 26f -> Color(0xFF3D3520)
                        pct >= 1f  -> Color(0xFF3E2A18)
                        else       -> Color(0xFF3E1F18)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f).height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(animColor)
                            .clickable { onFlatClick(flatNumber) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (pct > 0f) "${pct.toInt()}%" else "—",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Medium,
                                color = textColor, fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DWRoomDialog(
    initial: DWRoom?,
    allTypes: List<DWType>,
    onConfirm: (name: String, typeIds: List<Int>) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    val selectedTypeIds = remember {
        mutableStateListOf<Int>().also {
            initial?.types?.forEach { t -> it.add(t.id) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial != null) "Edit Room" else "Add Room") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Room Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "Assign Types",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (allTypes.isEmpty()) {
                    Text(
                        "No types defined. Add types from the ⋮ menu first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 250.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(allTypes) { type ->
                            val isSelected = type.id in selectedTypeIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (isSelected) selectedTypeIds.remove(type.id)
                                        else selectedTypeIds.add(type.id)
                                    }
                                    .padding(vertical = 6.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = {
                                        if (it) selectedTypeIds.add(type.id)
                                        else selectedTypeIds.remove(type.id)
                                    }
                                )
                                Column {
                                    Text(type.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "${type.kind.replaceFirstChar { it.uppercase() }} — ${type.height}×${type.breadth}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Delete button
                if (onDelete != null) {
                    HorizontalDivider()
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete Room")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && selectedTypeIds.isNotEmpty()) {
                        onConfirm(name.trim(), selectedTypeIds.toList())
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        shape = RoundedCornerShape(28.dp)
    )
}
