package com.phase3.tracker.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phase3.tracker.model.Activity
import com.phase3.tracker.ui.theme.*

/**
 * Pre Hung Doors — Tower view.
 * Shows a grid of floors (34→2) × units (01–04).
 * Each cell displays the completion % across all door types for that flat.
 * Tapping a cell navigates to the flat detail screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PHDTowerScreen(
    towerName: String,
    isLoading: Boolean,
    flatCompletion: (flatNumber: Int) -> Float,
    onFlatClick: (flatNumber: Int) -> Unit,
    onBack: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val completeColor = if (isDark) StatusCompleteDark else StatusComplete

    // Compute overall tower completion
    val overallCompletion = remember(flatCompletion) {
        val completions = Activity.FLAT_NUMBERS.map { flatCompletion(it) }
        if (completions.isEmpty()) 0f else completions.average().toFloat()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("$towerName — Pre Hung Doors", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${overallCompletion.toInt()}% complete",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                PHDFlatGrid(
                    flatCompletion = flatCompletion,
                    onFlatClick = onFlatClick
                )
            }
        }
    }
}

@Composable
private fun PHDFlatGrid(
    flatCompletion: (Int) -> Float,
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
                    val animColor by animateColorAsState(bgColor, tween(200), label = "phd_bg")
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
