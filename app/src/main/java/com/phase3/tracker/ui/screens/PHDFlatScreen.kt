package com.phase3.tracker.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phase3.tracker.ui.theme.*

/**
 * Pre Hung Doors — Flat detail screen.
 * Shows all door types for a specific flat as a checklist.
 * Format: S.no. | Door Type | Status (tap to toggle)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PHDFlatScreen(
    flatNumber: Int,
    towerName: String,
    doorTypes: List<String>,
    statuses: Map<String, Boolean>,
    flatCompletion: Float,
    onToggle: (doorType: String) -> Unit,
    onBack: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val completeColor = if (isDark) StatusCompleteDark else StatusComplete

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Flat $flatNumber", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "$towerName · ${flatCompletion.toInt()}% complete",
                            style = MaterialTheme.typography.labelSmall,
                            color = completeColor
                        )
                    }
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
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        if (doorTypes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No door types configured for this flat.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
            ) {
                // Header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "S.No.",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 1.sp
                            ),
                            modifier = Modifier.width(40.dp)
                        )
                        Text(
                            "DOOR TYPE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 1.sp
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "STATUS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 1.sp
                            )
                        )
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }

                itemsIndexed(doorTypes) { index, doorType ->
                    val isDone = statuses[doorType] ?: false
                    val bgColor by animateColorAsState(
                        targetValue = if (isDone) {
                            completeColor.copy(alpha = 0.15f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        },
                        animationSpec = tween(200),
                        label = "phd_item_bg"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(bgColor)
                            .clickable { onToggle(doorType) }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // S.No.
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.width(40.dp)
                        )

                        // Door Type
                        Text(
                            doorType,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isDone) FontWeight.Medium else FontWeight.Normal
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        // Status icon
                        Icon(
                            if (isDone) Icons.Default.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                            contentDescription = if (isDone) "Done" else "Not done",
                            tint = if (isDone) completeColor
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
