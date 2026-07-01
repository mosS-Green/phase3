package com.phase3.tracker.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import com.phase3.tracker.ui.captureAndShareScreen
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phase3.tracker.viewmodel.QSIMetrics
import com.phase3.tracker.viewmodel.QSIViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QSIDetailScreen(
    detailType: String, // "discipline", "compliance", "open-ncs"
    qsiViewModel: QSIViewModel,
    onBack: () -> Unit
) {
    val metrics by qsiViewModel.metrics.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var ageFilter by remember { mutableStateOf("all") } // "all", "lte20", "gt20", "gt35"
    var selectedNcDetail by remember { mutableStateOf<Map<String, String>?>(null) }

    if (metrics == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No metrics data available.")
        }
        return
    }

    val (ncRows, title, themeColor, icon) = when (detailType) {
        "compliance" -> Quad(metrics!!.complianceNCs, "Compliance — Contractors", Color(0xFF1565C0), Icons.Default.Verified)
        "open-ncs" -> Quad(metrics!!.openNCsData, "Open NCs — Added & Rejected", Color(0xFFE65100), Icons.Default.Warning)
        else -> Quad(metrics!!.panel2DetailNCs, "Discipline — Contractors", Color(0xFF2E7D32), Icons.Default.Gavel)
    }

    val filteredRows = remember(ncRows, ageFilter) {
        ncRows.filter { row ->
            val age = row["Age Of NC(Days)"]?.toIntOrNull() ?: 0
            when (ageFilter) {
                "lte20" -> age <= 20
                "gt20" -> age > 20
                "gt35" -> age > 35
                else -> true
            }
        }
    }

    // Build hierarchical tree: Contractor -> Tower -> Age -> NC Description -> List of NC IDs
    val pivotTree = remember(filteredRows) {
        val tree = mutableMapOf<String, MutableMap<String, MutableMap<Int, MutableMap<String, MutableList<Map<String, String>>>>>>()
        filteredRows.forEach { row ->
            val contractor = (row["Contractor Name"] ?: "Unknown").trim()
            val tower = (row["Tower"] ?: "Unknown").trim().replace("\n", "").trim()
            val age = row["Age Of NC(Days)"]?.toIntOrNull() ?: 0
            val ncDesc = (row["NC"] ?: "Unknown").trim()

            tree.getOrPut(contractor) { mutableMapOf() }
                .getOrPut(tower) { mutableMapOf() }
                .getOrPut(age) { mutableMapOf() }
                .getOrPut(ncDesc) { mutableListOf() }
                .add(row)
        }
        tree
    }

    val contractors = remember(pivotTree) { pivotTree.keys.sorted() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${filteredRows.size} NCs", style = MaterialTheme.typography.labelSmall, color = themeColor)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            captureAndShareScreen(context, view, title)
                        }
                    }) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Capture Detail")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Age Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("AGE:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                AgeChip(label = "All", selected = ageFilter == "all", onClick = { ageFilter = "all" })
                AgeChip(label = "≤ 20d", selected = ageFilter == "lte20", onClick = { ageFilter = "lte20" })
                AgeChip(label = "> 20d", selected = ageFilter == "gt20", onClick = { ageFilter = "gt20" })
                AgeChip(label = "> 35d", selected = ageFilter == "gt35", onClick = { ageFilter = "gt35" })
            }

            if (contractors.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.FilterListOff, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No matching NCs found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(contractors) { contractor ->
                        val towersMap = pivotTree[contractor] ?: emptyMap()
                        ContractorCard(
                            contractorName = contractor,
                            towersMap = towersMap,
                            themeColor = themeColor,
                            onNcClick = { selectedNcDetail = it }
                        )
                    }
                }
            }
        }
    }

    if (selectedNcDetail != null) {
        NCDetailDialog(
            nc = selectedNcDetail!!,
            onDismiss = { selectedNcDetail = null },
            onOpenUrl = { url ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
        )
    }
}

// ── Composable Node Elements ─────────────────────────────────────────────

@Composable
fun ContractorCard(
    contractorName: String,
    towersMap: Map<String, Map<Int, Map<String, List<Map<String, String>>>>>,
    themeColor: Color,
    onNcClick: (Map<String, String>) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val totalCount = remember(towersMap) {
        towersMap.values.sumOf { agesMap -> agesMap.values.sumOf { ncsMap -> ncsMap.values.sumOf { it.size } } }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.Default.Business, contentDescription = null, tint = themeColor, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        contractorName,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Badge(containerColor = themeColor) {
                    Text("$totalCount")
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    towersMap.keys.sorted().forEach { tower ->
                        val agesMap = towersMap[tower] ?: emptyMap()
                        TowerNode(
                            towerName = tower,
                            agesMap = agesMap,
                            themeColor = themeColor,
                            onNcClick = onNcClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TowerNode(
    towerName: String,
    agesMap: Map<Int, Map<String, List<Map<String, String>>>>,
    themeColor: Color,
    onNcClick: (Map<String, String>) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val towerCount = remember(agesMap) {
        agesMap.values.sumOf { ncsMap -> ncsMap.values.sumOf { it.size } }
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.Apartment, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(6.dp))
                Text(towerName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
            Text("$towerCount", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Sort age decending
                agesMap.keys.sortedDescending().forEach { age ->
                    val ncsMap = agesMap[age] ?: emptyMap()
                    AgeNode(
                        age = age,
                        ncsMap = ncsMap,
                        onNcClick = onNcClick
                    )
                }
            }
        }
    }
}

@Composable
fun AgeNode(
    age: Int,
    ncsMap: Map<String, List<Map<String, String>>>,
    onNcClick: (Map<String, String>) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val ageCount = remember(ncsMap) { ncsMap.values.sumOf { it.size } }
    val isCritical = age >= 26
    val isDanger = age > 20 && age < 26

    val textColor = when {
        isCritical -> Color(0xFFB71C1C)
        isDanger -> Color(0xFFE65100)
        else -> MaterialTheme.colorScheme.onSurface
    }

    val bgColor = when {
        isCritical -> Color(0xFFB71C1C).copy(alpha = 0.05f)
        isDanger -> Color(0xFFE65100).copy(alpha = 0.05f)
        else -> Color.Transparent
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                if (isCritical) {
                    Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(14.dp), tint = textColor)
                    Spacer(modifier = Modifier.width(4.dp))
                } else {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp), tint = textColor)
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text("$age days", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = textColor)
            }
            Text("$ageCount", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = textColor)
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ncsMap.keys.sorted().forEach { ncDesc ->
                    val rowsList = ncsMap[ncDesc] ?: emptyList()
                    DescNode(
                        ncDesc = ncDesc,
                        rowsList = rowsList,
                        onNcClick = onNcClick
                    )
                }
            }
        }
    }
}

@Composable
fun DescNode(
    ncDesc: String,
    rowsList: List<Map<String, String>>,
    onNcClick: (Map<String, String>) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 6.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(14.dp).padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                ncDesc,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "${rowsList.size}",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(visible = expanded) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowsList.forEach { row ->
                    val id = row["NC ID"] ?: "N/A"
                    SuggestionChip(
                        onClick = { onNcClick(row) },
                        label = { Text(id, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.height(26.dp)
                    )
                }
            }
        }
    }
}

// ── Detail Pop-up dialog ──────────────────────────────────────────────────

@Composable
fun NCDetailDialog(
    nc: Map<String, String>,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val status = nc["Status"] ?: "Unknown"
    val severity = nc["NC Severity Name"] ?: nc["GPL Severity"] ?: "Unknown"
    val age = nc["Age Of NC(Days)"] ?: "N/A"

    val statusColor = when (status.lowercase()) {
        "approved" -> Color(0xFF2E7D32)
        "added" -> Color(0xFF1565C0)
        else -> Color(0xFFE65100)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("NC ID: ${nc["NC ID"] ?: "N/A"}", fontWeight = FontWeight.Black)
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text(status, color = statusColor, fontWeight = FontWeight.Bold) },
                            modifier = Modifier.height(24.dp)
                        )
                        Text("$severity · $age days", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    DetailSection(title = "Location") {
                        DetailField(label = "Project / Phase", value = "${nc["Project"]} / ${nc["Phase"]}")
                        DetailField(label = "Tower / Floor / Flat", value = "${nc["Tower"]} / ${nc["Floor"]} / ${nc["Flat"]}")
                        DetailField(label = "Specific Location", value = nc["Location"])
                    }
                }

                item {
                    DetailSection(title = "Details") {
                        DetailField(label = "Contractor", value = nc["Contractor Name"])
                        DetailField(label = "Activity", value = nc["Activity Name"])
                        DetailField(label = "Description", value = nc["NC"], fullWidth = true)
                        DetailField(label = "Comments", value = nc["NC Comments"], fullWidth = true)
                    }
                }

                item {
                    DetailSection(title = "Cause & Actions") {
                        DetailField(label = "Occurrence / Reason", value = "${nc["Nature Of Occurance"]} / ${nc["Reason"]}")
                        DetailField(label = "Root Cause", value = nc["Root Cause"], fullWidth = true)
                        DetailField(label = "Corrective Action", value = nc["Corrective Action"], fullWidth = true)
                        DetailField(label = "Preventive Action", value = nc["Preventive Action"], fullWidth = true)
                    }
                }

                item {
                    DetailSection(title = "People & Dates") {
                        DetailField(label = "Created By / Date", value = "${nc["NC created By"]} on ${nc["Created On"]}")
                        DetailField(label = "Updated By / Date", value = "${nc["Updated By"]} on ${nc["Updated On"]}")
                        DetailField(label = "Tower Incharge", value = nc["TowerIncharge"])
                        DetailField(label = "Complianced By", value = nc["Complianced By"])
                    }
                }

                item {
                    DetailSection(title = "Classification") {
                        DetailField(label = "CTQ / CTQ Type", value = "${nc["CTQ"]} / ${nc["CTQ Type"]}")
                        DetailField(label = "GPL Promis", value = nc["GPL Promis"])
                    }
                }

                // Photos & Links
                val photoFields = listOf(
                    "NC Photo 1", "NC Photo 2", "NC Photo 3",
                    "Compliance Photo 1", "Compliance Photo 2", "Compliance Photo 3"
                )
                val links = photoFields.mapNotNull { key ->
                    val url = nc[key] ?: ""
                    if (url.startsWith("http")) Pair(key, url) else null
                }

                if (links.isNotEmpty()) {
                    item {
                        DetailSection(title = "NC Photos & Attachments") {
                            links.forEach { link ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenUrl(link.second) }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        link.first,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

@Composable
fun DetailSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun DetailField(label: String, value: String?, fullWidth: Boolean = false) {
    val displayValue = if (value.isNullOrBlank()) "—" else value.trim()
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        Text(displayValue, style = MaterialTheme.typography.bodyMedium)
    }
}

// Simple FlowRow helper to layout items horizontally, wrapping to new lines when needed
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.layout.Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val layoutWidth = constraints.maxWidth
        val rows = mutableListOf<List<androidx.compose.ui.layout.Placeable>>()
        var currentRow = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var currentRowWidth = 0

        val hSpacing = horizontalArrangement.spacing.roundToPx()
        val vSpacing = verticalArrangement.spacing.roundToPx()

        placeables.forEach { placeable ->
            if (currentRowWidth + placeable.width > layoutWidth && currentRow.isNotEmpty()) {
                rows.add(currentRow)
                currentRow = mutableListOf()
                currentRowWidth = 0
            }
            currentRow.add(placeable)
            currentRowWidth += placeable.width + hSpacing
        }
        if (currentRow.isNotEmpty()) {
            rows.add(currentRow)
        }

        var totalHeight = 0
        rows.forEachIndexed { index, row ->
            val rowHeight = row.maxOf { it.height }
            totalHeight += rowHeight
            if (index < rows.size - 1) {
                totalHeight += vSpacing
            }
        }

        layout(layoutWidth, maxOf(0, totalHeight)) {
            var y = 0
            rows.forEach { row ->
                var x = 0
                val rowHeight = row.maxOf { it.height }
                row.forEach { placeable ->
                    placeable.place(x, y + (rowHeight - placeable.height) / 2)
                    x += placeable.width + hSpacing
                }
                y += rowHeight + vSpacing
            }
        }
    }
}

// ── Quad Helper ──────────────────────────────────────────────────────────
private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun AgeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.height(28.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    )
}
