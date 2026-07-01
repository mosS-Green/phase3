package com.phase3.tracker.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import com.phase3.tracker.ui.captureAndShareScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phase3.tracker.viewmodel.PMQHourMetrics
import com.phase3.tracker.viewmodel.QSIMetrics
import com.phase3.tracker.viewmodel.QSIViewModel
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QSIDashboardScreen(
    qsiViewModel: QSIViewModel,
    onNavigateToDetail: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val botToken by qsiViewModel.botToken.collectAsStateWithLifecycle()
    val chatId by qsiViewModel.chatId.collectAsStateWithLifecycle()
    val selectedMonth by qsiViewModel.selectedMonth.collectAsStateWithLifecycle()
    val selectedYear by qsiViewModel.selectedYear.collectAsStateWithLifecycle()
    val fileName by qsiViewModel.fileName.collectAsStateWithLifecycle()
    val isLoading by qsiViewModel.isLoading.collectAsStateWithLifecycle()
    val statusMessage by qsiViewModel.statusMessage.collectAsStateWithLifecycle()
    val metrics by qsiViewModel.metrics.collectAsStateWithLifecycle()

    var showConfigDialog by remember { mutableStateOf(false) }
    var showMonthSelector by remember { mutableStateOf(false) }
    var showRigDialog by remember { mutableStateOf(false) }

    // Auto open config on first launch if not configured
    LaunchedEffect(Unit) {
        if (!qsiViewModel.isTelegramConfigured) {
            showConfigDialog = true
        }
    }

    // Snackbar for status notifications
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusMessage) {
        if (!statusMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(statusMessage!!)
            qsiViewModel.clearStatusMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QSi Dashboard", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            captureAndShareScreen(context, view, "QSI_Dashboard_${metrics?.currMONName ?: ""}")
                        }
                    }) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Capture Dashboard")
                    }
                    IconButton(onClick = { showMonthSelector = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Change Period / Source")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (metrics == null) {
                EmptyStateView(onLoadClick = { showMonthSelector = true }, isLoading = isLoading)
            } else {
                DashboardContent(
                    metrics = metrics!!,
                    qsiViewModel = qsiViewModel,
                    onNavigateToDetail = onNavigateToDetail,
                    onShowRigDetails = { showRigDialog = true }
                )
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator()
                            Text("Loading Data...", fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }

    // ── Dialogs & Bottom Sheets ──────────────────────────────────────────

    if (showConfigDialog) {
        TelegramConfigDialog(
            initialBotToken = botToken,
            initialChatId = chatId,
            onDismiss = { showConfigDialog = false },
            onSave = { token, chat ->
                qsiViewModel.saveTelegramConfig(token, chat)
                showConfigDialog = false
            }
        )
    }

    if (showMonthSelector) {
        MonthSelectorBottomSheet(
            qsiViewModel = qsiViewModel,
            selectedMonth = selectedMonth,
            selectedYear = selectedYear,
            onDismiss = { showMonthSelector = false },
            onConfigureTelegram = {
                showMonthSelector = false
                showConfigDialog = true
            }
        )
    }

    if (showRigDialog && metrics != null) {
        RedIsGoodDetailsDialog(
            metrics = metrics!!,
            onDismiss = { showRigDialog = false }
        )
    }
}

// ── Composable Sub-views ──────────────────────────────────────────────

@Composable
fun EmptyStateView(onLoadClick: () -> Unit, isLoading: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Analytics,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "QSi Dashboard",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Load a quality NC report spreadsheet from Telegram to view Quality Status Indicators.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onLoadClick,
            enabled = !isLoading,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Icon(Icons.Default.CloudDownload, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Load Report File")
        }
    }
}

@Composable
fun DashboardContent(
    metrics: QSIMetrics,
    qsiViewModel: QSIViewModel,
    onNavigateToDetail: (String) -> Unit,
    onShowRigDetails: () -> Unit
) {
    val pmQHourData by qsiViewModel.pmQHourData.collectAsStateWithLifecycle()
    val pmQHourHolidays by qsiViewModel.pmQHourHolidays.collectAsStateWithLifecycle()
    val tncp by qsiViewModel.tncp.collectAsStateWithLifecycle()
    val fncp by qsiViewModel.fncp.collectAsStateWithLifecycle()

    val pmMetrics = qsiViewModel.calculatePMQHourMetrics(metrics)

    // QSI Input Score formula: ((RiG*5)+(D*5)+(P*5)+(Qh*10)+(SV*5))/30
    val qsiInputScore = ((metrics.ratingRig * 5) +
            (metrics.ratingDiscipline * 5) +
            (metrics.ratingPromptness * 5) +
            (pmMetrics.rating * 10) +
            (metrics.ratingSequence * 5)) / 30f

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Summary Header Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "REPORTING PERIOD",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${metrics.currMONName} (26 ${metrics.prevMONShort} – 25 ${metrics.currMONShort})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "QSI INPUT SCORE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        String.format("%.2f / 5", qsiInputScore),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Report: ${qsiViewModel.fileName.value}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Panel 1: Red is Good
        item {
            RedIsGoodPanel(metrics, onShowRigDetails)
        }

        // Panel 2: Discipline
        item {
            DisciplinePanel(
                metrics = metrics,
                tncp = tncp,
                fncp = fncp,
                onTncpChange = { qsiViewModel.setTncp(it) },
                onFncpChange = { qsiViewModel.setFncp(it) },
                onNavigateToDetail = onNavigateToDetail
            )
        }

        // Panel 3: Promptness
        item {
            PromptnessPanel(metrics)
        }

        // Panel 4: PM Q-hour
        item {
            PMQHourPanel(
                pmMetrics = pmMetrics,
                holidays = pmQHourHolidays,
                onHolidaysChange = { qsiViewModel.setPmQHourHolidays(it) },
                onRefresh = { qsiViewModel.refreshPMQHour() }
            )
        }

        // Panel 5: Sequence Violation
        item {
            MetricValuePanel(
                title = "5. Sequence Violation",
                count = metrics.sequenceViolationCount,
                rating = metrics.ratingSequence,
                subtitle = "Sequence Violations Detected",
                desc = "Matches description column for 'sequence violation'",
                themeColor = Color(0xFFB26A00)
            )
        }

        // Panel 6: Open NCs
        item {
            MetricValuePanel(
                title = "Open NCs",
                count = metrics.openNCsCount,
                rating = -1, // No rating for Open NCs
                subtitle = "Total Open NCs",
                desc = "All 'Added' & 'Rejected' status NCs",
                themeColor = Color(0xFFE65100),
                onClick = { onNavigateToDetail("open-ncs") }
            )
        }
    }
}

// ── Panel Composable Subcomponents ───────────────────────────────────────

@Composable
fun RatingBox(rating: Float, maxRating: Int = 5) {
    val color = ratingColor(rating.toInt())
    val gradient = Brush.linearGradient(
        colors = listOf(color.copy(alpha = 0.08f), color.copy(alpha = 0.18f))
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(gradient)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("RATING", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text(
            if (rating == rating.toInt().toFloat()) "${rating.toInt()}/$maxRating" else "$rating/$maxRating",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun RedIsGoodPanel(metrics: QSIMetrics, onClick: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFFC62828),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("1. Red is Good", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = {
                            scope.launch {
                                captureAndShareScreen(context, view, "QSI_RedIsGood")
                            }
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Capture Panel", modifier = Modifier.size(16.dp))
                    }
                }
                SuggestionChip(
                    onClick = {},
                    label = { Text("Target: 450", fontWeight = FontWeight.Bold, color = Color(0xFFC62828)) },
                    colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color(0xFFFFEBEE))
                )
            }

            RatingBox(rating = metrics.ratingRig.toFloat())

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${metrics.panel1Total}", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                    Text("TOTAL NCs CREATED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(modifier = Modifier.width(1.dp).height(50.dp).background(MaterialTheme.colorScheme.outlineVariant))
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    val balanceColor = if (metrics.panel1Balance >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                    Text(
                        if (metrics.panel1Balance >= 0) "+${metrics.panel1Balance}" else "${metrics.panel1Balance}",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = balanceColor
                    )
                    Text("BALANCE REMAINING", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Progress Bar
            val pct = minOf(1f, metrics.panel1Total.toFloat() / 450f)
            LinearProgressIndicator(
                progress = pct,
                color = Color(0xFFC62828),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
            )

            // Dynamic Week Breakdown Expandable
            var expanded by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Week-wise Breakdown", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    metrics.panel1Weeks.forEachIndexed { idx, w ->
                        val isLast = idx == metrics.panel1Weeks.size - 1
                        val metTarget = w.count >= w.weekTarget
                        val countColor = if (isLast) MaterialTheme.colorScheme.onSurface else if (metTarget) Color(0xFF1B5E20) else Color(0xFFC62828)
                        val bgColor = if (w.isPassed) {
                            if (metTarget) Color(0xFF1B5E20).copy(alpha = 0.06f) else Color(0xFFC62828).copy(alpha = 0.06f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        }

                        val statusIcon = when {
                            w.isPassed -> if (metTarget) "✓" else "✗"
                            w.isCurrent -> "●"
                            else -> ""
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(bgColor)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "$statusIcon ${w.label}${if (isLast) " (Buffer)" else ""}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(w.range, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${w.count}", fontWeight = FontWeight.Bold, color = countColor, style = MaterialTheme.typography.bodyLarge)
                                if (!isLast) {
                                    Text(" / ${w.weekTarget}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
fun DisciplinePanel(
    metrics: QSIMetrics,
    tncp: Int,
    fncp: Int,
    onTncpChange: (Int) -> Unit,
    onFncpChange: (Int) -> Unit,
    onNavigateToDetail: (String) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Gavel,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("2. Discipline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = {
                        scope.launch {
                            captureAndShareScreen(context, view, "QSI_Discipline")
                        }
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Capture Panel", modifier = Modifier.size(16.dp))
                }
            }
            Text(
                "NC approvals for issues created on/before 25th of ${metrics.prevMONName}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            RatingBox(rating = metrics.ratingDiscipline.toFloat())

            // Overall stats
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onNavigateToDetail("discipline") }
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("OVERALL NC APPROVALS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${metrics.panel2Approved} / ${metrics.panel2Total}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Text("${metrics.panel2Percent}%", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = Color(0xFF2E7D32))
            }

            // Fatal & Critical stats
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("FATAL & CRITICAL ONLY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${metrics.panel2FatalCriticalApproved} / ${metrics.panel2FatalCriticalTotal}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Text("${metrics.panel2FatalCriticalPercent}%", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.error)
            }

            // TNCP / FNCP adjustments steppers
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("MANUAL TNCP / FNCP ADJUSTMENTS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        StepperInput(label = "TNCP", value = tncp, onValueChange = onTncpChange, modifier = Modifier.weight(1f))
                        StepperInput(label = "FNCP", value = fncp, onValueChange = onFncpChange, modifier = Modifier.weight(1f))
                    }
                }
            }

            // Compliance detail navigates
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onNavigateToDetail("compliance") }
                    .background(Color(0xFF1565C0).copy(alpha = 0.08f))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF1565C0))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("COMPLIANCE NCs", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                        Text("Ready for inspection review", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("${metrics.complianceCount}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
            }
        }
    }
}

@Composable
fun PromptnessPanel(metrics: QSIMetrics) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("3. Promptness", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = {
                        scope.launch {
                            captureAndShareScreen(context, view, "QSI_Promptness")
                        }
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Capture Panel", modifier = Modifier.size(16.dp))
                }
            }

            RatingBox(rating = metrics.ratingPromptness)

            AgeBracketRow(label = "Age > 30 days", count = metrics.countAgeGt30, total = metrics.panel3Total, color = Color(0xFFB71C1C))
            AgeBracketRow(label = "15 < Age <= 30 days", count = metrics.countAge15To30, total = metrics.panel3Total, color = Color(0xFFE65100))
            AgeBracketRow(label = "7 < Age <= 15 days", count = metrics.countAge7To15, total = metrics.panel3Total, color = Color(0xFFF57F17))
            AgeBracketRow(label = "Age <= 7 days", count = metrics.countAgeLt7, total = metrics.panel3Total, color = Color(0xFF1B5E20))

            if (metrics.topAges.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("TOP AGEING OPEN NCs", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                }
                metrics.topAges.forEach { ageGroup ->
                    val ageColor = if (ageGroup.age >= 26) Color(0xFFB71C1C) else Color(0xFFE65100)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ageColor.copy(alpha = 0.08f))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${ageGroup.age} days", fontWeight = FontWeight.Bold, color = ageColor)
                        Text("${ageGroup.count} NCs", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun PMQHourPanel(
    pmMetrics: PMQHourMetrics,
    holidays: Int,
    onHolidaysChange: (Int) -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = Color(0xFF6A1B9A),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("4. PM Q-hour", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = {
                            scope.launch {
                                captureAndShareScreen(context, view, "QSI_PMQHour")
                            }
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Capture Panel", modifier = Modifier.size(16.dp))
                    }
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh API Data", tint = Color(0xFF6A1B9A))
                }
            }

            if (!pmMetrics.fetched) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.CloudOff, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("API data not loaded yet", fontWeight = FontWeight.Medium)
                    if (pmMetrics.error != null) {
                        Text(pmMetrics.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                RatingBox(rating = pmMetrics.rating.toFloat())

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(12.dp)
                    ) {
                        Text("VISITS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${pmMetrics.visits} / ${pmMetrics.effectiveWorkingDays}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${pmMetrics.visitPercent}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (pmMetrics.visitPercent >= 90) Color(0xFF2E7D32) else Color(0xFFB71C1C))
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(12.dp)
                    ) {
                        Text("APPROVED WIRs", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${pmMetrics.approvedWir} / ${String.format("%.1f", pmMetrics.proRataWir)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${pmMetrics.wirPercent}% of pro rata", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (pmMetrics.wirPercent >= 100) Color(0xFF2E7D32) else Color(0xFFB71C1C))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Holidays / Leaves", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text("Subtracted from working days", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = { onHolidaysChange(holidays - 1) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrement")
                        }
                        Text("$holidays", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = { onHolidaysChange(holidays + 1) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Add, contentDescription = "Increment")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricValuePanel(
    title: String,
    count: Int,
    rating: Int,
    subtitle: String,
    desc: String,
    themeColor: Color,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = themeColor)
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = {
                            scope.launch {
                                captureAndShareScreen(context, view, title.replace(Regex("[^a-zA-Z0-9]"), "_"))
                            }
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Capture Panel", modifier = Modifier.size(16.dp))
                    }
                }
                if (onClick != null) {
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = themeColor)
                }
            }

            if (rating > 0) {
                RatingBox(rating = rating.toFloat())
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("$count", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black, color = themeColor)
                Text(subtitle, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ── Helper Views ─────────────────────────────────────────────────────────

@Composable
fun AgeBracketRow(label: String, count: Int, total: Int, color: Color) {
    val pct = if (total > 0) Math.round((count.toFloat() / total.toFloat()) * 100) else 0
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("$count ($pct%)", fontWeight = FontWeight.Bold, color = color)
        }
        LinearProgressIndicator(
            progress = if (total > 0) count.toFloat() / total.toFloat() else 0f,
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
        )
    }
}

@Composable
fun StepperInput(label: String, value: Int, onValueChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    var textValue by remember(value) { mutableStateOf(value.toString()) }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onValueChange(value - 1) }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = textValue,
                    onValueChange = { newValue ->
                        val filtered = newValue.filter { it.isDigit() }
                        textValue = filtered
                        val intVal = filtered.toIntOrNull()
                        if (intVal != null) {
                            onValueChange(intVal)
                        } else if (filtered.isEmpty()) {
                            onValueChange(0)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = LocalTextStyle.current.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            IconButton(onClick = { onValueChange(value + 1) }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

fun ratingColor(r: Int): Color {
    return when {
        r >= 5 -> Color(0xFF1B5E20)
        r >= 4 -> Color(0xFF2E7D32)
        r >= 3 -> Color(0xFFE65100)
        r >= 2 -> Color(0xFFF57F17)
        else -> Color(0xFFB71C1C)
    }
}

// ── Configuration & Selector Dialogs/Sheets ─────────────────────────────

@Composable
fun TelegramConfigDialog(
    initialBotToken: String,
    initialChatId: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var botToken by remember { mutableStateOf(initialBotToken) }
    var chatId by remember { mutableStateOf(initialChatId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure Telegram Bot", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Bot will fetch the pinned Excel/JSON report from the chat.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = botToken,
                    onValueChange = { botToken = it },
                    label = { Text("Bot Token") },
                    placeholder = { Text("123456:ABC-DEF...") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = chatId,
                    onValueChange = { chatId = it },
                    label = { Text("Chat ID") },
                    placeholder = { Text("-100123456789") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(botToken, chatId) },
                enabled = botToken.isNotBlank() && chatId.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthSelectorBottomSheet(
    qsiViewModel: QSIViewModel,
    selectedMonth: Int,
    selectedYear: Int,
    onDismiss: () -> Unit,
    onConfigureTelegram: () -> Unit
) {
    val months = arrayOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val years = (currentYear - 2..currentYear + 1).toList()

    var tempMonth by remember { mutableIntStateOf(selectedMonth) }
    var tempYear by remember { mutableIntStateOf(selectedYear) }

    var expandedMonth by remember { mutableStateOf(false) }
    var expandedYear by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Period Configuration", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Month Picker
                Box(modifier = Modifier.weight(1.5f)) {
                    OutlinedButton(onClick = { expandedMonth = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(months[tempMonth])
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = expandedMonth, onDismissRequest = { expandedMonth = false }) {
                        months.forEachIndexed { index, m ->
                            DropdownMenuItem(text = { Text(m) }, onClick = {
                                tempMonth = index
                                expandedMonth = false
                            })
                        }
                    }
                }
                // Year Picker
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(onClick = { expandedYear = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("$tempYear")
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = expandedYear, onDismissRequest = { expandedYear = false }) {
                        years.forEach { y ->
                            DropdownMenuItem(text = { Text("$y") }, onClick = {
                                tempYear = y
                                expandedYear = false
                            })
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    qsiViewModel.setMonth(tempMonth)
                    qsiViewModel.setYear(tempYear)
                    qsiViewModel.loadFromTelegram()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Load from Telegram")
            }

            TextButton(
                onClick = onConfigureTelegram,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.SettingsInputComponent, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Edit Bot Credentials")
            }
        }
    }
}

@Composable
fun RedIsGoodDetailsDialog(metrics: QSIMetrics, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val creatorGroups = remember(metrics.panel1Rows) {
        metrics.panel1Rows
            .groupBy { it["NC created By"] ?: it["Updated By"] ?: "Unknown" }
            .map { Pair(it.key, it.value.size) }
            .sortedByDescending { it.second }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Red is Good Summary", fontWeight = FontWeight.Bold)
                IconButton(onClick = {
                    scope.launch {
                        captureAndShareScreen(context, view, "QSI_RedIsGood_Summary")
                    }
                }) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Capture Dialog")
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                Text(
                    "Total NCs created in current period: ${metrics.panel1Total}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(creatorGroups) { idx, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("#${idx + 1}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(24.dp))
                                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(item.first, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(0.7f))
                            }
                            Text("${item.second}", fontWeight = FontWeight.Black, color = Color(0xFFC62828))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
