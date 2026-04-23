package com.phase3.tracker.ui.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.phase3.tracker.model.Activity
import com.phase3.tracker.model.FlatStatus
import com.phase3.tracker.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(
    activityName: String,
    activity: Activity,
    editMode: Boolean,
    onToggleFlat: (Int) -> Unit,
    onToggleFloor: (Int) -> Unit,
    onUpdatePercentage: (flatNumber: Int, percentage: Int) -> Unit,
    onBack: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val completeColor = if (isDark) StatusCompleteDark else StatusComplete
    val wipColor = if (isDark) StatusWipDark else StatusWip
    val emptyColor = if (isDark) StatusEmptyDark else StatusEmpty
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // Percentage input dialog state
    var showPercentageDialog by remember { mutableStateOf<Int?>(null) }
    var percentageInput by remember { mutableStateOf("") }

    // Percentage input dialog
    showPercentageDialog?.let { flatNumber ->
        val currentPct = activity.percentages[flatNumber] ?: 0
        LaunchedEffect(flatNumber) {
            percentageInput = if (currentPct > 0) currentPct.toString() else ""
        }
        AlertDialog(
            onDismissRequest = { showPercentageDialog = null },
            title = {
                Text(
                    if (activity.isFloorBased) "Floor ${flatNumber / 100}" else "Flat $flatNumber",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter completion percentage (0-100)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = percentageInput,
                        onValueChange = { v ->
                            // Only allow digits 0-100
                            val filtered = v.filter { it.isDigit() }
                            val num = filtered.toIntOrNull()
                            if (num == null || num <= 100) {
                                percentageInput = filtered
                            }
                        },
                        label = { Text("%") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                val pct = percentageInput.toIntOrNull() ?: 0
                                onUpdatePercentage(flatNumber, pct.coerceIn(0, 100))
                                showPercentageDialog = null
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
                TextButton(onClick = {
                    val pct = percentageInput.toIntOrNull() ?: 0
                    onUpdatePercentage(flatNumber, pct.coerceIn(0, 100))
                    showPercentageDialog = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPercentageDialog = null }) {
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
                        activityName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                captureAndShareScreen(context, view, activityName)
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = "Snapshot",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
        ) {
            // ── Legend bar ───────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
            ) {
                if (activity.usePercentage) {
                    val pctC = pctLevelColors(isDark)
                    LegendItem(pctC[4], "85-100%")
                    LegendItem(pctC[3], "51-84%")
                    LegendItem(pctC[2], "26-50%")
                    LegendItem(pctC[1], "1-25%")
                    LegendItem(pctC[0], "0%")
                } else {
                    LegendItem(completeColor, "Complete")
                    LegendItem(wipColor, "WIP")
                    LegendItem(emptyColor.copy(alpha = 0.75f), "Empty")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ── Stats row ───────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (activity.usePercentage) {
                    // Use statuses keys (covers ALL flats) — blanks default to 0%
                    val slots = if (activity.isFloorBased) {
                        activity.statuses.keys.filter { it % 100 == 1 }
                    } else {
                        activity.statuses.keys.toList()
                    }
                    val done = slots.count { (activity.percentages[it] ?: 0) >= 85 }
                    val wip = slots.count { (activity.percentages[it] ?: 0) in 1..84 }
                    val empty = slots.count { (activity.percentages[it] ?: 0) == 0 }
                    StatChip("${activity.completionPercent.toInt()}%", "Avg", completeColor)
                    StatChip("$done", "≥85%", completeColor)
                    StatChip("$wip", "WIP", wipColor)
                    StatChip("$empty", "0%", emptyColor)
                } else {
                    val relevantStatuses = if (activity.isFloorBased) {
                        activity.statuses.filter { it.key % 100 == 1 }
                    } else {
                        activity.statuses
                    }
                    val complete = relevantStatuses.values.count { it == FlatStatus.COMPLETE }
                    val wip = relevantStatuses.values.count { it == FlatStatus.WIP }
                    val empty = relevantStatuses.size - complete - wip
                    StatChip("${activity.completionPercent.toInt()}%", "Done", completeColor)
                    StatChip("$wip", "WIP", wipColor)
                    StatChip("$empty", "Pending", emptyColor)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Grid ────────────────────────────────────────────
            if (activity.isFloorBased) {
                // Floor-based grid: 1 cell per floor
                FloorBasedGrid(
                    activity = activity,
                    editMode = editMode,
                    completeColor = completeColor,
                    wipColor = wipColor,
                    emptyColor = emptyColor,
                    onToggleFloor = onToggleFloor,
                    onShowPercentageDialog = { showPercentageDialog = it }
                )
            } else {
                // Flat-based grid: 4 columns + floor label
                FlatBasedGrid(
                    activity = activity,
                    editMode = editMode,
                    completeColor = completeColor,
                    wipColor = wipColor,
                    emptyColor = emptyColor,
                    onToggleFlat = onToggleFlat,
                    onToggleFloor = onToggleFloor,
                    onShowPercentageDialog = { showPercentageDialog = it }
                )
            }
        }
    }
}

@Composable
private fun FloorBasedGrid(
    activity: Activity,
    editMode: Boolean,
    completeColor: Color,
    wipColor: Color,
    emptyColor: Color,
    onToggleFloor: (Int) -> Unit,
    onShowPercentageDialog: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Box(
                    modifier = Modifier.width(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "FL",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    )
                }
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "STATUS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }

        // Floors 34 down to 2
        items(33) { reverseIndex ->
            val floor = 34 - reverseIndex
            val flatNumber = floor * 100 + 1

            if (activity.usePercentage) {
                val pct = activity.percentages[flatNumber] ?: 0
                val bgColor = percentageColor(pct, completeColor, wipColor, emptyColor)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.5.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "$floor",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }

                    PercentageCell(
                        percentage = pct,
                        bgColor = bgColor,
                        enabled = editMode,
                        onClick = { if (editMode) onShowPercentageDialog(flatNumber) },
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                val status = activity.statuses[flatNumber] ?: FlatStatus.EMPTY

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.5.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .then(
                                if (editMode) Modifier.clickable { onToggleFloor(floor) }
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "$floor",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }

                    FlatCell(
                        flatNumber = floor, // Display floor number
                        status = status,
                        completeColor = completeColor,
                        wipColor = wipColor,
                        emptyColor = emptyColor,
                        onClick = { if (editMode) onToggleFloor(floor) },
                        showAsFloor = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun FlatBasedGrid(
    activity: Activity,
    editMode: Boolean,
    completeColor: Color,
    wipColor: Color,
    emptyColor: Color,
    onToggleFlat: (Int) -> Unit,
    onToggleFloor: (Int) -> Unit,
    onShowPercentageDialog: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // Column headers
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Box(
                    modifier = Modifier.width(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "FL",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    )
                }
                for (unit in 1..4) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "0$unit",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }
        }

        // Floors 34 down to 2
        items(33) { reverseIndex ->
            val floor = 34 - reverseIndex

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 1.5.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Floor label
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                        .then(
                            if (editMode) Modifier.clickable { onToggleFloor(floor) }
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$floor",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }

                // 4 flat cells
                for (unit in 1..4) {
                    val flatNumber = floor * 100 + unit

                    if (activity.usePercentage) {
                        val pct = activity.percentages[flatNumber] ?: 0
                        val bgColor = percentageColor(pct, completeColor, wipColor, emptyColor)
                        PercentageCell(
                            percentage = pct,
                            bgColor = bgColor,
                            enabled = editMode,
                            onClick = { if (editMode) onShowPercentageDialog(flatNumber) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        val status = activity.statuses[flatNumber] ?: FlatStatus.EMPTY
                        FlatCell(
                            flatNumber = flatNumber,
                            status = status,
                            completeColor = completeColor,
                            wipColor = wipColor,
                            emptyColor = emptyColor,
                            onClick = { if (editMode) onToggleFlat(flatNumber) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/** Returns the 5-level color array: [level1..level5] for current theme */
@Composable
private fun pctLevelColors(isDark: Boolean): List<Color> {
    return if (isDark) {
        listOf(PctLevel1Dark, PctLevel2Dark, PctLevel3Dark, PctLevel4Dark, PctLevel5Dark)
    } else {
        listOf(PctLevel1, PctLevel2, PctLevel3, PctLevel4, PctLevel5)
    }
}

@Composable
private fun percentageColor(pct: Int, completeColor: Color, wipColor: Color, emptyColor: Color): Color {
    val isDark = isSystemInDarkTheme()
    return when {
        pct >= 85  -> if (isDark) PctLevel5Dark else PctLevel5
        pct >= 51  -> if (isDark) PctLevel4Dark else PctLevel4
        pct >= 26  -> if (isDark) PctLevel3Dark else PctLevel3
        pct >= 1   -> if (isDark) PctLevel2Dark else PctLevel2
        else       -> if (isDark) PctLevel1Dark else PctLevel1
    }
}

@Composable
private fun PercentageCell(
    percentage: Int,
    bgColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedColor by animateColorAsState(
        targetValue = bgColor,
        animationSpec = tween(200),
        label = "pct_bg"
    )

    val textColor = when {
        percentage >= 85 -> Color(0xFF1B3417)   // dark green text on green bg
        percentage >= 51 -> Color(0xFF2B3D1F)   // dark sage text
        percentage >= 26 -> Color(0xFF3D3520)   // dark amber text
        percentage >= 1  -> Color(0xFF3E2A18)   // dark peach text
        else             -> Color(0xFF3E1F18)   // dark salmon text
    }

    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(animatedColor)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (percentage > 0) "$percentage%" else "—",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                color = textColor,
                fontSize = 11.sp
            ),
            textAlign = TextAlign.Center
        )
    }
}

private suspend fun captureAndShareScreen(context: Context, view: View, activityName: String) {
    withContext(Dispatchers.IO) {
        try {
            // Capture the current view
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            view.draw(canvas)

            // Save to cache dir for sharing
            val fileName = "Phase3_${activityName.replace(Regex("[^a-zA-Z0-9]"), "_")}_${System.currentTimeMillis()}.png"
            val cacheDir = File(context.cacheDir, "snapshots")
            cacheDir.mkdirs()
            val file = File(cacheDir, fileName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            // Also save to gallery
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Phase3")
                }
                context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)?.let { uri ->
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                }
            }

            bitmap.recycle()

            // Share via intent
            withContext(Dispatchers.Main) {
                try {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Snapshot"))
                } catch (e: Exception) {
                    Toast.makeText(context, "Saved to gallery", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Snapshot failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
private fun FlatCell(
    flatNumber: Int,
    status: FlatStatus,
    completeColor: Color,
    wipColor: Color,
    emptyColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showAsFloor: Boolean = false
) {
    val backgroundColor by animateColorAsState(
        targetValue = when (status) {
            FlatStatus.COMPLETE -> completeColor
            FlatStatus.WIP -> wipColor
            FlatStatus.EMPTY -> emptyColor.copy(alpha = 0.55f)
        },
        animationSpec = tween(200),
        label = "bg"
    )

    val textColor = when (status) {
        FlatStatus.COMPLETE -> Color(0xFF1B3417) // dark on sage
        FlatStatus.WIP -> Color(0xFF3D3520)      // dark on pale yellow
        FlatStatus.EMPTY -> Color(0xFF3E1F18)    // dark on salmon
    }

    val label = when (status) {
        FlatStatus.COMPLETE -> if (showAsFloor) "✓" else "$flatNumber"
        FlatStatus.WIP -> if (showAsFloor) "WIP" else "$flatNumber"
        FlatStatus.EMPTY -> if (showAsFloor) "—" else "$flatNumber"
    }

    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                color = textColor,
                fontSize = if (showAsFloor) 14.sp else 11.sp
            )
        )
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

@Composable
private fun StatChip(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}
