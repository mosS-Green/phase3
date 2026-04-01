package com.phase3.tracker.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phase3.tracker.model.Tower
import com.phase3.tracker.ui.theme.StatusComplete
import com.phase3.tracker.ui.theme.StatusCompleteDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataScreen(
    towers: List<Tower>,
    isUploading: Boolean,
    isDownloading: Boolean,
    onTowerClick: (Int) -> Unit,
    onUpload: () -> Unit,
    onDownload: () -> Unit,
    onBack: () -> Unit
) {
    var showUploadDialog by remember { mutableStateOf(false) }

    if (showUploadDialog) {
        AlertDialog(
            onDismissRequest = { showUploadDialog = false },
            title = { Text("Upload Changes") },
            text = { Text("Upload the current Excel file to Telegram?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUploadDialog = false
                        onUpload()
                    }
                ) {
                    Text("Upload")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUploadDialog = false }) {
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
                        "Towers",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = onDownload,
                        enabled = !isDownloading
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                Icons.Default.CloudDownload,
                                contentDescription = "Download from Telegram",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showUploadDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                icon = {
                    if (isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                    }
                },
                text = { Text(if (isUploading) "Uploading…" else "Upload") }
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
                TowerCard(
                    tower = tower,
                    onClick = { onTowerClick(index) }
                )
            }
        }
    }
}

@Composable
private fun TowerCard(
    tower: Tower,
    onClick: () -> Unit
) {
    val completion = tower.overallCompletion
    val isDark = isSystemInDarkTheme()
    val completeColor = if (isDark) StatusCompleteDark else StatusComplete

    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 1.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                tower.name,
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "ACTIVITIES",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Text(
                        "${tower.activities.size}",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "COMPLETION",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Text(
                        "${completion.toInt()}%",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = completeColor
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = { completion / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = completeColor,
                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
        }
    }
}
