package com.phase3.tracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.phase3.tracker.data.ConnectivityObserver
import com.phase3.tracker.data.SupabaseClient
import com.phase3.tracker.model.Activity
import com.phase3.tracker.model.PHDDoorConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class PHDViewModel(application: Application) : AndroidViewModel(application) {

    private val supabase = SupabaseClient()
    private val connectivityObserver = ConnectivityObserver(application)

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

    override fun onCleared() {
        super.onCleared()
        connectivityObserver.unregister()
    }
}
