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
import com.phase3.tracker.model.DWRoom
import com.phase3.tracker.model.DWType
import com.phase3.tracker.ui.theme.*

/**
 * Unit Type Screen: shows a grid of (Room + DW Type) columns × Floor rows
 * for a specific unit digit (1=A, 2=B, 3=C, 4=D).
 *
 * Tapping a cell cycles through: Frame done → Shutter done → Glass done → All reset.
 */

data class RoomTypeColumn(
    val roomName: String,
    val type: DWType,
    /** Room IDs per column type: "frame" -> roomId, "shutter" -> roomId, "glass" -> roomId */
    val roomIds: Map<String, Int>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitTypeScreen(
    towerName: String,
    unitDigit: Int,
    allTowerRooms: Map<String, List<DWRoom>>,
    isLoading: Boolean,
    onToggleStatus: (roomId: Int, typeId: Int, flatNumber: Int) -> Unit,
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

    // Build columns: unique (roomName, typeName) combos for this unit's flats
    val columns = remember(allTowerRooms, unitDigit) {
        buildRoomTypeColumns(allTowerRooms, unitDigit)
    }

    // Color coding for the cycling state
    // none done = empty, frame done = blue-ish, shutter done = amber-ish, glass done = green
    val frameColor = if (isDark) Color(0xFF3D5A80) else Color(0xFF90CAF9)
    val shutterColor = if (isDark) Color(0xFF7A5C30) else Color(0xFFFFCC80)
    val glassColor = if (isDark) StatusCompleteDark else StatusComplete
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
                            "Tap: F → S → G → Reset",
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
        } else if (columns.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No DW rooms configured for this tower.\nSet up rooms in the DW section first.",
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
                    LegendDot(frameColor, "Frame")
                    LegendDot(shutterColor, "Shutter")
                    LegendDot(glassColor, "Glass")
                    LegendDot(emptyColor, "Empty")
                }

                val horizontalScroll = rememberScrollState()

                // Grid: floor rows × room+type columns
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    // Header row
                    item {
                        Row(
                            modifier = Modifier
                                .padding(bottom = 4.dp)
                        ) {
                            // Floor label column (fixed)
                            Box(
                                modifier = Modifier
                                    .width(40.dp)
                                    .height(56.dp),
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
                                columns.forEach { col ->
                                    Box(
                                        modifier = Modifier
                                            .width(64.dp)
                                            .height(56.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(2.dp)
                                        ) {
                                            Text(
                                                col.roomName,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                col.type.name,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 7.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                if (col.type.isDoor) "D" else "W",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 7.sp,
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                                )
                                            )
                                        }
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
                                columns.forEach { col ->
                                    val cellState = getCellState(col, flatNumber, allTowerRooms)
                                    val bgColor = when (cellState) {
                                        CellState.FRAME_DONE -> frameColor
                                        CellState.SHUTTER_DONE -> shutterColor
                                        CellState.GLASS_DONE -> glassColor
                                        CellState.EMPTY -> emptyColor
                                    }
                                    val animBg by animateColorAsState(bgColor, tween(200), label = "cell_bg")
                                    val label = when (cellState) {
                                        CellState.FRAME_DONE -> "F"
                                        CellState.SHUTTER_DONE -> "S"
                                        CellState.GLASS_DONE -> "G"
                                        CellState.EMPTY -> "—"
                                    }
                                    val textColor = when (cellState) {
                                        CellState.GLASS_DONE -> Color(0xFF1B3417)
                                        CellState.SHUTTER_DONE -> Color(0xFF3D3520)
                                        CellState.FRAME_DONE -> if (isDark) Color(0xFFCCDDEE) else Color(0xFF1A3A5C)
                                        CellState.EMPTY -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    }

                                    Box(
                                        modifier = Modifier
                                            .width(64.dp)
                                            .height(40.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(animBg)
                                            .clickable {
                                                onCellClick(col, flatNumber, cellState, allTowerRooms, onToggleStatus)
                                            },
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

private enum class CellState {
    EMPTY, FRAME_DONE, SHUTTER_DONE, GLASS_DONE
}

/**
 * Determine the cell state based on the sequential dependency: Frame → Shutter → Glass.
 * Shows the highest completed stage. If glass is done, show GLASS_DONE regardless of lower stages.
 * If a column type's room doesn't exist for this combo, it's treated as "not applicable" (skip it).
 */
private fun getCellState(
    col: RoomTypeColumn,
    flatNumber: Int,
    allTowerRooms: Map<String, List<DWRoom>>
): CellState {
    val glassDone = isTypeComplete(col, flatNumber, "glass", allTowerRooms)
    val shutterDone = isTypeComplete(col, flatNumber, "shutter", allTowerRooms)
    val frameDone = isTypeComplete(col, flatNumber, "frame", allTowerRooms)

    return when {
        glassDone -> CellState.GLASS_DONE
        shutterDone -> CellState.SHUTTER_DONE
        frameDone -> CellState.FRAME_DONE
        else -> CellState.EMPTY
    }
}

private fun isTypeComplete(
    col: RoomTypeColumn,
    flatNumber: Int,
    colType: String,
    allTowerRooms: Map<String, List<DWRoom>>
): Boolean {
    val roomId = col.roomIds[colType] ?: return false
    val rooms = allTowerRooms[colType] ?: return false
    val room = rooms.find { it.id == roomId } ?: return false
    return room.flatStatuses[flatNumber]?.get(col.type.id) ?: false
}

/**
 * Cycle on tap: Empty → Frame done → Shutter done → Glass done → Reset all.
 */
private fun onCellClick(
    col: RoomTypeColumn,
    flatNumber: Int,
    currentState: CellState,
    allTowerRooms: Map<String, List<DWRoom>>,
    onToggleStatus: (roomId: Int, typeId: Int, flatNumber: Int) -> Unit
) {
    when (currentState) {
        CellState.EMPTY -> {
            // Set frame to done
            val roomId = col.roomIds["frame"] ?: return
            onToggleStatus(roomId, col.type.id, flatNumber)
        }
        CellState.FRAME_DONE -> {
            // Set shutter to done
            val roomId = col.roomIds["shutter"] ?: return
            onToggleStatus(roomId, col.type.id, flatNumber)
        }
        CellState.SHUTTER_DONE -> {
            // Set glass to done
            val roomId = col.roomIds["glass"] ?: return
            onToggleStatus(roomId, col.type.id, flatNumber)
        }
        CellState.GLASS_DONE -> {
            // Reset all three: toggle each off (they're currently on)
            col.roomIds["frame"]?.let { onToggleStatus(it, col.type.id, flatNumber) }
            col.roomIds["shutter"]?.let { onToggleStatus(it, col.type.id, flatNumber) }
            col.roomIds["glass"]?.let { onToggleStatus(it, col.type.id, flatNumber) }
        }
    }
}

/**
 * Build the column list from allTowerRooms, filtered to only include
 * room+type combos that have status entries for the given unit digit's flats.
 * Each unique (roomName, typeId) across all column types becomes one column.
 */
private fun buildRoomTypeColumns(allTowerRooms: Map<String, List<DWRoom>>, unitDigit: Int): List<RoomTypeColumn> {
    // All flat numbers for this unit digit (e.g. digit 1 → 201, 301, ..., 3401)
    val unitFlats = Activity.FLAT_NUMBERS.filter { it % 100 == unitDigit }.toSet()

    data class Key(val roomName: String, val typeId: Int)

    val typeMap = mutableMapOf<Int, DWType>()
    val roomIdMap = mutableMapOf<Key, MutableMap<String, Int>>()

    for ((colType, rooms) in allTowerRooms) {
        for (room in rooms) {
            for (type in room.types) {
                // Only include this room+type if there's at least one flat status
                // for any of this unit's flats
                val hasStatus = unitFlats.any { flatNum ->
                    room.flatStatuses[flatNum]?.containsKey(type.id) == true
                }
                if (!hasStatus) continue

                typeMap[type.id] = type
                val key = Key(room.name, type.id)
                roomIdMap.getOrPut(key) { mutableMapOf() }[colType] = room.id
            }
        }
    }

    return roomIdMap.entries
        .sortedWith(compareBy({ it.key.roomName }, { typeMap[it.key.typeId]?.name ?: "" }))
        .map { (key, ids) ->
            RoomTypeColumn(
                roomName = key.roomName,
                type = typeMap[key.typeId]!!,
                roomIds = ids
            )
        }
}

@Composable
private fun LegendDot(color: Color, label: String) {
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
