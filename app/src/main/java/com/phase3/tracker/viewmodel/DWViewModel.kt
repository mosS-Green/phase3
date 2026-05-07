package com.phase3.tracker.viewmodel

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.phase3.tracker.data.SupabaseClient
import com.phase3.tracker.model.Activity
import com.phase3.tracker.model.DWRoom
import com.phase3.tracker.model.DWType
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
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream

class DWViewModel(application: Application) : AndroidViewModel(application) {

    private val supabase = SupabaseClient()

    // Global door/window types catalog
    private val _dwTypes = MutableStateFlow<List<DWType>>(emptyList())
    val dwTypes: StateFlow<List<DWType>> = _dwTypes.asStateFlow()

    // Rooms for current tower + column
    private val _rooms = MutableStateFlow<List<DWRoom>>(emptyList())
    val rooms: StateFlow<List<DWRoom>> = _rooms.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    // Debounce for status updates
    private data class PendingDWStatus(
        val roomId: Int, val typeId: Int, val flatNumber: Int, val isDone: Boolean
    )
    private val pendingUpdates = mutableListOf<PendingDWStatus>()
    private val pendingLock = Any()
    private var debounceJob: Job? = null

    init {
        loadTypes()
    }

    // ── Types CRUD ──────────────────────────────────────────────

    fun loadTypes() {
        viewModelScope.launch {
            val result = supabase.fetchDWTypes()
            result.onSuccess { arr ->
                val types = mutableListOf<DWType>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    types.add(DWType(
                        id = o.getInt("id"),
                        name = o.getString("name"),
                        kind = o.getString("kind"),
                        height = o.optDouble("height", 0.0),
                        breadth = o.optDouble("breadth", 0.0)
                    ))
                }
                _dwTypes.value = types
            }
        }
    }

    fun addType(name: String, kind: String, height: Double, breadth: Double) {
        viewModelScope.launch {
            val payload = JSONObject().apply {
                put("name", name)
                put("kind", kind)
                put("height", height)
                put("breadth", breadth)
            }
            supabase.insertDWType(payload).onSuccess { loadTypes() }
        }
    }

    fun updateType(id: Int, name: String, kind: String, height: Double, breadth: Double) {
        viewModelScope.launch {
            val payload = JSONObject().apply {
                put("name", name)
                put("kind", kind)
                put("height", height)
                put("breadth", breadth)
            }
            supabase.updateDWType(id, payload).onSuccess { loadTypes() }
        }
    }

    fun deleteType(id: Int) {
        viewModelScope.launch {
            supabase.deleteDWType(id).onSuccess { loadTypes() }
        }
    }

    // ── Rooms CRUD ──────────────────────────────────────────────

    fun loadRooms(towerId: Int, columnType: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val roomsResult = supabase.fetchDWRooms(towerId, columnType)
                val roomRows = roomsResult.getOrThrow()

                val roomList = mutableListOf<DWRoom>()
                for (i in 0 until roomRows.length()) {
                    val r = roomRows.getJSONObject(i)
                    val roomId = r.getInt("id")

                    // Fetch assigned types for this room
                    val rtResult = supabase.fetchDWRoomTypes(roomId)
                    val rtRows = rtResult.getOrElse { JSONArray() }
                    val types = mutableListOf<DWType>()
                    for (j in 0 until rtRows.length()) {
                        val rt = rtRows.getJSONObject(j)
                        val t = rt.optJSONObject("dw_types") ?: continue
                        types.add(DWType(
                            id = t.getInt("id"),
                            name = t.getString("name"),
                            kind = t.getString("kind"),
                            height = t.optDouble("height", 0.0),
                            breadth = t.optDouble("breadth", 0.0)
                        ))
                    }

                    // Fetch flat statuses for this room
                    val stResult = supabase.fetchDWStatuses(roomId)
                    val stRows = stResult.getOrElse { JSONArray() }
                    val flatStatuses = mutableMapOf<Int, MutableMap<Int, Boolean>>()
                    for (j in 0 until stRows.length()) {
                        val s = stRows.getJSONObject(j)
                        val flatNum = s.getInt("flat_number")
                        val typeId = s.getInt("type_id")
                        val isDone = s.getBoolean("is_done")
                        flatStatuses.getOrPut(flatNum) { mutableMapOf() }[typeId] = isDone
                    }

                    roomList.add(DWRoom(
                        id = roomId,
                        towerId = towerId,
                        columnType = columnType,
                        name = r.getString("name"),
                        sortOrder = r.getInt("sort_order"),
                        types = types,
                        flatStatuses = flatStatuses
                    ))
                }
                _rooms.value = roomList
            } catch (e: Exception) {
                _statusMessage.value = "Load failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addRoom(towerId: Int, columnType: String, name: String, typeIds: List<Int>) {
        viewModelScope.launch {
            val nextOrder = _rooms.value.maxOfOrNull { it.sortOrder + 1 } ?: 0
            withContext(Dispatchers.IO) {
                val payload = JSONObject().apply {
                    put("tower_id", towerId)
                    put("column_type", columnType)
                    put("name", name)
                    put("sort_order", nextOrder)
                }
                val result = supabase.insertDWRoom(payload)
                val row = result.getOrNull()?.optJSONObject(0) ?: return@withContext
                val roomId = row.getInt("id")

                // Assign types
                supabase.replaceDWRoomTypes(roomId, typeIds)

                // Initialize flat statuses for all 132 flats
                val flatArr = JSONArray()
                Activity.FLAT_NUMBERS.forEach { flatNum ->
                    typeIds.forEach { typeId ->
                        flatArr.put(JSONObject().apply {
                            put("room_id", roomId)
                            put("type_id", typeId)
                            put("flat_number", flatNum)
                            put("is_done", false)
                        })
                    }
                }
                if (flatArr.length() > 0) supabase.upsertDWStatuses(flatArr)
            }
            loadRooms(towerId, columnType)
            _statusMessage.value = "Room added: $name"
        }
    }

    fun updateRoom(roomId: Int, towerId: Int, columnType: String, name: String, typeIds: List<Int>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val payload = JSONObject().apply { put("name", name) }
                supabase.updateDWRoom(roomId, payload)
                supabase.replaceDWRoomTypes(roomId, typeIds)

                // Ensure flat statuses exist for new types
                val flatArr = JSONArray()
                Activity.FLAT_NUMBERS.forEach { flatNum ->
                    typeIds.forEach { typeId ->
                        flatArr.put(JSONObject().apply {
                            put("room_id", roomId)
                            put("type_id", typeId)
                            put("flat_number", flatNum)
                            put("is_done", false)
                        })
                    }
                }
                if (flatArr.length() > 0) supabase.upsertDWStatuses(flatArr)
            }
            loadRooms(towerId, columnType)
            _statusMessage.value = "Room updated: $name"
        }
    }

    fun deleteRoom(roomId: Int, towerId: Int, columnType: String) {
        viewModelScope.launch {
            supabase.deleteDWRoom(roomId)
            loadRooms(towerId, columnType)
        }
    }

    // ── Flat Status Toggle ──────────────────────────────────────

    fun toggleDWStatus(roomId: Int, typeId: Int, flatNumber: Int) {
        // Optimistic update
        val roomList = _rooms.value.toMutableList()
        val roomIdx = roomList.indexOfFirst { it.id == roomId }
        if (roomIdx < 0) return
        val room = roomList[roomIdx]
        val flatMap = room.flatStatuses.getOrPut(flatNumber) { mutableMapOf() }
        val current = flatMap[typeId] ?: false
        flatMap[typeId] = !current

        _rooms.value = roomList.toList()

        // Enqueue
        synchronized(pendingLock) {
            pendingUpdates.removeAll { it.roomId == roomId && it.typeId == typeId && it.flatNumber == flatNumber }
            pendingUpdates.add(PendingDWStatus(roomId, typeId, flatNumber, !current))
        }
        _isSyncing.value = true

        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(500)
            flushPendingUpdates()
        }
    }

    private suspend fun flushPendingUpdates() {
        val updates: List<PendingDWStatus>
        synchronized(pendingLock) {
            updates = pendingUpdates.toList()
            pendingUpdates.clear()
        }
        if (updates.isEmpty()) { _isSyncing.value = false; return }

        withContext(NonCancellable + Dispatchers.IO) {
            try {
                val arr = JSONArray()
                updates.forEach { u ->
                    arr.put(JSONObject().apply {
                        put("room_id", u.roomId)
                        put("type_id", u.typeId)
                        put("flat_number", u.flatNumber)
                        put("is_done", u.isDone)
                    })
                }
                supabase.upsertDWStatuses(arr)
            } catch (e: Exception) {
                _statusMessage.value = "Sync error: ${e.message}"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    // ── Percentage helpers ──────────────────────────────────────

    /** Overall completion for a flat across all rooms in a column */
    fun flatColumnCompletion(flatNumber: Int): Float {
        val roomList = _rooms.value
        if (roomList.isEmpty()) return 0f
        var totalWeight = 0
        var doneWeight = 0
        for (room in roomList) {
            for (type in room.types) {
                val w = if (type.isDoor) 3 else 1
                totalWeight += w
                val isDone = room.flatStatuses[flatNumber]?.get(type.id) ?: false
                if (isDone) doneWeight += w
            }
        }
        return if (totalWeight == 0) 0f else doneWeight.toFloat() / totalWeight * 100f
    }

    /** Overall column completion across all 132 flats */
    fun columnCompletion(): Float {
        val flats = Activity.FLAT_NUMBERS
        if (flats.isEmpty()) return 0f
        return flats.map { flatColumnCompletion(it) }.average().toFloat()
    }

    // ── Excel Export ─────────────────────────────────────────────

    fun exportToExcel(towers: List<Tower>): Uri? {
        val app = getApplication<Application>()
        try {
            val wb = XSSFWorkbook()
            val types = _dwTypes.value

            for (tower in towers) {
                for (colType in listOf("frame", "shutter", "glass")) {
                    val sheetName = "${tower.sheetName}_${colType.replaceFirstChar { it.uppercase() }}"
                    val sheet = wb.createSheet(sheetName)

                    // Load rooms synchronously (we're already in a coroutine context from caller)
                    // For export we use the currently loaded rooms if they match, otherwise skip
                    val rooms = _rooms.value.filter {
                        it.towerId == tower.id && it.columnType == colType
                    }

                    // Header row: Room | Type | Kind | Flat 201 | Flat 202 | ...
                    val headerRow = sheet.createRow(0)
                    headerRow.createCell(0).setCellValue("Room")
                    headerRow.createCell(1).setCellValue("Type")
                    headerRow.createCell(2).setCellValue("Kind")
                    headerRow.createCell(3).setCellValue("H")
                    headerRow.createCell(4).setCellValue("B")
                    Activity.FLAT_NUMBERS.forEachIndexed { idx, flatNum ->
                        headerRow.createCell(5 + idx).setCellValue(flatNum.toDouble())
                    }

                    var rowIdx = 1
                    for (room in rooms) {
                        for (type in room.types) {
                            val row = sheet.createRow(rowIdx++)
                            row.createCell(0).setCellValue(room.name)
                            row.createCell(1).setCellValue(type.name)
                            row.createCell(2).setCellValue(type.kind)
                            row.createCell(3).setCellValue(type.height)
                            row.createCell(4).setCellValue(type.breadth)
                            Activity.FLAT_NUMBERS.forEachIndexed { idx, flatNum ->
                                val isDone = room.flatStatuses[flatNum]?.get(type.id) ?: false
                                row.createCell(5 + idx).setCellValue(if (isDone) "Y" else "")
                            }
                        }
                    }
                }
            }

            val fileName = "Phase3_DW_${System.currentTimeMillis()}.xlsx"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cv = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                uri?.let { app.contentResolver.openOutputStream(it)?.use { out -> wb.write(out) } }
                wb.close()
                _statusMessage.value = "Exported: $fileName"
                return uri
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(dir, fileName)
                file.outputStream().use { wb.write(it) }
                wb.close()
                _statusMessage.value = "Exported: $fileName"
                return Uri.fromFile(file)
            }
        } catch (e: Exception) {
            _statusMessage.value = "Export failed: ${e.message}"
            return null
        }
    }

    // ── Excel Import ─────────────────────────────────────────────

    fun importFromExcel(inputStream: InputStream, towers: List<Tower>) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                withContext(Dispatchers.IO) {
                    val wb = XSSFWorkbook(inputStream)
                    val allTypes = _dwTypes.value.associateBy { it.name }

                    for (tower in towers) {
                        for (colType in listOf("frame", "shutter", "glass")) {
                            val sheetName = "${tower.sheetName}_${colType.replaceFirstChar { it.uppercase() }}"
                            val sheet = wb.getSheet(sheetName) ?: continue

                            // Read header to get flat number mapping
                            val headerRow = sheet.getRow(0) ?: continue
                            val flatCols = mutableMapOf<Int, Int>() // colIdx -> flatNumber
                            for (c in 5 until headerRow.lastCellNum) {
                                val cell = headerRow.getCell(c) ?: continue
                                val flatNum = cell.numericCellValue.toInt()
                                flatCols[c] = flatNum
                            }

                            // Group data rows by room name
                            val roomData = mutableMapOf<String, MutableList<Triple<DWType, Map<Int, Boolean>, Int>>>()
                            for (r in 1..sheet.lastRowNum) {
                                val row = sheet.getRow(r) ?: continue
                                val roomName = row.getCell(0)?.stringCellValue?.trim() ?: continue
                                val typeName = row.getCell(1)?.stringCellValue?.trim() ?: continue
                                val type = allTypes[typeName] ?: continue

                                val statuses = mutableMapOf<Int, Boolean>()
                                flatCols.forEach { (colIdx, flatNum) ->
                                    val cellVal = row.getCell(colIdx)?.stringCellValue?.trim() ?: ""
                                    statuses[flatNum] = cellVal.equals("Y", ignoreCase = true)
                                }

                                roomData.getOrPut(roomName) { mutableListOf() }
                                    .add(Triple(type, statuses, 0))
                            }

                            // Create rooms + statuses
                            var sortOrder = 0
                            for ((roomName, entries) in roomData) {
                                val typeIds = entries.map { it.first.id }
                                val payload = JSONObject().apply {
                                    put("tower_id", tower.id)
                                    put("column_type", colType)
                                    put("name", roomName)
                                    put("sort_order", sortOrder++)
                                }

                                // Upsert room
                                val result = supabase.insertDWRoom(payload)
                                val roomRow = result.getOrNull()?.optJSONObject(0) ?: continue
                                val roomId = roomRow.getInt("id")

                                supabase.replaceDWRoomTypes(roomId, typeIds)

                                // Upsert statuses
                                val statusArr = JSONArray()
                                for ((type, statuses, _) in entries) {
                                    statuses.forEach { (flatNum, isDone) ->
                                        statusArr.put(JSONObject().apply {
                                            put("room_id", roomId)
                                            put("type_id", type.id)
                                            put("flat_number", flatNum)
                                            put("is_done", isDone)
                                        })
                                    }
                                }
                                if (statusArr.length() > 0) supabase.upsertDWStatuses(statusArr)
                            }
                        }
                    }
                    wb.close()
                }
                _statusMessage.value = "Import complete"
            } catch (e: Exception) {
                _statusMessage.value = "Import failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearStatusMessage() { _statusMessage.value = null }
}
