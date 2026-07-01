package com.phase3.tracker.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.concurrent.TimeUnit

// ── Data Classes ────────────────────────────────────────────────────────

data class PMQHourApiData(
    val visits: Int,
    val approvedWir: Int,
    val fetched: Boolean,
    val error: String? = null
)

data class WeekData(
    val label: String,
    val range: String,
    val count: Int,
    var weekTarget: Int,
    var isPassed: Boolean,
    var isCurrent: Boolean,
    val weekStart: Date,
    val weekEnd: Date
)

data class AgeGroup(val age: Int, val count: Int)

data class QSIMetrics(
    val currMONName: String,
    val prevMONName: String,
    val currMONShort: String,
    val prevMONShort: String,
    
    // Panel 1: Red is Good
    val panel1Total: Int,
    val panel1Balance: Int,
    val panel1Weeks: List<WeekData>,
    val panel1Rows: List<Map<String, String>>,
    val ratingRig: Int,

    // Panel 2: Discipline
    val panel2Total: Int,
    val panel2Approved: Int,
    val panel2Percent: Int,
    val panel2FatalCriticalTotal: Int,
    val panel2FatalCriticalApproved: Int,
    val panel2FatalCriticalPercent: Int,
    val ratingDiscipline: Int,

    // Panel 3: Promptness
    val panel3Total: Int,
    val countAgeGt30: Int,
    val countAge15To30: Int,
    val countAge7To15: Int,
    val countAgeLt7: Int,
    val ratingPromptness: Float,
    val topAges: List<AgeGroup>,

    // Panel 5: Sequence Violation
    val sequenceViolationCount: Int,
    val ratingSequence: Int,

    // Panel 6: Open NCs
    val openNCsCount: Int,
    val openNCsData: List<Map<String, String>>,

    // Compliance
    val complianceCount: Int,
    val complianceNCs: List<Map<String, String>>,

    // Detail Screen Data
    val panel2DetailNCs: List<Map<String, String>>
)

data class PMQHourMetrics(
    val rating: Int,
    val visits: Int,
    val approvedWir: Int,
    val effectiveWorkingDays: Int,
    val workingDaysConsidered: Int,
    val totalWorkingDaysFull: Int,
    val proRataWir: Float,
    val visitPercent: Int,
    val wirPercent: Int,
    val holidays: Int,
    val fetched: Boolean,
    val error: String?,
    val consideredPeriod: String,
    val fullPeriod: String
)

// ── QSIViewModel ────────────────────────────────────────────────────────

class QSIViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("qsi_prefs", Context.MODE_PRIVATE)

    // ── Telegram Configuration ──────────────────────────────────────────
    private val _botToken = MutableStateFlow(prefs.getString("bot_token", "") ?: "")
    val botToken: StateFlow<String> = _botToken.asStateFlow()

    private val _chatId = MutableStateFlow(prefs.getString("chat_id", "") ?: "")
    val chatId: StateFlow<String> = _chatId.asStateFlow()

    fun saveTelegramConfig(botToken: String, chatId: String) {
        _botToken.value = botToken.trim()
        _chatId.value = chatId.trim()
        prefs.edit()
            .putString("bot_token", _botToken.value)
            .putString("chat_id", _chatId.value)
            .apply()
    }

    val isTelegramConfigured: Boolean
        get() = _botToken.value.isNotBlank() && _chatId.value.isNotBlank()

    // ── Month/Year Selection ─────────────────────────────────────────────
    private val _selectedMonth = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH)) // 0-11
    val selectedMonth: StateFlow<Int> = _selectedMonth.asStateFlow()

    private val _selectedYear = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    val selectedYear: StateFlow<Int> = _selectedYear.asStateFlow()

    fun setMonth(m: Int) {
        _selectedMonth.value = m
        recalculateMetrics()
    }

    fun setYear(y: Int) {
        _selectedYear.value = y
        recalculateMetrics()
    }

    // ── Parsed NC Data ───────────────────────────────────────────────────
    private val _ncData = MutableStateFlow<List<Map<String, String>>>(emptyList())
    val ncData: StateFlow<List<Map<String, String>>> = _ncData.asStateFlow()

    private val _fileName = MutableStateFlow("")
    val fileName: StateFlow<String> = _fileName.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // ── PM Q-hour API Data & Local Overrides ─────────────────────────────
    private val _pmQHourData = MutableStateFlow<PMQHourApiData?>(null)
    val pmQHourData: StateFlow<PMQHourApiData?> = _pmQHourData.asStateFlow()

    private val _pmQHourHolidays = MutableStateFlow(prefs.getInt("pmqhour_holidays", 0))
    val pmQHourHolidays: StateFlow<Int> = _pmQHourHolidays.asStateFlow()

    fun setPmQHourHolidays(v: Int) {
        _pmQHourHolidays.value = maxOf(0, v)
        prefs.edit().putInt("pmqhour_holidays", _pmQHourHolidays.value).apply()
    }

    private val _tncp = MutableStateFlow(prefs.getInt("tncp", 0))
    val tncp: StateFlow<Int> = _tncp.asStateFlow()

    fun setTncp(v: Int) {
        _tncp.value = maxOf(0, v)
        prefs.edit().putInt("tncp", _tncp.value).apply()
        recalculateMetrics()
    }

    private val _fncp = MutableStateFlow(prefs.getInt("fncp", 0))
    val fncp: StateFlow<Int> = _fncp.asStateFlow()

    fun setFncp(v: Int) {
        _fncp.value = maxOf(0, v)
        prefs.edit().putInt("fncp", _fncp.value).apply()
        recalculateMetrics()
    }

    // ── Calculated Metrics ────────────────────────────────────────────────
    private val _metrics = MutableStateFlow<QSIMetrics?>(null)
    val metrics: StateFlow<QSIMetrics?> = _metrics.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // ── Telegram Bot Fetching ────────────────────────────────────────────
    fun loadFromTelegram() {
        if (!isTelegramConfigured) {
            _statusMessage.value = "Configure Telegram Bot first"
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _statusMessage.value = "Fetching pinned message..."
            try {
                val fileInfo = withContext(Dispatchers.IO) {
                    // 1. getChat to find pinned message
                    val chatUrl = "https://api.telegram.org/bot${_botToken.value}/getChat?chat_id=${_chatId.value}"
                    val chatReq = Request.Builder().url(chatUrl).build()
                    val chatRes = client.newCall(chatReq).execute()
                    val chatBody = chatRes.body?.string() ?: throw Exception("Empty response from getChat")
                    val chatJson = JSONObject(chatBody)

                    if (!chatJson.optBoolean("ok", false)) {
                        throw Exception(chatJson.optString("description", "getChat failed"))
                    }

                    val resultObj = chatJson.optJSONObject("result") ?: throw Exception("No chat result found")
                    val pinnedMsg = resultObj.optJSONObject("pinned_message")
                        ?: throw Exception("No pinned message found in this chat")

                    val document = pinnedMsg.optJSONObject("document")
                        ?: throw Exception("Pinned message is not a document file")

                    val fileId = document.getString("file_id")
                    val docFileName = document.optString("file_name", "report.xlsx")

                    // 2. getFile to get the download path
                    val fileUrl = "https://api.telegram.org/bot${_botToken.value}/getFile?file_id=$fileId"
                    val fileReq = Request.Builder().url(fileUrl).build()
                    val fileRes = client.newCall(fileReq).execute()
                    val fileBody = fileRes.body?.string() ?: throw Exception("Empty response from getFile")
                    val fileJson = JSONObject(fileBody)

                    if (!fileJson.optBoolean("ok", false)) {
                        throw Exception(fileJson.optString("description", "getFile failed"))
                    }

                    val filePath = fileJson.getJSONObject("result").getString("file_path")
                    Pair(docFileName, filePath)
                }

                val (docFileName, filePath) = fileInfo
                _statusMessage.value = "Downloading file..."

                val bytes = withContext(Dispatchers.IO) {
                    val downloadUrl = "https://api.telegram.org/file/bot${_botToken.value}/$filePath"
                    val dlReq = Request.Builder().url(downloadUrl).build()
                    val dlRes = client.newCall(dlReq).execute()
                    dlRes.body?.bytes() ?: throw Exception("File download returned empty content")
                }

                _fileName.value = docFileName

                val rows = withContext(Dispatchers.IO) {
                    if (docFileName.lowercase().endsWith(".json")) {
                        parseJsonFile(bytes)
                    } else {
                        parseExcelFile(bytes)
                    }
                }

                _ncData.value = rows
                recalculateMetrics()
                _statusMessage.value = "Loaded ${rows.size} records from $docFileName"

                // Auto-refresh PM Q-hour statistics
                refreshPMQHour()

            } catch (e: Exception) {
                _statusMessage.value = "Load failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ── Excel and JSON Parsers ───────────────────────────────────────────
    private fun parseJsonFile(bytes: ByteArray): List<Map<String, String>> {
        val text = String(bytes, Charsets.UTF_8)
        val arr = JSONArray(text)
        val list = mutableListOf<Map<String, String>>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val map = mutableMapOf<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = obj.optString(key, "")
            }
            list.add(map)
        }
        return list
    }

    private fun parseExcelFile(bytes: ByteArray): List<Map<String, String>> {
        val workbook = org.apache.poi.xssf.usermodel.XSSFWorkbook(ByteArrayInputStream(bytes))
        val sheet = workbook.getSheetAt(0)
        val rows = mutableListOf<Map<String, String>>()

        // Find header row (heuristic search in first 15 rows)
        var headerRowIdx = 0
        for (i in 0 until minOf(15, sheet.lastRowNum + 1)) {
            val row = sheet.getRow(i) ?: continue
            val hasKeywords = (0 until row.lastCellNum).any { c ->
                val text = getCellStringValue(row.getCell(c)).lowercase()
                text.contains("nc id") || text.contains("created on") || text.contains("status")
            }
            if (hasKeywords) {
                headerRowIdx = i
                break
            }
        }

        val headerRow = sheet.getRow(headerRowIdx) ?: return emptyList()
        val headers = (0 until headerRow.lastCellNum).map { c ->
            getCellStringValue(headerRow.getCell(c)).trim()
        }

        val photoCols = setOf(
            "NC Photo 1", "NC Photo 2", "NC Photo 3",
            "Compliance Photo 1", "Compliance Photo 2", "Compliance Photo 3"
        )

        for (i in (headerRowIdx + 1)..sheet.lastRowNum) {
            val row = sheet.getRow(i) ?: continue
            val map = mutableMapOf<String, String>()
            var hasData = false

            headers.forEachIndexed { colIdx, header ->
                if (header.isNotBlank()) {
                    val cell = row.getCell(colIdx)
                    var value = getCellStringValue(cell)

                    // Extract hyperlink from formula if it's a photo column
                    if (header in photoCols && cell?.cellType == org.apache.poi.ss.usermodel.CellType.FORMULA) {
                        try {
                            val formula = cell.cellFormula ?: ""
                            val match = Regex("""HYPERLINK\("([^"]+)"\)""", RegexOption.IGNORE_CASE).find(formula)
                            if (match != null) {
                                value = match.groupValues[1]
                            }
                        } catch (_: Exception) {}
                    }
                    map[header] = value
                    if (value.isNotBlank()) hasData = true
                }
            }
            if (hasData) {
                rows.add(map)
            }
        }
        workbook.close()
        return rows
    }

    private fun getCellStringValue(cell: org.apache.poi.ss.usermodel.Cell?): String {
        if (cell == null) return ""
        return when (cell.cellType) {
            org.apache.poi.ss.usermodel.CellType.STRING -> cell.stringCellValue ?: ""
            org.apache.poi.ss.usermodel.CellType.NUMERIC -> {
                if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                    val sdf = SimpleDateFormat("M/d/yyyy", Locale.US)
                    sdf.format(cell.dateCellValue)
                } else {
                    val num = cell.numericCellValue
                    if (num == num.toLong().toDouble()) num.toLong().toString() else num.toString()
                }
            }
            org.apache.poi.ss.usermodel.CellType.BOOLEAN -> cell.booleanCellValue.toString()
            org.apache.poi.ss.usermodel.CellType.FORMULA -> {
                try { cell.stringCellValue ?: "" } catch (_: Exception) {
                    try { cell.numericCellValue.toString() } catch (_: Exception) { "" }
                }
            }
            else -> ""
        }
    }

    // ── Metric Computations ──────────────────────────────────────────────
    private fun parseDateStr(str: String?): Date? {
        if (str.isNullOrBlank()) return null
        val cleaned = str.trim()
        val parts = cleaned.split("/")
        if (parts.size == 3) {
            val p0 = parts[0].toIntOrNull() ?: return null
            val p1 = parts[1].toIntOrNull() ?: return null
            val p2 = parts[2].toIntOrNull() ?: return null
            // Heuristic matching from JS
            return if (p0 > 12) {
                // Day/Month/Year
                GregorianCalendar(p2, p1 - 1, p0).time
            } else {
                // Month/Day/Year
                GregorianCalendar(p2, p0 - 1, p1).time
            }
        }
        return try {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(cleaned)
        } catch (_: Exception) {
            try {
                SimpleDateFormat("M/d/yyyy", Locale.US).parse(cleaned)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun safePercent(part: Int, total: Int): Int {
        if (total <= 0) return 0
        if (part >= total) return 100
        val pct = Math.round((part.toFloat() / total.toFloat()) * 100)
        return if (pct >= 100) 99 else pct
    }

    private fun countWorkingDays(fromDate: Date, toDate: Date): Int {
        var count = 0
        val cal = Calendar.getInstance()
        cal.time = fromDate
        val endCal = Calendar.getInstance()
        endCal.time = toDate

        // Set to start of day
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        endCal.set(Calendar.HOUR_OF_DAY, 23)
        endCal.set(Calendar.MINUTE, 59)
        endCal.set(Calendar.SECOND, 59)

        while (cal.before(endCal)) {
            val day = cal.get(Calendar.DAY_OF_WEEK)
            if (day != Calendar.SATURDAY && day != Calendar.SUNDAY) {
                count++
            }
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return count
    }

    private fun recalculateMetrics() {
        val data = _ncData.value
        if (data.isEmpty()) {
            _metrics.value = null
            return
        }

        val year = _selectedYear.value
        val monthIdx = _selectedMonth.value

        var prevYear = year
        var prevMonthIdx = monthIdx - 1
        if (prevMonthIdx < 0) {
            prevMonthIdx = 11
            prevYear--
        }

        val startDate = GregorianCalendar(prevYear, prevMonthIdx, 26, 0, 0, 0).time
        val endDate = GregorianCalendar(year, monthIdx, 25, 23, 59, 59).time
        val prevEndDate = GregorianCalendar(prevYear, prevMonthIdx, 25, 23, 59, 59).time

        val allowedCreators = setOf(
            "Abdul  Mannan", "Md Rahbar Zamir", "Neeraj  Kumar",
            "Nilendra Mishra", "Sandeep Saini", "Ashish Saini",
            "Ankur Saxena", "Kartik Mittal", "Beekam Chandra Yadav"
        )

        var panel1Total = 0
        val panel1NCs = mutableListOf<Date>()
        val panel1Rows = mutableListOf<Map<String, String>>()

        var panel2CreatedBeforePrev = 0
        var panel2ApprovedBeforePrev = 0
        var panel2Approved = 0
        var panel2FCCreatedBeforePrev = 0
        var panel2FCApprovedBeforePrev = 0
        var panel2FCApproved = 0

        var countAgeGt30 = 0
        var countAge15To30 = 0
        var countAge7To15 = 0
        var countAgeLt7 = 0
        var panel3Total = 0

        var sequenceViolationCount = 0
        var complianceCount = 0
        val complianceNCs = mutableListOf<Map<String, String>>()
        var openNCsCount = 0
        val openNCsData = mutableListOf<Map<String, String>>()
        val panel2DetailNCs = mutableListOf<Map<String, String>>()

        data.forEach { row ->
            val createdOnVal = row["Created On"]
            val createdDate = parseDateStr(createdOnVal)
            val updatedOnVal = row["Updated On"]
            val updatedDate = parseDateStr(updatedOnVal)
            val creatorVal = (row["NC created By"] ?: row["Updated By"] ?: "").trim()
            val statusVal = (row["Status"] ?: "").trim()
            val statusLower = statusVal.lowercase()
            val severityVal = (row["NC Severity Name"] ?: row["GPL Severity"] ?: "").trim()
            val severityLower = severityVal.lowercase()
            val ncText = (row["NC"] ?: "").lowercase()

            if (ncText.contains("sequence violation")) {
                sequenceViolationCount++
            }

            if (statusLower == "compliance") {
                complianceCount++
                complianceNCs.add(row)
            }

            if (statusLower == "added" || statusLower.contains("reject")) {
                openNCsCount++
                openNCsData.add(row)
            }

            // Panel 1: Created within current reporting period
            if (createdDate != null && createdDate >= startDate && createdDate <= endDate) {
                if (creatorVal in allowedCreators) {
                    panel1Total++
                    panel1NCs.add(createdDate)
                    panel1Rows.add(row)
                }
            }

            // Panel 2: Discipline (Outstanding/resolved issues created before this period)
            if (createdDate != null && createdDate <= prevEndDate) {
                panel2CreatedBeforePrev++

                val isApproved = statusLower == "approved"
                val isFatalOrCritical = severityLower == "fatal" || severityLower == "critical"

                if (isFatalOrCritical) {
                    panel2FCCreatedBeforePrev++
                }

                if (isApproved && updatedDate != null && updatedDate <= prevEndDate) {
                    panel2ApprovedBeforePrev++
                    if (isFatalOrCritical) {
                        panel2FCApprovedBeforePrev++
                    }
                }

                if (isApproved) {
                    panel2Approved++
                    if (isFatalOrCritical) {
                        panel2FCApproved++
                    }
                }

                if (statusLower == "added" || statusLower == "rejected") {
                    panel2DetailNCs.add(row)
                }
            }

            // Panel 3: Promptness (Issues approved in current period)
            if (updatedDate != null && updatedDate >= startDate && updatedDate <= endDate) {
                if (statusLower == "approved") {
                    panel3Total++
                    val age = row["Age Of NC(Days)"]?.toIntOrNull() ?: 0
                    when {
                        age > 30 -> countAgeGt30++
                        age in 16..30 -> countAge15To30++
                        age in 8..15 -> countAge7To15++
                        else -> countAgeLt7++
                    }
                }
            }
        }

        // Weeks calculation for Panel 1
        val weeks = mutableListOf<WeekData>()
        val weeksCal = Calendar.getInstance()
        weeksCal.time = startDate
        var weekIdx = 1

        while (weeksCal.time.before(endDate)) {
            val weekStart = weeksCal.time
            weeksCal.add(Calendar.DAY_OF_MONTH, 6)
            var weekEnd = weeksCal.time
            if (weekEnd.after(endDate)) {
                weekEnd = endDate
            }
            val formatter = SimpleDateFormat("d MMM", Locale.US)
            val rangeStr = "${formatter.format(weekStart)} – ${formatter.format(weekEnd)}"
            
            val count = panel1NCs.count { it >= weekStart && it <= weekEnd }

            weeks.add(
                WeekData(
                    label = "Week $weekIdx",
                    range = rangeStr,
                    count = count,
                    weekTarget = 0,
                    isPassed = false,
                    isCurrent = false,
                    weekStart = weekStart,
                    weekEnd = weekEnd
                )
            )
            weeksCal.add(Calendar.DAY_OF_MONTH, 1)
            weekIdx++
        }

        // Dynamic weekly targets distribution
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val lastWeekIdx = weeks.size - 1
        var passedWeekTotal = 0

        weeks.forEachIndexed { idx, w ->
            if (idx == lastWeekIdx) {
                w.weekTarget = 0
                w.isPassed = w.weekEnd.before(today)
                w.isCurrent = !w.isPassed && !w.weekStart.after(today)
                return@forEachIndexed
            }
            if (w.weekEnd.before(today)) {
                w.isPassed = true
                w.isCurrent = false
                w.weekTarget = w.count
                passedWeekTotal += w.count
            } else {
                w.isPassed = false
                w.isCurrent = !w.weekStart.after(today)
            }
        }

        val remainingBalance = maxOf(0, 450 - passedWeekTotal)
        val lastWeekStart = if (lastWeekIdx >= 0) weeks[lastWeekIdx].weekStart else endDate
        var totalRemainingDays = 0
        val futureWeekDays = IntArray(weeks.size)

        weeks.forEachIndexed { idx, w ->
            if (idx == lastWeekIdx || w.isPassed) {
                futureWeekDays[idx] = 0
                return@forEachIndexed
            }
            val weekEffectiveStart = if (w.isCurrent) today else w.weekStart
            val diffMs = w.weekEnd.time - weekEffectiveStart.time
            val daysInWeek = maxOf(0, (diffMs / 86400000).toInt() + 1)
            futureWeekDays[idx] = daysInWeek
            totalRemainingDays += daysInWeek
        }

        weeks.forEachIndexed { idx, w ->
            if (idx == lastWeekIdx || w.isPassed) return@forEachIndexed
            w.weekTarget = if (totalRemainingDays > 0) {
                Math.round((remainingBalance.toFloat() / totalRemainingDays.toFloat()) * futureWeekDays[idx])
            } else {
                0
            }
        }

        // Ratings Calculations
        // 1. Red is Good
        val rigDiffMs = endDate.time - startDate.time
        val rigTotalDays = (rigDiffMs / 86400000).toInt() + 1
        val rigYesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }.time
        val rigConsideredEnd = if (rigYesterday.after(endDate)) endDate else rigYesterday
        val rigConsideredDays = maxOf(1, ((rigConsideredEnd.time - startDate.time) / 86400000).toInt() + 1)

        val proRataRig = { target: Int ->
            if (rigTotalDays > 0) (target.toFloat() / rigTotalDays.toFloat()) * rigConsideredDays else 0f
        }

        val ratingRig = when {
            panel1Total >= proRataRig(450) -> 5
            panel1Total >= proRataRig(360) -> 4
            panel1Total >= proRataRig(281) -> 3
            panel1Total >= proRataRig(230) -> 2
            else -> 1
        }

        // 2. Discipline
        val panel2TotalCount = panel2CreatedBeforePrev - panel2ApprovedBeforePrev
        val panel2ApprovedInPeriodCount = panel2Approved - panel2ApprovedBeforePrev
        val panel2FatalCriticalTotalCount = panel2FCCreatedBeforePrev - panel2FCApprovedBeforePrev
        val panel2FatalCriticalApprovedInPeriodCount = panel2FCApproved - panel2FCApprovedBeforePrev

        val adjP2Total = panel2TotalCount + _tncp.value
        val adjP2Approved = panel2ApprovedInPeriodCount + _tncp.value
        val adjP2FCTotal = panel2FatalCriticalTotalCount + _fncp.value
        val adjP2FCApproved = panel2FatalCriticalApprovedInPeriodCount + _fncp.value

        val discOverallPct = safePercent(adjP2Approved, adjP2Total)
        val discFCPct = if (adjP2FCTotal == 0) 100 else safePercent(adjP2FCApproved, adjP2FCTotal)

        val ratingDiscipline = when {
            discOverallPct >= 100 && discFCPct >= 100 -> 5
            discOverallPct >= 98 && discFCPct >= 100 -> 4
            discOverallPct >= 95 && discFCPct >= 100 -> 3
            discOverallPct >= 90 && discFCPct >= 95 -> 2
            else -> 1
        }

        // 3. Promptness
        var ratingPromptness = 1.0f
        if (panel3Total == 0) {
            ratingPromptness = 5.0f
        } else {
            val pLt7 = countAgeLt7.toFloat() / panel3Total.toFloat()
            val pLt15 = (countAgeLt7 + countAge7To15).toFloat() / panel3Total.toFloat()
            val pLt30 = (countAgeLt7 + countAge7To15 + countAge15To30).toFloat() / panel3Total.toFloat()

            ratingPromptness = when {
                pLt15 >= 1.0f -> 4.0f + pLt7
                pLt30 >= 1.0f -> 3.0f
                pLt30 >= 0.8f -> 2.0f
                else -> 1.0f
            }
        }
        val roundedRatingPromptness = Math.round(ratingPromptness * 10f) / 10f

        // 4. Sequence Violation
        val ratingSequence = when (sequenceViolationCount) {
            0 -> 5
            1 -> 4
            2 -> 3
            3 -> 2
            else -> 1
        }

        // Top Ageing Open NCs Warning Grouping
        val ageGroupsMap = mutableMapOf<Int, Int>()
        openNCsData.forEach { row ->
            val age = row["Age Of NC(Days)"]?.toIntOrNull() ?: 0
            ageGroupsMap[age] = (ageGroupsMap[age] ?: 0) + 1
        }
        val topAges = ageGroupsMap.entries
            .map { AgeGroup(it.key, it.value) }
            .sortedByDescending { it.age }
            .take(3)

        // Labels
        val monthsFull = arrayOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
        val monthsShort = arrayOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
        )

        val currMONName = "${monthsFull[monthIdx]} $year"
        val prevMONName = "${monthsFull[prevMonthIdx]} $prevYear"
        val currMONShort = monthsShort[monthIdx]
        val prevMONShort = monthsShort[prevMonthIdx]

        _metrics.value = QSIMetrics(
            currMONName = currMONName,
            prevMONName = prevMONName,
            currMONShort = currMONShort,
            prevMONShort = prevMONShort,
            panel1Total = panel1Total,
            panel1Balance = 450 - panel1Total,
            panel1Weeks = weeks,
            panel1Rows = panel1Rows,
            ratingRig = ratingRig,
            panel2Total = adjP2Total,
            panel2Approved = adjP2Approved,
            panel2Percent = discOverallPct,
            panel2FatalCriticalTotal = adjP2FCTotal,
            panel2FatalCriticalApproved = adjP2FCApproved,
            panel2FatalCriticalPercent = discFCPct,
            ratingDiscipline = ratingDiscipline,
            panel3Total = panel3Total,
            countAgeGt30 = countAgeGt30,
            countAge15To30 = countAge15To30,
            countAge7To15 = countAge7To15,
            countAgeLt7 = countAgeLt7,
            ratingPromptness = roundedRatingPromptness,
            topAges = topAges,
            sequenceViolationCount = sequenceViolationCount,
            ratingSequence = ratingSequence,
            openNCsCount = openNCsCount,
            openNCsData = openNCsData,
            complianceCount = complianceCount,
            complianceNCs = complianceNCs,
            panel2DetailNCs = panel2DetailNCs
        )
    }

    // ── PM Q-Hour API Fetching ───────────────────────────────────────────
    fun refreshPMQHour() {
        if (_isLoading.value) return
        val monthIdx = _selectedMonth.value
        val year = _selectedYear.value

        var prevMonthIdx = monthIdx - 1
        var prevYear = year
        if (prevMonthIdx < 0) {
            prevMonthIdx = 11
            prevYear--
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val fromDate = GregorianCalendar(prevYear, prevMonthIdx, 26).time
                val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }.time
                val fullEnd = GregorianCalendar(year, monthIdx, 25).time
                val toDate = if (yesterday.after(fullEnd)) fullEnd else yesterday

                val formattedFrom = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(fromDate)
                val formattedTo = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(toDate)

                val data = withContext(Dispatchers.IO) {
                    val visitUrl = "$PM_QHOUR_API_BASE/PMCHourVisitData?fromDate=$formattedFrom&toDate=$formattedTo&RegionId=$PM_QHOUR_REGION_ID&ProjectId=$PM_QHOUR_PROJECT_ID&userId=$PM_QHOUR_USER_ID"
                    val wirUrl = "$PM_QHOUR_API_BASE/WirApprovedDashborad?fromDate=$formattedFrom&toDate=$formattedTo&RegionId=$PM_QHOUR_REGION_ID&ProjectId=$PM_QHOUR_PROJECT_ID&userId=$PM_QHOUR_USER_ID"

                    val vReq = Request.Builder().url(visitUrl).build()
                    val wReq = Request.Builder().url(wirUrl).build()

                    val vRes = client.newCall(vReq).execute()
                    val wRes = client.newCall(wReq).execute()

                    val vBody = vRes.body?.string() ?: "{}"
                    val wBody = wRes.body?.string() ?: "{}"

                    val vJson = JSONObject(vBody)
                    val wJson = JSONObject(wBody)

                    val vArr = vJson.optJSONArray("data")
                    val wArr = wJson.optJSONArray("data")

                    val visits = if (vArr != null && vArr.length() > 0) vArr.getJSONObject(0).optInt("count", 0) else 0
                    val approvedWir = if (wArr != null && wArr.length() > 0) wArr.getJSONObject(0).optInt("approvedWirCount", 0) else 0

                    PMQHourApiData(visits, approvedWir, true)
                }

                _pmQHourData.value = data

            } catch (e: Exception) {
                _pmQHourData.value = PMQHourApiData(0, 0, false, e.message)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun calculatePMQHourMetrics(qsiMetrics: QSIMetrics): PMQHourMetrics {
        val monthIdx = _selectedMonth.value
        val year = _selectedYear.value

        var prevMonthIdx = monthIdx - 1
        var prevYear = year
        if (prevMonthIdx < 0) {
            prevMonthIdx = 11
            prevYear--
        }

        val fullPeriodStart = GregorianCalendar(prevYear, prevMonthIdx, 26).time
        val fullPeriodEnd = GregorianCalendar(year, monthIdx, 25).time

        val yesterday = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, -1)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
        }.time

        val consideredEnd = if (yesterday.after(fullPeriodEnd)) fullPeriodEnd else yesterday
        val holidays = _pmQHourHolidays.value

        val totalWorkingDaysFull = countWorkingDays(fullPeriodStart, fullPeriodEnd)
        val workingDaysConsidered = countWorkingDays(fullPeriodStart, consideredEnd)
        val effectiveWorkingDays = maxOf(0, workingDaysConsidered - holidays)

        val apiData = _pmQHourData.value ?: PMQHourApiData(0, 0, false)

        val proRataWir = if (totalWorkingDaysFull > 0) {
            Math.round((PM_QHOUR_QTARGET.toFloat() / totalWorkingDaysFull.toFloat()) * effectiveWorkingDays * 100f) / 100f
        } else {
            0f
        }

        val visitPercent = safePercent(apiData.visits, effectiveWorkingDays)
        val wirPercent = safePercent(apiData.approvedWir, Math.round(proRataWir))

        val proRataFor = { qt: Int ->
            if (totalWorkingDaysFull > 0) (qt.toFloat() / totalWorkingDaysFull.toFloat()) * effectiveWorkingDays else 0f
        }

        var rating = 1
        when {
            visitPercent >= 100 && apiData.approvedWir >= proRataFor(25) -> rating = 5
            visitPercent >= 100 && apiData.approvedWir >= proRataFor(20) -> rating = 4
            visitPercent >= 100 && apiData.approvedWir >= proRataFor(15) -> rating = 3
            visitPercent >= 90 && apiData.approvedWir >= proRataFor(10) -> rating = 2
        }

        val formatter = SimpleDateFormat("d MMM", Locale.US)
        val consideredPeriod = "${formatter.format(fullPeriodStart)} – ${formatter.format(consideredEnd)}"
        val fullPeriod = "${formatter.format(fullPeriodStart)} – ${formatter.format(fullPeriodEnd)}"

        return PMQHourMetrics(
            rating = rating,
            visits = apiData.visits,
            approvedWir = apiData.approvedWir,
            effectiveWorkingDays = effectiveWorkingDays,
            workingDaysConsidered = workingDaysConsidered,
            totalWorkingDaysFull = totalWorkingDaysFull,
            proRataWir = proRataWir,
            visitPercent = visitPercent,
            wirPercent = wirPercent,
            holidays = holidays,
            fetched = apiData.fetched,
            error = apiData.error,
            consideredPeriod = consideredPeriod,
            fullPeriod = fullPeriod
        )
    }

    companion object {
        private const val PM_QHOUR_QTARGET = 25
        private const val PM_QHOUR_API_BASE = "https://quality.godrejproperties.com:8092/api/api/PMCheck"
        private const val PM_QHOUR_REGION_ID = "5045"
        private const val PM_QHOUR_PROJECT_ID = "76"
        private const val PM_QHOUR_USER_ID = "28044"
    }
}
