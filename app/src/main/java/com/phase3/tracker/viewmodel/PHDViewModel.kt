package com.phase3.tracker.viewmodel

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.phase3.tracker.data.ConnectivityObserver
import com.phase3.tracker.data.SupabaseClient
import com.phase3.tracker.model.Activity
import com.phase3.tracker.model.PHDDoorConfig
import com.phase3.tracker.model.Tower
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFColor
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream

class PHDViewModel(application: Application) : AndroidViewModel(application) {

    private val supabase = SupabaseClient()
    private val connectivityObserver = ConnectivityObserver(application)

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    /**
     * PHD statuses: towerId → (flatNumber → (doorType → isDone))
     * We keep data for all loaded towers so navigation between them is instant.
     */
    private val _phdStatuses = MutableStateFlow<Map<Int, Map<String, Boolean>>>(emptyMap())
    val phdStatuses: StateFlow<Map<Int, Map<String, Boolean>>> = _phdStatuses.asStateFlow()

    private val _currentTowerId = MutableStateFlow(-1)
    val currentTowerId: StateFlow<Int> = _currentTowerId.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    // ── Debounce for status updates ────────────────────────────────
    private data class PendingPHDStatus(
        val towerId: Int, val flatNumber: Int, val doorType: String, val isDone: Boolean
    )
    private val pendingUpdates = mutableListOf<PendingPHDStatus>()
    private val pendingLock = Any()
    private var debounceJob: Job? = null

    // ── Cache helpers ──────────────────────────────────────────────

    private fun saveCache(filename: String, dataStr: String) {
        try {
            val file = File(getApplication<Application>().cacheDir, filename)
            file.writeText(dataStr)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadCache(filename: String): String? {
        return try {
            val file = File(getApplication<Application>().cacheDir, filename)
            if (file.exists()) file.readText() else null
        } catch (e: Exception) {
            null
        }
    }

    private fun serializeStatuses(statuses: Map<Int, Map<String, Boolean>>): String {
        val obj = JSONObject()
        for ((flatNum, doorMap) in statuses) {
            val doorObj = JSONObject()
            for ((doorType, isDone) in doorMap) {
                doorObj.put(doorType, isDone)
            }
            obj.put(flatNum.toString(), doorObj)
        }
        return obj.toString()
    }

    private fun deserializeStatuses(jsonStr: String): Map<Int, Map<String, Boolean>> {
        val map = mutableMapOf<Int, Map<String, Boolean>>()
        val obj = JSONObject(jsonStr)
        val keys = obj.keys()
        while (keys.hasNext()) {
            val flatStr = keys.next()
            val flatNum = flatStr.toInt()
            val doorObj = obj.getJSONObject(flatStr)
            val doorMap = mutableMapOf<String, Boolean>()
            val doorKeys = doorObj.keys()
            while (doorKeys.hasNext()) {
                val doorType = doorKeys.next()
                doorMap[doorType] = doorObj.getBoolean(doorType)
            }
            map[flatNum] = doorMap
        }
        return map
    }

    private fun serializePendingUpdates(updates: List<PendingPHDStatus>): String {
        val arr = JSONArray()
        for (u in updates) {
            arr.put(JSONObject().apply {
                put("towerId", u.towerId)
                put("flatNumber", u.flatNumber)
                put("doorType", u.doorType)
                put("isDone", u.isDone)
            })
        }
        return arr.toString()
    }

    private fun deserializePendingUpdates(jsonStr: String): List<PendingPHDStatus> {
        val list = mutableListOf<PendingPHDStatus>()
        val arr = JSONArray(jsonStr)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(PendingPHDStatus(
                towerId = o.getInt("towerId"),
                flatNumber = o.getInt("flatNumber"),
                doorType = o.getString("doorType"),
                isDone = o.getBoolean("isDone")
            ))
        }
        return list
    }

    // ── Init ───────────────────────────────────────────────────────

    init {
        // Load pending sync queue
        val cachedSync = loadCache("phd_pending_sync.json")
        if (cachedSync != null) {
            try {
                synchronized(pendingLock) {
                    pendingUpdates.clear()
                    pendingUpdates.addAll(deserializePendingUpdates(cachedSync))
                }
                if (pendingUpdates.isNotEmpty()) {
                    _isSyncing.value = true
                    viewModelScope.launch { flushPendingUpdates() }
                }
            } catch (_: Exception) { }
        }

        // Auto-resync when connectivity returns
        viewModelScope.launch {
            var wasOffline = !connectivityObserver.isOnline.value
            connectivityObserver.isOnline.collect { online ->
                if (online && wasOffline) {
                    if (pendingUpdates.isNotEmpty()) {
                        viewModelScope.launch { flushPendingUpdates() }
                    }
                    // Refresh current tower if one is loaded
                    val tid = _currentTowerId.value
                    if (tid > 0) loadStatuses(tid)
                }
                wasOffline = !online
            }
        }
    }

    // ── Load statuses from Supabase ────────────────────────────────

    fun loadStatuses(towerId: Int) {
        _currentTowerId.value = towerId
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Try cache first for instant display
                val cacheKey = "phd_statuses_t${towerId}.json"
                if (_phdStatuses.value.isEmpty()) {
                    val cached = loadCache(cacheKey)
                    if (cached != null) {
                        try {
                            _phdStatuses.value = deserializeStatuses(cached)
                        } catch (_: Exception) { }
                    }
                }

                // Fetch from API
                val result = supabase.fetchPHDStatuses(towerId)
                result.onSuccess { arr ->
                    val statusMap = mutableMapOf<Int, MutableMap<String, Boolean>>()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val flatNum = o.getInt("flat_number")
                        val doorType = o.getString("door_type")
                        val isDone = o.getBoolean("is_done")
                        statusMap.getOrPut(flatNum) { mutableMapOf() }[doorType] = isDone
                    }
                    _phdStatuses.value = statusMap
                    saveCache(cacheKey, serializeStatuses(statusMap))
                }
                result.onFailure { e ->
                    _statusMessage.value = "Load failed: ${e.message}"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Load failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ── Toggle status ──────────────────────────────────────────────

    fun toggleStatus(towerId: Int, flatNumber: Int, doorType: String) {
        // Optimistic update
        val current = _phdStatuses.value.toMutableMap()
        val flatMap = (current[flatNumber] ?: emptyMap()).toMutableMap()
        val currentVal = flatMap[doorType] ?: false
        val newVal = !currentVal
        flatMap[doorType] = newVal
        current[flatNumber] = flatMap
        _phdStatuses.value = current

        // Enqueue for sync
        synchronized(pendingLock) {
            pendingUpdates.removeAll {
                it.towerId == towerId && it.flatNumber == flatNumber && it.doorType == doorType
            }
            pendingUpdates.add(PendingPHDStatus(towerId, flatNumber, doorType, newVal))
            saveCache("phd_pending_sync.json", serializePendingUpdates(pendingUpdates))
        }
        _isSyncing.value = true

        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(500)
            flushPendingUpdates()
        }
    }

    private suspend fun flushPendingUpdates() {
        val updatesToSend: List<PendingPHDStatus>
        synchronized(pendingLock) {
            updatesToSend = pendingUpdates.toList()
        }
        if (updatesToSend.isEmpty()) {
            _isSyncing.value = false
            return
        }

        _isSyncing.value = true

        val success = withContext(NonCancellable + Dispatchers.IO) {
            try {
                val arr = JSONArray()
                updatesToSend.forEach { u ->
                    arr.put(JSONObject().apply {
                        put("tower_id", u.towerId)
                        put("flat_number", u.flatNumber)
                        put("door_type", u.doorType)
                        put("is_done", u.isDone)
                    })
                }
                val result = supabase.upsertPHDStatuses(arr)
                if (result.isSuccess) {
                    true
                } else {
                    _statusMessage.value = "Sync failed: ${result.exceptionOrNull()?.message}"
                    false
                }
            } catch (e: Exception) {
                _statusMessage.value = "Sync error: ${e.message}"
                false
            }
        }

        if (success) {
            synchronized(pendingLock) {
                pendingUpdates.removeAll { item ->
                    updatesToSend.any { sent ->
                        sent.towerId == item.towerId &&
                        sent.flatNumber == item.flatNumber &&
                        sent.doorType == item.doorType &&
                        sent.isDone == item.isDone
                    }
                }
                saveCache("phd_pending_sync.json", serializePendingUpdates(pendingUpdates))
            }
        }
        _isSyncing.value = false
    }

    // ── Completion helpers ─────────────────────────────────────────

    /**
     * Completion % for a specific flat, based on the door types
     * that apply to its unit type in the given tower.
     */
    fun flatCompletion(flatNumber: Int, towerSheetName: String): Float {
        val unitDigit = flatNumber % 100
        val doorTypes = PHDDoorConfig.getDoorTypes(towerSheetName, unitDigit)
        if (doorTypes.isEmpty()) return 0f

        val flatMap = _phdStatuses.value[flatNumber] ?: return 0f
        val done = doorTypes.count { flatMap[it] == true }
        return done.toFloat() / doorTypes.size * 100f
    }

    fun clearStatusMessage() { _statusMessage.value = null }

    // ── Cell-reading helpers ───────────────────────────────────────

    private fun cellString(cell: org.apache.poi.ss.usermodel.Cell?): String? {
        if (cell == null) return null
        return try {
            when (cell.cellType) {
                org.apache.poi.ss.usermodel.CellType.STRING -> cell.stringCellValue?.trim()
                org.apache.poi.ss.usermodel.CellType.NUMERIC -> {
                    val v = cell.numericCellValue
                    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
                }
                org.apache.poi.ss.usermodel.CellType.BOOLEAN -> cell.booleanCellValue.toString()
                org.apache.poi.ss.usermodel.CellType.FORMULA -> try { cell.stringCellValue?.trim() } catch (_: Exception) {
                    try { cell.numericCellValue.toString() } catch (_: Exception) { null }
                }
                else -> null
            }
        } catch (_: Exception) { null }
    }

    private fun cellDouble(cell: org.apache.poi.ss.usermodel.Cell?): Double {
        if (cell == null) return 0.0
        return try {
            when (cell.cellType) {
                org.apache.poi.ss.usermodel.CellType.NUMERIC -> cell.numericCellValue
                org.apache.poi.ss.usermodel.CellType.STRING -> cell.stringCellValue?.trim()?.toDoubleOrNull() ?: 0.0
                org.apache.poi.ss.usermodel.CellType.FORMULA -> try { cell.numericCellValue } catch (_: Exception) { 0.0 }
                else -> 0.0
            }
        } catch (_: Exception) { 0.0 }
    }

    // ── Excel Export Styling ────────────────────────────────────────

    private fun phdHeaderStyle(wb: XSSFWorkbook): XSSFCellStyle {
        val font = wb.createFont()
        font.bold = true
        (font as org.apache.poi.xssf.usermodel.XSSFFont).color = IndexedColors.WHITE.index
        font.fontHeightInPoints = 10
        val style = wb.createCellStyle() as XSSFCellStyle
        style.setFont(font)
        style.setFillForegroundColor(XSSFColor(byteArrayOf(0x00.toByte(), 0x51.toByte(), 0x41.toByte()), null)) // Sea green matching theme
        style.fillPattern = FillPatternType.SOLID_FOREGROUND
        style.alignment = HorizontalAlignment.CENTER
        style.setBorderBottom(BorderStyle.THIN)
        return style
    }

    private fun phdDoneStyle(wb: XSSFWorkbook): XSSFCellStyle {
        val style = wb.createCellStyle() as XSSFCellStyle
        style.setFillForegroundColor(XSSFColor(byteArrayOf(0x81.toByte(), 0xC7.toByte(), 0x84.toByte()), null))
        style.fillPattern = FillPatternType.SOLID_FOREGROUND
        style.alignment = HorizontalAlignment.CENTER
        return style
    }

    private fun phdStripeStyle(wb: XSSFWorkbook): XSSFCellStyle {
        val style = wb.createCellStyle() as XSSFCellStyle
        style.setFillForegroundColor(XSSFColor(byteArrayOf(0xF5.toByte(), 0xF5.toByte(), 0xF5.toByte()), null))
        style.fillPattern = FillPatternType.SOLID_FOREGROUND
        return style
    }

    // ── Excel Export ────────────────────────────────────────────────

    fun exportToExcel(towers: List<Tower>) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val uri = withContext(Dispatchers.IO) { buildPHDWorkbook(towers) }
                _statusMessage.value = if (uri != null) "PHD export saved to Downloads" else "Export failed"
            } catch (e: Exception) {
                _statusMessage.value = "Export failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun buildPHDWorkbook(towers: List<Tower>): Uri? {
        val app = getApplication<Application>()
        val wb = XSSFWorkbook()
        val hdrStyle = phdHeaderStyle(wb)
        val doneStyle = phdDoneStyle(wb)
        val stripeStyle = phdStripeStyle(wb)

        for (tower in towers) {
            val sheetName = tower.sheetName
            val sheet = wb.createSheet(sheetName)

            // Header: Tower | Flat No. | Unit Type | Door Type | Status
            val headerRow = sheet.createRow(0)
            listOf("Tower", "Flat No.", "Unit Type", "Door Type", "Status")
                .forEachIndexed { i, title ->
                    headerRow.createCell(i).apply {
                        setCellValue(title)
                        cellStyle = hdrStyle
                    }
                }

            sheet.setColumnWidth(0, 15 * 256) // Tower
            sheet.setColumnWidth(1, 12 * 256) // Flat No.
            sheet.setColumnWidth(2, 12 * 256) // Unit Type
            sheet.setColumnWidth(3, 20 * 256) // Door Type
            sheet.setColumnWidth(4, 15 * 256) // Status

            sheet.createFreezePane(0, 1)

            // Fetch ALL statuses for this tower to avoid fetching in loop
            val statusesList = supabase.fetchPHDStatuses(tower.id).getOrElse { JSONArray() }
            val statusMap = mutableMapOf<Int, MutableMap<String, Boolean>>()
            for (j in 0 until statusesList.length()) {
                val o = statusesList.getJSONObject(j)
                val flatNum = o.getInt("flat_number")
                val doorType = o.getString("door_type")
                val isDone = o.getBoolean("is_done")
                statusMap.getOrPut(flatNum) { mutableMapOf() }[doorType] = isDone
            }

            var rowIdx = 1
            var useStripe = false
            for (flatNum in Activity.FLAT_NUMBERS) {
                useStripe = !useStripe
                val unitDigit = flatNum % 100
                val unitLabel = when (unitDigit) {
                    1 -> "A"
                    2 -> "B"
                    3 -> "C"
                    4 -> "D"
                    else -> "Unknown"
                }
                val doorTypes = PHDDoorConfig.getDoorTypes(tower.sheetName, unitDigit)
                for (doorType in doorTypes) {
                    val isDone = statusMap[flatNum]?.get(doorType) ?: false
                    val row = sheet.createRow(rowIdx++)
                    val rowStyle = if (useStripe) stripeStyle else null

                    row.createCell(0).also {
                        it.setCellValue(tower.name)
                        if (rowStyle != null) it.cellStyle = rowStyle
                    }
                    row.createCell(1).also {
                        it.setCellValue(flatNum.toDouble())
                        if (rowStyle != null) it.cellStyle = rowStyle
                    }
                    row.createCell(2).also {
                        it.setCellValue(unitLabel)
                        if (rowStyle != null) it.cellStyle = rowStyle
                    }
                    row.createCell(3).also {
                        it.setCellValue(doorType)
                        if (rowStyle != null) it.cellStyle = rowStyle
                    }
                    row.createCell(4).also {
                        if (isDone) {
                            it.setCellValue("C")
                            it.cellStyle = doneStyle
                        } else if (rowStyle != null) {
                            it.cellStyle = rowStyle
                        }
                    }
                }
            }
        }

        val fileName = "Phase3_PHD_${System.currentTimeMillis()}.xlsx"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cv = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
            uri?.let { app.contentResolver.openOutputStream(it)?.use { out -> wb.write(out) } }
            wb.close()
            uri
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(dir, fileName)
            file.outputStream().use { wb.write(it) }
            wb.close()
            Uri.fromFile(file)
        }
    }

    // ── Excel Import ────────────────────────────────────────────────

    fun importFromExcel(inputStream: InputStream, towers: List<Tower>) {
        viewModelScope.launch {
            _isLoading.value = true
            _isImporting.value = true
            try {
                withContext(Dispatchers.IO) {
                    val wb = XSSFWorkbook(inputStream)
                    for (tower in towers) {
                        val sheet = wb.getSheet(tower.sheetName)
                            ?: wb.getSheet("Tower ${tower.name.filter { it.isDigit() }}")
                            ?: wb.getSheet(tower.name)
                            ?: continue

                        val statusArr = JSONArray()
                        for (r in 1..sheet.lastRowNum) {
                            val row = sheet.getRow(r) ?: continue
                            // cols: 0=Tower, 1=FlatNo, 2=UnitType, 3=DoorType, 4=Status
                            val flatNum = cellDouble(row.getCell(1)).toInt().takeIf { it > 0 } ?: continue
                            val doorType = cellString(row.getCell(3))?.takeIf { it.isNotBlank() } ?: continue
                            val statusRaw = cellString(row.getCell(4)) ?: ""
                            val isDone = statusRaw.equals("Y", ignoreCase = true) || statusRaw.equals("C", ignoreCase = true)

                            statusArr.put(JSONObject().apply {
                                put("tower_id", tower.id)
                                put("flat_number", flatNum)
                                put("door_type", doorType)
                                put("is_done", isDone)
                            })
                        }
                        if (statusArr.length() > 0) {
                            supabase.upsertPHDStatuses(statusArr)
                        }
                    }
                    wb.close()
                }
                // Refresh current tower if loaded
                val tid = _currentTowerId.value
                if (tid > 0) loadStatuses(tid)
                _statusMessage.value = "Imported and upsynced successfully"
            } catch (e: Exception) {
                _statusMessage.value = "Import failed: ${e.message}"
            } finally {
                _isLoading.value = false
                _isImporting.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        connectivityObserver.unregister()
    }
}
