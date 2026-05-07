package com.phase3.tracker.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phase3.tracker.model.DWType
import com.phase3.tracker.model.Tower

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DWHomeScreen(
    towers: List<Tower>,
    dwTypes: List<DWType>,
    onTowerClick: (towerIndex: Int) -> Unit,
    onAddType: (name: String, kind: String, height: Double, breadth: Double) -> Unit,
    onUpdateType: (id: Int, name: String, kind: String, height: Double, breadth: Double) -> Unit,
    onDeleteType: (id: Int) -> Unit,
    onExportExcel: () -> Unit,
    onImportExcel: () -> Unit,
    onBack: () -> Unit
) {
    var showTypesSheet by remember { mutableStateOf(false) }
    var showAddTypeDialog by remember { mutableStateOf(false) }
    var editingType by remember { mutableStateOf<DWType?>(null) }

    // Types management bottom sheet
    if (showTypesSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTypesSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Door/Window Types",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    IconButton(onClick = { showAddTypeDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Type")
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                if (dwTypes.isEmpty()) {
                    Text(
                        "No types defined yet. Tap + to add.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.heightIn(max = 400.dp)
                    ) {
                        items(dwTypes, key = { it.id }) { type ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            type.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            "${type.kind.replaceFirstChar { it.uppercase() }} — ${type.height}×${type.breadth}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row {
                                        IconButton(onClick = { editingType = type }) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Edit",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(onClick = { onDeleteType(type.id) }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add/Edit type dialog
    val typeToEdit = editingType
    if (showAddTypeDialog || typeToEdit != null) {
        DWTypeDialog(
            initial = typeToEdit,
            onConfirm = { name, kind, h, b ->
                if (typeToEdit != null) {
                    onUpdateType(typeToEdit.id, name, kind, h, b)
                } else {
                    onAddType(name, kind, h, b)
                }
                showAddTypeDialog = false
                editingType = null
            },
            onDismiss = {
                showAddTypeDialog = false
                editingType = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aluminium Doors & Windows") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Import
                    IconButton(onClick = onImportExcel) {
                        Icon(Icons.Default.FileOpen, contentDescription = "Import Excel")
                    }
                    // Export
                    IconButton(onClick = onExportExcel) {
                        Icon(Icons.Default.SaveAlt, contentDescription = "Export Excel")
                    }
                    // Types management
                    IconButton(onClick = { showTypesSheet = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Manage Types")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            towers.forEachIndexed { index, tower ->
                ElevatedCard(
                    onClick = { onTowerClick(index) },
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp)
                    ) {
                        Text(tower.name, style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Frame · Shutter · Glass",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DWTypeDialog(
    initial: DWType?,
    onConfirm: (name: String, kind: String, height: Double, breadth: Double) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var kind by remember { mutableStateOf(initial?.kind ?: "door") }
    var height by remember { mutableStateOf(initial?.height?.toString() ?: "") }
    var breadth by remember { mutableStateOf(initial?.breadth?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial != null) "Edit Type" else "Add Type") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = kind == "door",
                        onClick = { kind = "door" },
                        label = { Text("Door") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = kind == "window",
                        onClick = { kind = "window" },
                        label = { Text("Window") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = height,
                        onValueChange = { height = it },
                        label = { Text("H") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = breadth,
                        onValueChange = { breadth = it },
                        label = { Text("B") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(
                            name.trim(), kind,
                            height.toDoubleOrNull() ?: 0.0,
                            breadth.toDoubleOrNull() ?: 0.0
                        )
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        shape = RoundedCornerShape(28.dp)
    )
}
