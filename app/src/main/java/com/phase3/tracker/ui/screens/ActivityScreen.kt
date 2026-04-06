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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
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
    onToggleFlat: (Int) -> Unit,
    onToggleFloor: (Int) -> Unit,
    onBack: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val completeColor = if (isDark) StatusCompleteDark else StatusComplete
    val wipColor = if (isDark) StatusWipDark else StatusWip
    val emptyColor = if (isDark) StatusEmptyDark else StatusEmpty
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

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
                LegendItem(completeColor, "Complete")
                LegendItem(wipColor, "WIP")
                LegendItem(emptyColor.copy(alpha = 0.75f), "Empty")
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ── Stats row ───────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val complete = activity.statuses.values.count { it == FlatStatus.COMPLETE }
                val wip = activity.statuses.values.count { it == FlatStatus.WIP }
                val empty = activity.statuses.size - complete - wip

                StatChip("${activity.completionPercent.toInt()}%", "Done", completeColor)
                StatChip("$wip", "WIP", wipColor)
                StatChip("$empty", "Pending", emptyColor)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Grid: 4 columns + floor label ───────────────────
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
                                .clickable { onToggleFloor(floor) },
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
                            val status = activity.statuses[flatNumber] ?: FlatStatus.EMPTY

                            FlatCell(
                                flatNumber = flatNumber,
                                status = status,
                                completeColor = completeColor,
                                wipColor = wipColor,
                                emptyColor = emptyColor,
                                onClick = { onToggleFlat(flatNumber) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
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
    modifier: Modifier = Modifier
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

    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "$flatNumber",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                color = textColor,
                fontSize = 11.sp
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
