package com.phase3.tracker.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
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
import com.phase3.tracker.model.Tower
import com.phase3.tracker.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    towers: List<Tower>,
    onNavigateToData: () -> Unit,
    onActivityClick: (towerIndex: Int, activityIndex: Int) -> Unit
) {
    var selectedTowerIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Phase 3",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Light,
                            letterSpacing = (-1).sp
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToData,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.background
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Data")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Tower toggle chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                towers.forEachIndexed { index, tower ->
                    FilterChip(
                        selected = selectedTowerIndex == index,
                        onClick = { selectedTowerIndex = index },
                        label = { Text(tower.name, style = MaterialTheme.typography.labelLarge) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = MaterialTheme.colorScheme.outline,
                            selectedBorderColor = MaterialTheme.colorScheme.primary,
                            enabled = true,
                            selected = selectedTowerIndex == index
                        )
                    )
                }
            }

            // Section header
            Text(
                "ONGOING ACTIVITIES",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            val tower = towers.getOrNull(selectedTowerIndex)
            val ongoingActivities = tower?.ongoingActivities ?: emptyList()

            if (ongoingActivities.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No ongoing activities",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    items(ongoingActivities) { activity ->
                        val activityIndex = tower!!.activities.indexOf(activity)
                        ProgressBar(
                            activity = activity,
                            onClick = { onActivityClick(selectedTowerIndex, activityIndex) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressBar(
    activity: Activity,
    onClick: () -> Unit
) {
    val animatedComplete by animateFloatAsState(
        targetValue = activity.completionPercent / 100f,
        animationSpec = tween(600),
        label = "complete"
    )
    val animatedWip by animateFloatAsState(
        targetValue = activity.wipPercent / 100f,
        animationSpec = tween(600),
        label = "wip"
    )

    val groupColor = GroupColors.getOrElse(activity.groupIndex) { GroupApartments }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(12.dp)
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(groupColor)
                )
                Text(
                    activity.name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                "${activity.completionPercent.toInt()}%",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = StatusComplete
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Stacked progress bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(StatusEmpty.copy(alpha = 0.3f))
        ) {
            if (animatedComplete > 0f) {
                Box(
                    modifier = Modifier
                        .weight(animatedComplete.coerceAtLeast(0.001f))
                        .fillMaxHeight()
                        .background(StatusComplete)
                )
            }
            if (animatedWip > 0f) {
                Box(
                    modifier = Modifier
                        .weight(animatedWip.coerceAtLeast(0.001f))
                        .fillMaxHeight()
                        .background(StatusWip)
                )
            }
            val emptyFraction = 1f - animatedComplete - animatedWip
            if (emptyFraction > 0.001f) {
                Box(
                    modifier = Modifier
                        .weight(emptyFraction)
                        .fillMaxHeight()
                )
            }
        }
    }
}
