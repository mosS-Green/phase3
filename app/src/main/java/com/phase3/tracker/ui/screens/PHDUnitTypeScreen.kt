package com.phase3.tracker.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phase3.tracker.model.Activity
import com.phase3.tracker.ui.theme.*

/**
 * Pre Hung Doors — Unit Type screen.
 * Shows a grid of floors (34→2) × door types (columns).
 * Each cell is a tap-to-toggle done/not-done.
 * Similar to the DW UnitTypeScreen but simpler — no Frame/Shutter/Glass cycling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PHDUnitTypeScreen(
    towerName: String,
    unitDigit: Int,
    doorTypes: List<String>,
    statuses: Map<Int, Map<String, Boolean>>,
    isLoading: Boolean,
    onToggle: (flatNumber: Int, doorType: String) -> Unit,
    onBack: () -> Unit
) {
    val unitLabel = when (unitDigit) {
        1 -> "A"
        2 -> "B"
        3 -> "C"
        4 -> "D"
        else -> "$unitDigit"
    }

    val isDark = isSystemInDarkTheme()
    val doneColor = if (isDark) StatusCompleteDark else StatusComplete
    val emptyColor = if (isDark) Color(0xFF2A2A2A) else Color(0xFFEFEFEF)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "$towerName — Unit $unitLabel",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "${doorTypes.size} door types · Tap to toggle",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (doorTypes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No door types configured for this unit type.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            // Legend
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
                ) {
                    PHDLegendDot(doneColor, "Done")
                    PHDLegendDot(emptyColor, "Not Done")
                }

                val horizontalScroll = rememberScrollState()

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    // Header row with door type names
                    item {
                        Row(
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            // Floor label column (fixed)
                            Box(
                                modifier = Modifier
                                    .width(40.dp)
                                    .height(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "FL",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }

                            Row(
                                modifier = Modifier.horizontalScroll(horizontalScroll),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                doorTypes.forEach { doorType ->
                                    Box(
                                        modifier = Modifier
                                            .width(64.dp)
                                            .height(48.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            doorType,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Floor rows: 34 → 2
                    items(33) { reverseIndex ->
                        val floor = 34 - reverseIndex
                        val flatNumber = floor * 100 + unitDigit

                        Row(
                            modifier = Modifier.padding(vertical = 1.dp)
                        ) {
                            // Floor label
                            Box(
                                modifier = Modifier
                                    .width(40.dp)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(6.dp))
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

                            Row(
                                modifier = Modifier.horizontalScroll(horizontalScroll),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                doorTypes.forEach { doorType ->
                                    val isDone = statuses[flatNumber]?.get(doorType) ?: false
                                    val bgColor = if (isDone) doneColor else emptyColor
                                    val animBg by animateColorAsState(bgColor, tween(200), label = "phd_cell_bg")
                                    val label = if (isDone) "✓" else "—"
                                    val textColor = if (isDone) {
                                        Color(0xFF1B3417)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    }

                                    Box(
                                        modifier = Modifier
                                            .width(64.dp)
                                            .height(40.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(animBg)
                                            .clickable { onToggle(flatNumber, doorType) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            label,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                color = textColor,
                                                fontSize = 11.sp
                                            )
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

@Composable
private fun PHDLegendDot(color: Color, label: String) {
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
