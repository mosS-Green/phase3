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
import com.phase3.tracker.data.ConnectivityObserver
import com.phase3.tracker.model.Activity
import com.phase3.tracker.model.DWRoom
import com.phase3.tracker.model.DWType
import com.phase3.tracker.model.Tower
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
    private val connectivityObserver = ConnectivityObserver(application)

    // Global door/window types catalog
    private val _dwTypes = MutableStateFlow<List<DWType>>(emptyList())
    val dwTypes: StateFlow<List<DWType>> = _dwTypes.asStateFlow()

    // Rooms for current tower + column
    private val _rooms = MutableStateFlow<List<DWRoom>>(emptyList())
    val rooms: StateFlow<List<DWRoom>> = _rooms.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    // All rooms for a tower (used by Unit Type screen)
    private val _allTowerRooms = MutableStateFlow<Map<String, List<DWRoom>>>(emptyMap())
    val allTowerRooms: StateFlow<Map<String, List<DWRoom>>> = _allTowerRooms.asStateFlow()

    // Debounce for status updates
    private data class PendingDWStatus(
        val roomId: Int, val typeId: Int, val flatNumber: Int, val isDone: Boolean
    )
    private val pendingUpdates = mutableListOf<PendingDWStatus>()
    private val pendingLock = Any()
    private var debounceJob: Job? = null

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

    private fun serializeDWTypes(types: List<DWType>): String {
        val arr = JSONArray()
        for (t in types) {
            arr.put(JSONObject().apply {
                put("id", t.id)
                put("name", t.name)
                put("kind", t.kind)
                put("height", t.height)
                put("breadth", t.breadth)
            })
        }
        return arr.toString()
    }

    private fun deserializeDWTypes(jsonStr: String): List<DWType> {
        val list = mutableListOf<DWType>()
        val arr = JSONArray(jsonStr)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(DWType(
                id = o.getInt("id"),
                name = o.getString("name"),
                kind = o.getString("kind"),
                height = o.optDouble("height", 0.0),
                breadth = o.optDouble("breadth", 0.0)
            ))
        }
        return list
    }

    private fun serializeDWRooms(roomsMap: Map<String, List<DWRoom>>): String {
        val obj = JSONObject()
        for ((colType, rooms) in roomsMap) {
            val roomsArr = JSONArray()
            for (room in rooms) {
                roomsArr.put(JSONObject().apply {
                    put("id", room.id)
                    put("tower_id", room.towerId)
                    put("column_type", room.columnType)
                    put("name", room.name)
                    put("sort_order", room.sortOrder)
                    
                    val typesArr = JSONArray()
                    for (t in room.types) {
                        typesArr.put(JSONObject().apply {
                            put("id", t.id)
                            put("name", t.name)
                            put("kind", t.kind)
                            put("height", t.height)
                            put("breadth", t.breadth)
                        })
                    }
                    put("types", typesArr)
                    
                    val flatStatusesObj = JSONObject()
                    for ((flatNum, typeMap) in room.flatStatuses) {
                        val typeMapObj = JSONObject()
                        for ((typeId, isDone) in typeMap) {
                            typeMapObj.put(typeId.toString(), isDone)
                        }
                        flatStatusesObj.put(flatNum.toString(), typeMapObj)
                    }
                    put("flat_statuses", flatStatusesObj)
                })
            }
            obj.put(colType, roomsArr)
        }
        return obj.toString()
    }

    private fun deserializeDWRooms(jsonStr: String): Map<String, List<DWRoom>> {
        val map = mutableMapOf<String, List<DWRoom>>()
        val obj = JSONObject(jsonStr)
        val keys = obj.keys()
        while (keys.hasNext()) {
            val colType = keys.next()
            val roomsArr = obj.getJSONArray(colType)
            val roomsList = mutableListOf<DWRoom>()
            for (i in 0 until roomsArr.length()) {
                val rObj = roomsArr.getJSONObject(i)
                val id = rObj.getInt("id")
                val towerId = rObj.getInt("tower_id")
                val columnType = rObj.getString("column_type")
                val name = rObj.getString("name")
                val sortOrder = rObj.getInt("sort_order")
                
                val typesArr = rObj.getJSONArray("types")
                val types = mutableListOf<DWType>()
                for (j in 0 until typesArr.length()) {
                    val tObj = typesArr.getJSONObject(j)
                    types.add(DWType(
                        id = tObj.getInt("id"),
                        name = tObj.getString("name"),
                        kind = tObj.getString("kind"),
                        height = tObj.optDouble("height", 0.0),
                        breadth = tObj.optDouble("breadth", 0.0)
                    ))
                }
                
                val flatStatusesObj = rObj.getJSONObject("flat_statuses")
                val flatStatuses = mutableMapOf<Int, MutableMap<Int, Boolean>>()
                val flatKeys = flatStatusesObj.keys()
                while (flatKeys.hasNext()) {
                    val flatStr = flatKeys.next()
                    val flatNum = flatStr.toInt()
                    val typeMapObj = flatStatusesObj.getJSONObject(flatStr)
                    val typeMap = mutableMapOf<Int, Boolean>()
                    val typeKeys = typeMapObj.keys()
                    while (typeKeys.hasNext()) {
                        val typeIdStr = typeKeys.next()
                        val typeId = typeIdStr.toInt()
                        typeMap[typeId] = typeMapObj.getBoolean(typeIdStr)
                    }
                    flatStatuses[flatNum] = typeMap
                }
                
                roomsList.add(DWRoom(
                    id = id,
                    towerId = towerId,
                    columnType = columnType,
                    name = name,
                    sortOrder = sortOrder,
                    types = types,
                    flatStatuses = flatStatuses
                ))
            }
            map[colType] = roomsList
        }
        return map
    }

    private fun serializePendingUpdates(updates: List<PendingDWStatus>): String {
        val arr = JSONArray()
        for (u in updates) {
            arr.put(JSONObject().apply {
                put("roomId", u.roomId)
                put("typeId", u.typeId)
                put("flatNumber", u.flatNumber)
                put("isDone", u.isDone)
            })
        }
        return arr.toString()
    }

    private fun deserializePendingUpdates(jsonStr: String): List<PendingDWStatus> {
        val list = mutableListOf<PendingDWStatus>()
        val arr = JSONArray(jsonStr)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(PendingDWStatus(
                roomId = o.getInt("roomId"),
                typeId = o.getInt("typeId"),
                flatNumber = o.getInt("flatNumber"),
                isDone = o.getBoolean("isDone")
            ))
        }
        return list
    }

    private suspend fun buildDWRooms(roomRows: JSONArray): List<DWRoom> {
        val roomList = mutableListOf<DWRoom>()
        if (roomRows.length() == 0) return roomList

        val roomIds = (0 until roomRows.length()).map { roomRows.getJSONObject(it).getInt("id") }

        // Fetch assigned types in batch (small dataset, safe under row limit)
        // and statuses per-room in parallel (each room ~132×N rows, safe under limit)
        val roomTypesDeferred = viewModelScope.async { supabase.fetchDWRoomTypesByRoomIds(roomIds).getOrElse { JSONArray() } }
        val statusDeferreds = roomIds.map { roomId ->
            viewModelScope.async { roomId to supabase.fetchDWStatuses(roomId).getOrElse { JSONArray() } }
        }

        val rtRows = roomTypesDeferred.await()
        val statusResults = statusDeferreds.map { it.await() }

        // Group types by room_id
        val typesByRoomId = mutableMapOf<Int, MutableList<DWType>>()
        for (i in 0 until rtRows.length()) {
            val rt = rtRows.getJSONObject(i)
            val roomId = rt.getInt("room_id")
            val t = rt.optJSONObject("dw_types") ?: continue
            typesByRoomId.getOrPut(roomId) { mutableListOf() }.add(
                DWType(
                    id = t.getInt("id"),
                    name = t.getString("name"),
                    kind = t.getString("kind"),
                    height = t.optDouble("height", 0.0),
                    breadth = t.optDouble("breadth", 0.0)
                )
            )
        }

        // Group statuses by room_id and then flat_number and type_id
        val statusesByRoomId = mutableMapOf<Int, MutableMap<Int, MutableMap<Int, Boolean>>>()
        for ((roomId, stRows) in statusResults) {
            for (i in 0 until stRows.length()) {
                val s = stRows.getJSONObject(i)
                val flatNum = s.getInt("flat_number")
                val typeId = s.getInt("type_id")
                val isDone = s.getBoolean("is_done")
                statusesByRoomId.getOrPut(roomId) { mutableMapOf() }
                    .getOrPut(flatNum) { mutableMapOf() }[typeId] = isDone
            }
        }

        for (i in 0 until roomRows.length()) {
            val r = roomRows.getJSONObject(i)
            val roomId = r.getInt("id")
            val towerId = r.getInt("tower_id")
            val columnType = r.getString("column_type")
            val roomName = r.getString("name")
            val sortOrder = r.getInt("sort_order")

            roomList.add(DWRoom(
                id = roomId,
                towerId = towerId,
                columnType = columnType,
                name = roomName,
                sortOrder = sortOrder,
                types = typesByRoomId[roomId] ?: emptyList(),
                flatStatuses = statusesByRoomId[roomId] ?: mutableMapOf()
            ))
        }

        return roomList
    }

    init {
        // Load from cache first for instant startup
        val cachedTypes = loadCache("dw_types_cache.json")
        if (cachedTypes != null) {
            try {
                _dwTypes.value = deserializeDWTypes(cachedTypes)
            } catch (e: Exception) {
                // Ignore
            }
        }
        val cachedRooms = loadCache("dw_rooms_cache.json")
        if (cachedRooms != null) {
            try {
                _allTowerRooms.value = deserializeDWRooms(cachedRooms)
            } catch (e: Exception) {
                // Ignore
            }
        }

        // Load pending sync queue
        val cachedSync = loadCache("dw_pending_sync.json")
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
            } catch (e: Exception) {
                // Ignore
            }
        }

        loadTypes()

        // Auto-resync when connectivity returns
        viewModelScope.launch {
            var wasOffline = !connectivityObserver.isOnline.value
            connectivityObserver.isOnline.collect { online ->
                if (online && wasOffline) {
                    _statusMessage.value = "Back online — syncing…"
                    loadTypes()
                    if (pendingUpdates.isNotEmpty()) {
                        viewModelScope.launch { flushPendingUpdates() }
                    }
                }
                wasOffline = !online
            }
        }
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
                saveCache("dw_types_cache.json", serializeDWTypes(types))
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
                val roomList = buildDWRooms(roomRows)
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
            saveCache("dw_pending_sync.json", serializePendingUpdates(pendingUpdates))
        }
        _isSyncing.value = true

        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(500)
            flushPendingUpdates()
        }
    }

    private suspend fun flushPendingUpdates() {
        val updatesToSend: List<PendingDWStatus>
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
                        put("room_id", u.roomId)
                        put("type_id", u.typeId)
                        put("flat_number", u.flatNumber)
                        put("is_done", u.isDone)
                    })
                }
                val result = supabase.upsertDWStatuses(arr)
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
                // Remove only the updates we successfully sent
                pendingUpdates.removeAll { item ->
                    updatesToSend.any { sent ->
                        sent.roomId == item.roomId &&
                        sent.typeId == item.typeId &&
                        sent.flatNumber == item.flatNumber &&
                        sent.isDone == item.isDone
                    }
                }
                saveCache("dw_pending_sync.json", serializePendingUpdates(pendingUpdates))
            }
            _isSyncing.value = false
        } else {
            _isSyncing.value = false
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
            val statusMap = room.flatStatuses[flatNumber] ?: continue
            for (type in room.types) {
                if (!statusMap.containsKey(type.id)) continue
                val w = if (type.isDoor) 3 else 1
                totalWeight += w
                val isDone = statusMap[type.id] ?: false
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

    // ── Cell-reading helpers (handles any cell type) ─────────────

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
                            it.setCellValue(tower.name.filter { c -> c.isDigit() }.toDouble())
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
                                it.setCellValue("C")
                                it.cellStyle = doneStyle
                            } else if (rowStyle != null) {
                                it.cellStyle = rowStyle
                            }
                        }
                        
                        // SHUTTER status
                        row.createCell(8).also {
                            if (shutterDone) {
                                it.setCellValue("C")
                                it.cellStyle = doneStyle
                            } else if (rowStyle != null) {
                                it.cellStyle = rowStyle
                            }
                        }
                        
                        // GLASS status
                        row.createCell(9).also {
                            if (glassDone) {
                                it.setCellValue("C")
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
            _isImporting.value = true
            try {
                withContext(Dispatchers.IO) {
                    val wb = XSSFWorkbook(inputStream)

                    // Try matching sheets by sheetName first, then by tower number
                    for (tower in towers) {
                        val sheet = wb.getSheet(tower.sheetName)
                            ?: wb.getSheet("Tower ${tower.name.filter { it.isDigit() }}")
                            ?: wb.getSheet(tower.name)
                            ?: continue

                        // key: roomName -> list of (typeName, kind, breadth, height, flatNum, frameDone, shutterDone, glassDone)
                        data class DWRow(val typeName: String, val kind: String, val breadth: Double, val height: Double, val flatNum: Int, val frameDone: Boolean, val shutterDone: Boolean, val glassDone: Boolean)
                        val roomRows = mutableMapOf<String, MutableList<DWRow>>()

                        for (r in 1..sheet.lastRowNum) {
                            val row = sheet.getRow(r) ?: continue
                            // cols: 0=Tower, 1=FlatNo, 2=RoomName, 3=TypeName, 4=D/W, 5=W, 6=H, 7=FRAME, 8=SHUTTER, 9=GLASS
                            val roomName = cellString(row.getCell(2))?.takeIf { it.isNotBlank() } ?: continue
                            val typeName = cellString(row.getCell(3))?.takeIf { it.isNotBlank() } ?: continue
                            val kindRaw  = cellString(row.getCell(4))?.uppercase() ?: "W"
                            val kind     = if (kindRaw == "D") "door" else "window"
                            val breadth  = cellDouble(row.getCell(5))
                            val height   = cellDouble(row.getCell(6))
                            val flatNum  = cellDouble(row.getCell(1)).toInt().takeIf { it > 0 } ?: continue
                            
                            val frameRaw   = cellString(row.getCell(7)) ?: ""
                            val shutterRaw = cellString(row.getCell(8)) ?: ""
                            val glassRaw   = cellString(row.getCell(9)) ?: ""
                            
                            val frameDone   = frameRaw.equals("Y", ignoreCase = true) || frameRaw.equals("C", ignoreCase = true)
                            val shutterDone = shutterRaw.equals("Y", ignoreCase = true) || shutterRaw.equals("C", ignoreCase = true)
                            val glassDone   = glassRaw.equals("Y", ignoreCase = true) || glassRaw.equals("C", ignoreCase = true)

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
                            
                            // Delete any existing rooms that are no longer in the Excel file
                            for ((existingName, existingId) in existingRoomIds) {
                                if (!roomRows.containsKey(existingName)) {
                                    supabase.deleteDWRoom(existingId)
                                }
                            }
                            
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

                                // Delete old statuses for this room to ensure removed combinations are cleared
                                supabase.deleteDWStatusesForRoom(roomId)

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
                _isImporting.value = false
            }
        }
    }

    fun clearStatusMessage() { _statusMessage.value = null }

    // ── Load all rooms for a tower (for Unit Type screen) ────────

    fun loadAllRoomsForTower(towerId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val roomRows = supabase.fetchAllDWRooms(towerId).getOrThrow()
                val allRooms = buildDWRooms(roomRows)
                val result = allRooms.groupBy { it.columnType }
                val resultMap = mutableMapOf<String, List<DWRoom>>()
                listOf("frame", "shutter", "glass").forEach { col ->
                    resultMap[col] = result[col] ?: emptyList()
                }
                _allTowerRooms.value = resultMap
                saveCache("dw_rooms_cache.json", serializeDWRooms(resultMap))
            } catch (e: Exception) {
                _statusMessage.value = "Load failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Toggle DW status for a specific room/type/flat, updating allTowerRooms state */
    fun toggleDWStatusInAllRooms(roomId: Int, typeId: Int, flatNumber: Int) {
        val current = _allTowerRooms.value
        var found = false
        var newIsDone = false

        // Build a fully new immutable structure so Compose can detect the change
        val updated = current.mapValues { (_, rooms) ->
            rooms.map { room ->
                if (room.id == roomId) {
                    found = true
                    val flatMap = room.flatStatuses.toMutableMap()
                    val typeMap = (flatMap[flatNumber] ?: mutableMapOf()).toMutableMap()
                    val curVal = typeMap[typeId] ?: false
                    newIsDone = !curVal
                    typeMap[typeId] = newIsDone
                    flatMap[flatNumber] = typeMap
                    room.copy(flatStatuses = flatMap)
                } else room
            }
        }

        if (!found) return
        _allTowerRooms.value = updated

        // Also update _rooms if the same room is loaded there
        _rooms.value = _rooms.value.map { room ->
            if (room.id == roomId) {
                val flatMap = room.flatStatuses.toMutableMap()
                val typeMap = (flatMap[flatNumber] ?: mutableMapOf()).toMutableMap()
                typeMap[typeId] = newIsDone
                flatMap[flatNumber] = typeMap
                room.copy(flatStatuses = flatMap)
            } else room
        }

        // Enqueue for sync
        synchronized(pendingLock) {
            pendingUpdates.removeAll { it.roomId == roomId && it.typeId == typeId && it.flatNumber == flatNumber }
            pendingUpdates.add(PendingDWStatus(roomId, typeId, flatNumber, newIsDone))
            saveCache("dw_pending_sync.json", serializePendingUpdates(pendingUpdates))
        }
        _isSyncing.value = true
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(500)
            flushPendingUpdates()
        }
    }

    // ── Calculate per-flat completion for a column type ──────────

    /**
     * Calculate per-flat completion %.
     * Prefers cached [_allTowerRooms] data if available for the given tower.
     * Falls back to fresh API fetch otherwise.
     */
    suspend fun calculatePerFlatCompletion(towerId: Int, columnType: String): Map<Int, Float> {
        // Try using cached allTowerRooms first
        val cached = _allTowerRooms.value[columnType]
        val roomsToUse: List<DWRoom> = if (cached != null && cached.isNotEmpty() && cached.first().towerId == towerId) {
            cached
        } else {
            // Fall back to fresh API fetch
            fetchRoomsFromApi(towerId, columnType)
        }

        val result = mutableMapOf<Int, Float>()
        for (flatNum in Activity.FLAT_NUMBERS) {
            var totalWeight = 0
            var doneWeight = 0
            for (room in roomsToUse) {
                val statusMap = room.flatStatuses[flatNum] ?: continue
                for (type in room.types) {
                    if (!statusMap.containsKey(type.id)) continue
                    val w = if (type.isDoor) 3 else 1
                    totalWeight += w
                    val isDone = statusMap[type.id] ?: false
                    if (isDone) doneWeight += w
                }
            }
            result[flatNum] = if (totalWeight == 0) 0f else doneWeight.toFloat() / totalWeight * 100f
        }
        return result
    }

    /** Fetch rooms + types + statuses from API for a tower/column type */
    private suspend fun fetchRoomsFromApi(towerId: Int, columnType: String): List<DWRoom> {
        val roomsResult = supabase.fetchDWRooms(towerId, columnType)
        val roomRows = roomsResult.getOrElse { return emptyList() }
        return buildDWRooms(roomRows)
    }

    override fun onCleared() {
        super.onCleared()
        connectivityObserver.unregister()
    }
}
