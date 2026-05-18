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

    /** Colour helpers for DW workbook */
    private fun dwHeaderStyle(wb: XSSFWorkbook): XSSFCellStyle {
        val font = wb.createFont()
        font.bold = true
        (font as org.apache.poi.xssf.usermodel.XSSFFont).color = IndexedColors.WHITE.index
        font.fontHeightInPoints = 10
        val style = wb.createCellStyle() as XSSFCellStyle
        style.setFont(font)
        style.setFillForegroundColor(XSSFColor(byteArrayOf(0x37.toByte(), 0x47.toByte(), 0x4F.toByte()), null)) // blue-grey
        style.fillPattern = FillPatternType.SOLID_FOREGROUND
        style.alignment = HorizontalAlignment.CENTER
        style.setBorderBottom(BorderStyle.THIN)
        return style
    }

    private fun dwDoneStyle(wb: XSSFWorkbook): XSSFCellStyle {
        val style = wb.createCellStyle() as XSSFCellStyle
        style.setFillForegroundColor(XSSFColor(byteArrayOf(0x81.toByte(), 0xC7.toByte(), 0x84.toByte()), null))
        style.fillPattern = FillPatternType.SOLID_FOREGROUND
        style.alignment = HorizontalAlignment.CENTER
        return style
    }

    private fun dwStripeStyle(wb: XSSFWorkbook): XSSFCellStyle {
        val style = wb.createCellStyle() as XSSFCellStyle
        style.setFillForegroundColor(XSSFColor(byteArrayOf(0xF5.toByte(), 0xF5.toByte(), 0xF5.toByte()), null))
        style.fillPattern = FillPatternType.SOLID_FOREGROUND
        return style
    }

    fun exportToExcel(towers: List<Tower>) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val uri = withContext(Dispatchers.IO) { buildDWWorkbook(towers) }
                _statusMessage.value = if (uri != null) "DW export saved to Downloads" else "Export failed"
            } catch (e: Exception) {
                _statusMessage.value = "Export failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun buildDWWorkbook(towers: List<Tower>): Uri? {
        val app = getApplication<Application>()
        val wb = XSSFWorkbook()
        val hdrStyle  = dwHeaderStyle(wb)
        val doneStyle = dwDoneStyle(wb)
        val stripeStyle = dwStripeStyle(wb)

        for (tower in towers) {
            val sheetName = tower.sheetName
            val sheet = wb.createSheet(sheetName)

            // Fetch ALL rooms for this tower
            val roomRows = supabase.fetchAllDWRooms(tower.id).getOrElse { JSONArray() }
            
            // We want to group by room name and track frame/shutter/glass room IDs and their types.
            // Map: roomName -> colType -> (roomId, types)
            data class RoomColInfo(val roomId: Int, val types: List<DWType>)
            val roomData = mutableMapOf<String, MutableMap<String, RoomColInfo>>()
            
            // Also need all types across all colTypes for a room name
            val roomAllTypes = mutableMapOf<String, MutableMap<Int, DWType>>()

            for (i in 0 until roomRows.length()) {
                val r = roomRows.getJSONObject(i)
                val roomId = r.getInt("id")
                val roomName = r.getString("name")
                val colType = r.getString("column_type")
                
                val rtRows = supabase.fetchDWRoomTypes(roomId).getOrElse { JSONArray() }
                val types = mutableListOf<DWType>()
                for (j in 0 until rtRows.length()) {
                    val rt = rtRows.getJSONObject(j)
                    val t = rt.optJSONObject("dw_types") ?: continue
                    val dwType = DWType(
                        id = t.getInt("id"),
                        name = t.getString("name"),
                        kind = t.getString("kind"),
                        height = t.optDouble("height", 0.0),
                        breadth = t.optDouble("breadth", 0.0)
                    )
                    types.add(dwType)
                    roomAllTypes.getOrPut(roomName) { mutableMapOf() }[dwType.id] = dwType
                }
                roomData.getOrPut(roomName) { mutableMapOf() }[colType] = RoomColInfo(roomId, types)
            }

            // Header: Tower | Flat No. | Room Name | Type Name | D or W | W | H | FRAME | SHUTTER | GLASS
            val headerRow = sheet.createRow(0)
            listOf("Tower", "Flat No.", "Room Name", "Type Name", "D or W", "W", "H", "FRAME", "SHUTTER", "GLASS")
                .forEachIndexed { i, title ->
                    headerRow.createCell(i).apply {
                        setCellValue(title)
                        cellStyle = hdrStyle
                    }
                }

            // Column widths
            sheet.setColumnWidth(0, 20 * 256) // Tower
            sheet.setColumnWidth(1, 10 * 256) // Flat No.
            sheet.setColumnWidth(2, 25 * 256) // Room Name
            sheet.setColumnWidth(3, 30 * 256) // Type Name
            sheet.setColumnWidth(4,  8 * 256) // D or W
            sheet.setColumnWidth(5,  8 * 256) // W
            sheet.setColumnWidth(6,  8 * 256) // H
            sheet.setColumnWidth(7, 12 * 256) // FRAME
            sheet.setColumnWidth(8, 12 * 256) // SHUTTER
            sheet.setColumnWidth(9, 12 * 256) // GLASS

            sheet.createFreezePane(0, 1)

            // Fetch all statuses per room up front
            val roomStatuses = mutableMapOf<Int, Map<Int, Map<Int, Boolean>>>() // roomId -> flatNum -> typeId -> isDone
            for (colMap in roomData.values) {
                for (info in colMap.values) {
                    val stRows = supabase.fetchDWStatuses(info.roomId).getOrElse { JSONArray() }
                    val flatMap = mutableMapOf<Int, MutableMap<Int, Boolean>>()
                    for (j in 0 until stRows.length()) {
                        val s = stRows.getJSONObject(j)
                        val flatNum = s.getInt("flat_number")
                        val typeId  = s.getInt("type_id")
                        val isDone  = s.getBoolean("is_done")
                        flatMap.getOrPut(flatNum) { mutableMapOf() }[typeId] = isDone
                    }
                    roomStatuses[info.roomId] = flatMap
                }
            }

            // Write tall rows: one per flat × roomName × DWType
            var rowIdx = 1
            var lastFlat = -1
            var useStripe = false
            for (flatNum in Activity.FLAT_NUMBERS) {
                if (flatNum != lastFlat) { useStripe = !useStripe; lastFlat = flatNum }
                
                // Sort room names so output is consistent
                val sortedRooms = roomAllTypes.keys.sorted()
                
                for (roomName in sortedRooms) {
                    val typesForRoom = roomAllTypes[roomName]!!.values.sortedBy { it.name }
                    val colsInfo = roomData[roomName] ?: emptyMap()
                    
                    val frameInfo = colsInfo["frame"]
                    val shutterInfo = colsInfo["shutter"]
                    val glassInfo = colsInfo["glass"]
                    
                    val frameStatuses = if (frameInfo != null) roomStatuses[frameInfo.roomId] ?: emptyMap() else emptyMap()
                    val shutterStatuses = if (shutterInfo != null) roomStatuses[shutterInfo.roomId] ?: emptyMap() else emptyMap()
                    val glassStatuses = if (glassInfo != null) roomStatuses[glassInfo.roomId] ?: emptyMap() else emptyMap()

                    for (type in typesForRoom) {
                        // Check if completed
                        val frameDone = frameStatuses[flatNum]?.get(type.id) ?: false
                        val shutterDone = shutterStatuses[flatNum]?.get(type.id) ?: false
                        val glassDone = glassStatuses[flatNum]?.get(type.id) ?: false
                        
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
                            it.setCellValue(roomName)
                            if (rowStyle != null) it.cellStyle = rowStyle
                        }
                        row.createCell(3).also {
                            it.setCellValue(type.name)
                            if (rowStyle != null) it.cellStyle = rowStyle
                        }
                        row.createCell(4).also {
                            it.setCellValue(if (type.isDoor) "D" else "W")
                            if (rowStyle != null) it.cellStyle = rowStyle
                        }
                        row.createCell(5).also {
                            it.setCellValue(type.breadth)
                            if (rowStyle != null) it.cellStyle = rowStyle
                        }
                        row.createCell(6).also {
                            it.setCellValue(type.height)
                            if (rowStyle != null) it.cellStyle = rowStyle
                        }
                        
                        // FRAME status
                        row.createCell(7).also {
                            if (frameDone) {
                                it.setCellValue("Y")
                                it.cellStyle = doneStyle
                            } else if (rowStyle != null) {
                                it.cellStyle = rowStyle
                            }
                        }
                        
                        // SHUTTER status
                        row.createCell(8).also {
                            if (shutterDone) {
                                it.setCellValue("Y")
                                it.cellStyle = doneStyle
                            } else if (rowStyle != null) {
                                it.cellStyle = rowStyle
                            }
                        }
                        
                        // GLASS status
                        row.createCell(9).also {
                            if (glassDone) {
                                it.setCellValue("Y")
                                it.cellStyle = doneStyle
                            } else if (rowStyle != null) {
                                it.cellStyle = rowStyle
                            }
                        }
                    }
                }
            }
        }

        val fileName = "Phase3_DW_${System.currentTimeMillis()}.xlsx"
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

    // ── Excel Import ─────────────────────────────────────────────

    /**
     * Parse the combined DW export:
     *   Tower | Flat No. | Room Name | Type Name | D or W | W | H | FRAME | SHUTTER | GLASS
     * and upsert everything to Supabase.
     */
    fun importFromExcel(inputStream: InputStream, towers: List<Tower>) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                withContext(Dispatchers.IO) {
                    val wb = XSSFWorkbook(inputStream)

                    for (tower in towers) {
                        val sheetName = tower.sheetName
                        val sheet = wb.getSheet(sheetName) ?: continue

                        // key: roomName -> list of (typeName, kind, breadth, height, flatNum, frameDone, shutterDone, glassDone)
                        data class DWRow(val typeName: String, val kind: String, val breadth: Double, val height: Double, val flatNum: Int, val frameDone: Boolean, val shutterDone: Boolean, val glassDone: Boolean)
                        val roomRows = mutableMapOf<String, MutableList<DWRow>>()

                        for (r in 1..sheet.lastRowNum) {
                            val row = sheet.getRow(r) ?: continue
                            // cols: 0=Tower, 1=FlatNo, 2=RoomName, 3=TypeName, 4=D/W, 5=W, 6=H, 7=FRAME, 8=SHUTTER, 9=GLASS
                            val roomName = row.getCell(2)?.stringCellValue?.trim()?.takeIf { it.isNotBlank() } ?: continue
                            val typeName = row.getCell(3)?.stringCellValue?.trim()?.takeIf { it.isNotBlank() } ?: continue
                            val kindRaw  = row.getCell(4)?.stringCellValue?.trim()?.uppercase() ?: "W"
                            val kind     = if (kindRaw == "D") "door" else "window"
                            val breadth  = try { row.getCell(5)?.numericCellValue ?: 0.0 } catch (_: Exception) { 0.0 }
                            val height   = try { row.getCell(6)?.numericCellValue ?: 0.0 } catch (_: Exception) { 0.0 }
                            val flatNum  = try { row.getCell(1)?.numericCellValue?.toInt() ?: continue } catch (_: Exception) { continue }
                            
                            val frameRaw = try { row.getCell(7)?.stringCellValue?.trim() ?: "" } catch (_: Exception) { "" }
                            val shutterRaw = try { row.getCell(8)?.stringCellValue?.trim() ?: "" } catch (_: Exception) { "" }
                            val glassRaw = try { row.getCell(9)?.stringCellValue?.trim() ?: "" } catch (_: Exception) { "" }
                            
                            val frameDone   = frameRaw.equals("Y", ignoreCase = true)
                            val shutterDone = shutterRaw.equals("Y", ignoreCase = true)
                            val glassDone   = glassRaw.equals("Y", ignoreCase = true)

                            roomRows.getOrPut(roomName) { mutableListOf() }
                                .add(DWRow(typeName, kind, breadth, height, flatNum, frameDone, shutterDone, glassDone))
                        }

                        // Load/create DW types catalog
                        val typesCatalog = _dwTypes.value.associateBy { it.name }.toMutableMap()

                        for (colType in listOf("frame", "shutter", "glass")) {
                            // Load existing rooms for this tower+column to avoid duplicates
                            val existingRoomRows = supabase.fetchDWRooms(tower.id, colType).getOrElse { JSONArray() }
                            val existingRoomIds = mutableMapOf<String, Int>() // roomName -> id
                            for (i in 0 until existingRoomRows.length()) {
                                val o = existingRoomRows.getJSONObject(i)
                                existingRoomIds[o.getString("name")] = o.getInt("id")
                            }

                            var sortOrder = existingRoomRows.length()
                            for ((roomName, entries) in roomRows) {
                                // Collect unique types in this room
                                val uniqueTypes = entries.map { Triple(it.typeName, it.kind, it.breadth to it.height) }
                                    .distinctBy { it.first }

                                // Ensure each DWType exists in catalog
                                val typeIds = mutableListOf<Int>()
                                for ((tName, tKind, dims) in uniqueTypes) {
                                    val existing = typesCatalog[tName]
                                    if (existing != null) {
                                        typeIds.add(existing.id)
                                    } else {
                                        val payload = JSONObject().apply {
                                            put("name", tName)
                                            put("kind", tKind)
                                            put("height", dims.second)
                                            put("breadth", dims.first)
                                        }
                                        val res = supabase.insertDWType(payload)
                                        val newId = res.getOrNull()?.optJSONObject(0)?.optInt("id") ?: continue
                                        val newType = DWType(id = newId, name = tName, kind = tKind, height = dims.second, breadth = dims.first)
                                        typesCatalog[tName] = newType
                                        typeIds.add(newId)
                                    }
                                }

                                // Get or create room
                                val existingId = existingRoomIds[roomName]
                                val roomId: Int
                                if (existingId != null) {
                                    roomId = existingId
                                } else {
                                    val payload = JSONObject().apply {
                                        put("tower_id", tower.id)
                                        put("column_type", colType)
                                        put("name", roomName)
                                        put("sort_order", sortOrder)
                                    }
                                    val res = supabase.insertDWRoom(payload)
                                    val newId = res.getOrNull()?.optJSONObject(0)?.optInt("id")
                                    if (newId == null) {
                                        sortOrder++
                                        continue
                                    }
                                    roomId = newId
                                }
                                sortOrder++

                                supabase.replaceDWRoomTypes(roomId, typeIds)

                                // Upsert statuses
                                val statusArr = JSONArray()
                                for (entry in entries) {
                                    val typeId = typesCatalog[entry.typeName]?.id ?: continue
                                    val isDone = when(colType) {
                                        "frame" -> entry.frameDone
                                        "shutter" -> entry.shutterDone
                                        "glass" -> entry.glassDone
                                        else -> false
                                    }
                                    statusArr.put(JSONObject().apply {
                                        put("room_id", roomId)
                                        put("type_id", typeId)
                                        put("flat_number", entry.flatNum)
                                        put("is_done", isDone)
                                    })
                                }
                                if (statusArr.length() > 0) supabase.upsertDWStatuses(statusArr)
                            }
                        }
                    }
                    wb.close()
                    // Refresh types list after possible new types were created
                    val refreshed = supabase.fetchDWTypes().getOrElse { JSONArray() }
                    val types = mutableListOf<DWType>()
                    for (i in 0 until refreshed.length()) {
                        val o = refreshed.getJSONObject(i)
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
                _statusMessage.value = "Imported and upsynced succesfully"
            } catch (e: Exception) {
                _statusMessage.value = "Import failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearStatusMessage() { _statusMessage.value = null }
}
