package com.phase3.tracker.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    isDownloading: Boolean,
    onNavigateToData: () -> Unit,
    onDownload: () -> Unit,
    onActivityClick: (towerIndex: Int, activityIndex: Int) -> Unit
) {
    var selectedTowerIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        "Phase 3",
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── Download FAB (small) ────────────────────────
                SmallFloatingActionButton(
                    onClick = onDownload,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    } else {
                        Icon(
                            Icons.Default.CloudDownload,
                            contentDescription = "Download from Telegram"
                        )
                    }
                }

                // ── Edit / Data FAB (primary) ───────────────────
                FloatingActionButton(
                    onClick = onNavigateToData,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Data")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // ── Tower selector chips ────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                towers.forEachIndexed { index, tower ->
                    FilterChip(
                        selected = selectedTowerIndex == index,
                        onClick = { selectedTowerIndex = index },
                        label = {
                            Text(
                                tower.name,
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = MaterialTheme.colorScheme.outline,
                            selectedBorderColor = MaterialTheme.colorScheme.secondaryContainer,
                            enabled = true,
                            selected = selectedTowerIndex == index
                        )
                    )
                }
            }

            // ── Section label ───────────────────────────────────
            Text(
                "ONGOING ACTIVITIES",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.5.sp,
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
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    items(ongoingActivities) { activity ->
                        val activityIndex = tower!!.activities.indexOf(activity)
                        ActivityProgressCard(
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
private fun ActivityProgressCard(
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
    val isDark = isSystemInDarkTheme()
    val completeColor = if (isDark) StatusCompleteDark else StatusComplete
    val wipColor = if (isDark) StatusWipDark else StatusWip
    val emptyColor = if (isDark) StatusEmptyDark else StatusEmpty

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    "${activity.completionPercent.toInt()}%",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = completeColor
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── Stacked progress bar ────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(emptyColor.copy(alpha = 0.35f))
            ) {
                if (animatedComplete > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(animatedComplete.coerceAtLeast(0.001f))
                            .fillMaxHeight()
                            .background(completeColor)
                    )
                }
                if (animatedWip > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(animatedWip.coerceAtLeast(0.001f))
                            .fillMaxHeight()
                            .background(wipColor)
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
}
