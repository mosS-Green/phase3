package com.phase3.tracker.viewmodel

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.phase3.tracker.data.ExcelManager
import com.phase3.tracker.data.SupabaseClient
import com.phase3.tracker.model.Activity
import com.phase3.tracker.model.FlatStatus
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val supabase = SupabaseClient()
    private val excelManager = ExcelManager()

    private val _towers = MutableStateFlow<List<Tower>>(emptyList())
    val towers: StateFlow<List<Tower>> = _towers.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncDone = MutableStateFlow(false)
    val syncDone: StateFlow<Boolean> = _syncDone.asStateFlow()

    // Edit mode — persisted via SharedPreferences, default OFF
    private val prefs = application.getSharedPreferences("phase3_settings", Context.MODE_PRIVATE)
    private val _editMode = MutableStateFlow(prefs.getBoolean("edit_mode", false))
    val editMode: StateFlow<Boolean> = _editMode.asStateFlow()

    fun toggleEditMode() {
        val newValue = !_editMode.value
        _editMode.value = newValue
        prefs.edit().putBoolean("edit_mode", newValue).apply()
    }

    fun setEditMode(enabled: Boolean) {
        _editMode.value = enabled
        prefs.edit().putBoolean("edit_mode", enabled).apply()
    }

    // Filter states
    enum class StatusFilter { COMPLETED, ONGOING, EMPTY }

    private val _selectedStatusFilters = MutableStateFlow(setOf(StatusFilter.ONGOING))
    val selectedStatusFilters: StateFlow<Set<StatusFilter>> = _selectedStatusFilters.asStateFlow()

    private val _selectedCategories = MutableStateFlow<Set<String>>(emptySet())
    val selectedCategories: StateFlow<Set<String>> = _selectedCategories.asStateFlow()

    private val _selectedContractor = MutableStateFlow("All")
    val selectedContractor: StateFlow<String> = _selectedContractor.asStateFlow()

    // ── Debounce system for batching flat status updates ─────────
    private data class PendingStatus(
        val activityId: Int,
        val flatNumber: Int,
        val status: String,
        val percentage: Int
    )

    private val pendingUpdates = mutableListOf<PendingStatus>()
    private val pendingLock = Any()
    private var debounceJob: Job? = null

    companion object {
        private const val DEBOUNCE_MS = 500L
    }

    init {
        loadFromSupabase()
    }

    // ── Data Loading ─────────────────────────────────────────────

    fun loadFromSupabase() {
        viewModelScope.launch {
            _isDownloading.value = true
            try {
                val towersResult = supabase.fetchTowers()
                val towerRows = towersResult.getOrThrow()

                val towerList = mutableListOf<Tower>()
                for (i in 0 until towerRows.length()) {
                    val t = towerRows.getJSONObject(i)
                    val towerId = t.getInt("id")
                    val towerName = t.getString("name")
                    val sheetName = t.getString("sheet_name")

                    // Fetch activities for this tower
                    val activitiesResult = supabase.fetchActivities(towerId)
                    val activityRows = activitiesResult.getOrThrow()

                    val activities = mutableListOf<Activity>()
                    for (j in 0 until activityRows.length()) {
                        val a = activityRows.getJSONObject(j)
                        val activityId = a.getInt("id")

                        // Fetch flat statuses for this activity
                        val statusesResult = supabase.fetchFlatStatuses(activityId)
                        val statusRows = statusesResult.getOrThrow()

                        val statuses = mutableMapOf<Int, FlatStatus>()
                        val percentages = mutableMapOf<Int, Int>()

                        // Initialize all flats to empty
                        Activity.FLAT_NUMBERS.forEach { flatNum ->
                            statuses[flatNum] = FlatStatus.EMPTY
                            percentages[flatNum] = 0
                        }

                        // Fill in actual data from DB
                        for (k in 0 until statusRows.length()) {
                            val s = statusRows.getJSONObject(k)
                            val flatNum = s.getInt("flat_number")
                            val statusStr = s.getString("status")
                            val pct = s.getInt("percentage")

                            statuses[flatNum] = when (statusStr) {
                                "complete" -> FlatStatus.COMPLETE
                                "wip" -> FlatStatus.WIP
                                else -> FlatStatus.EMPTY
                            }
                            percentages[flatNum] = pct
                        }

                        val groupName = a.getString("group_name")
                        val usePercentage = a.getBoolean("use_percentage")

                        activities.add(
                            Activity(
                                id = activityId,
                                towerId = towerId,
                                name = a.getString("name"),
                                sortOrder = a.getInt("sort_order"),
                                groupName = groupName,
                                groupIndex = Activity.groupIndexFor(groupName),
                                contractor = a.optString("contractor", ""),
                                categories = Activity.parseCategories(a.optString("categories", "")),
                                usePercentage = usePercentage,
                                isFloorBased = a.getBoolean("is_floor_based"),
                                weightage = a.getInt("weightage"),
                                statuses = statuses,
                                percentages = if (usePercentage) percentages else mutableMapOf()
                            )
                        )
                    }

                    towerList.add(Tower(id = towerId, name = towerName, sheetName = sheetName, activities = activities))
                }

                _towers.value = towerList
            } catch (e: Exception) {
                _statusMessage.value = "Load failed: ${e.message}"
            } finally {
                _isDownloading.value = false
                _isLoading.value = false
            }
        }
    }

    /** Refresh data from Supabase (pull button) */
    fun refreshFromSupabase() {
        loadFromSupabase()
    }

    // ── Flat Status Updates ──────────────────────────────────────

    fun toggleFlatStatus(towerIndex: Int, activityIndex: Int, flatNumber: Int) {
        val towersList = _towers.value.toMutableList()
        val tower = towersList.getOrNull(towerIndex) ?: return
        val activity = tower.activities.getOrNull(activityIndex) ?: return

        if (activity.isFloorBased) {
            val floor = flatNumber / 100
            toggleFloorStatus(towerIndex, activityIndex, floor)
            return
        }

        val currentStatus = activity.statuses[flatNumber] ?: FlatStatus.EMPTY
        val newStatus = currentStatus.next()

        val newStatuses = activity.statuses.toMutableMap()
        newStatuses[flatNumber] = newStatus
        val newActivity = activity.copy(statuses = newStatuses)

        val newActivities = tower.activities.toMutableList()
        newActivities[activityIndex] = newActivity
        towersList[towerIndex] = tower.copy(activities = newActivities)
        _towers.value = towersList.toList()

        enqueueStatusUpdate(activity.id, flatNumber, newStatus.toDbValue(), 0)
    }

    fun toggleFloorStatus(towerIndex: Int, activityIndex: Int, floor: Int) {
        val towersList = _towers.value.toMutableList()
        val tower = towersList.getOrNull(towerIndex) ?: return
        val activity = tower.activities.getOrNull(activityIndex) ?: return

        val flatsOnFloor = (1..4).map { floor * 100 + it }
        val currentStatuses = flatsOnFloor.map { activity.statuses[it] ?: FlatStatus.EMPTY }

        val allSame = currentStatuses.distinct().size == 1
        val targetStatus = if (allSame) {
            currentStatuses.first().next()
        } else {
            currentStatuses.groupBy { it }.maxByOrNull { it.value.size }?.key ?: FlatStatus.EMPTY
        }

        val newStatuses = activity.statuses.toMutableMap()
        for (flatNum in flatsOnFloor) {
            newStatuses[flatNum] = targetStatus
            enqueueStatusUpdate(activity.id, flatNum, targetStatus.toDbValue(), 0)
        }

        val newActivity = activity.copy(statuses = newStatuses)
        val newActivities = tower.activities.toMutableList()
        newActivities[activityIndex] = newActivity
        towersList[towerIndex] = tower.copy(activities = newActivities)
        _towers.value = towersList.toList()
    }

    fun updateFlatPercentage(towerIndex: Int, activityIndex: Int, flatNumber: Int, percentage: Int) {
        val towersList = _towers.value.toMutableList()
        val tower = towersList.getOrNull(towerIndex) ?: return
        val activity = tower.activities.getOrNull(activityIndex) ?: return

        val clamped = percentage.coerceIn(0, 100)
        val newPercentages = activity.percentages.toMutableMap()

        if (activity.isFloorBased) {
            val floor = flatNumber / 100
            val flatsOnFloor = (1..4).map { floor * 100 + it }
            for (flatNum in flatsOnFloor) {
                newPercentages[flatNum] = clamped
                enqueueStatusUpdate(activity.id, flatNum, "empty", clamped)
            }
        } else {
            newPercentages[flatNumber] = clamped
            enqueueStatusUpdate(activity.id, flatNumber, "empty", clamped)
        }

        val newActivity = activity.copy(percentages = newPercentages)
        val newActivities = tower.activities.toMutableList()
        newActivities[activityIndex] = newActivity
        towersList[towerIndex] = tower.copy(activities = newActivities)
        _towers.value = towersList.toList()
    }

    private fun enqueueStatusUpdate(activityId: Int, flatNumber: Int, status: String, percentage: Int) {
        synchronized(pendingLock) {
            // Replace any existing update for same activity+flat
            pendingUpdates.removeAll { it.activityId == activityId && it.flatNumber == flatNumber }
            pendingUpdates.add(PendingStatus(activityId, flatNumber, status, percentage))
        }
        _isSyncing.value = true
        _syncDone.value = false

        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            viewModelScope.launch { flushPendingUpdates() }
        }
    }

    private suspend fun flushPendingUpdates() {
        val updates: List<PendingStatus>
        synchronized(pendingLock) {
            updates = pendingUpdates.toList()
            pendingUpdates.clear()
        }

        if (updates.isEmpty()) {
            _isSyncing.value = false
            return
        }

        withContext(NonCancellable + Dispatchers.IO) {
            try {
                val jsonArray = JSONArray()
                for (u in updates) {
                    jsonArray.put(JSONObject().apply {
                        put("activity_id", u.activityId)
                        put("flat_number", u.flatNumber)
                        put("status", u.status)
                        put("percentage", u.percentage)
                    })
                }

                val result = supabase.upsertFlatStatuses(jsonArray)
                if (result.isFailure) {
                    _statusMessage.value = "Sync failed: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Sync error: ${e.message}"
            } finally {
                _isSyncing.value = false
                _syncDone.value = true
                viewModelScope.launch {
                    delay(3000)
                    _syncDone.value = false
                }
            }
        }
    }

    // ── Activity CRUD ────────────────────────────────────────────

    fun addActivity(
        towerIndex: Int,
        activityName: String,
        contractor: String = "",
        categories: List<String> = emptyList(),
        groupName: String = "",
        usePercentage: Boolean = false,
        weightage: Int = 5
    ) {
        viewModelScope.launch {
            val towersList = _towers.value.toMutableList()
            val tower = towersList.getOrNull(towerIndex) ?: return@launch
            val resolvedGroup = groupName.ifBlank { "Other" }
            val isFloorBased = resolvedGroup.contains("Common", ignoreCase = true)
            val nextSortOrder = tower.activities.maxOfOrNull { it.sortOrder + 1 } ?: 0

            withContext(Dispatchers.IO) {
                // Insert into current tower
                val payload = JSONObject().apply {
                    put("tower_id", tower.id)
                    put("name", activityName)
                    put("group_name", resolvedGroup)
                    put("contractor", contractor)
                    put("categories", Activity.serializeCategories(categories))
                    put("weightage", weightage)
                    put("use_percentage", usePercentage)
                    put("is_floor_based", isFloorBased)
                    put("sort_order", nextSortOrder)
                }

                val result = supabase.insertActivity(payload)
                val row = result.getOrNull()?.optJSONObject(0)
                if (row == null) {
                    _statusMessage.value = "Failed to add activity"
                    return@withContext
                }
                val newId = row.getInt("id")

                // Create empty flat statuses
                val statuses = mutableMapOf<Int, FlatStatus>()
                val percentages = mutableMapOf<Int, Int>()
                Activity.FLAT_NUMBERS.forEach {
                    statuses[it] = FlatStatus.EMPTY
                    if (usePercentage) percentages[it] = 0
                }

                // Batch insert flat statuses
                val flatArray = JSONArray()
                Activity.FLAT_NUMBERS.forEach { flatNum ->
                    flatArray.put(JSONObject().apply {
                        put("activity_id", newId)
                        put("flat_number", flatNum)
                        put("status", "empty")
                        put("percentage", 0)
                    })
                }
                supabase.upsertFlatStatuses(flatArray)

                val newActivity = Activity(
                    id = newId, towerId = tower.id, name = activityName,
                    sortOrder = nextSortOrder, groupName = resolvedGroup,
                    groupIndex = Activity.groupIndexFor(resolvedGroup),
                    contractor = contractor, categories = categories,
                    usePercentage = usePercentage, isFloorBased = isFloorBased,
                    weightage = weightage, statuses = statuses, percentages = percentages
                )

                val newActivities = tower.activities.toMutableList().apply { add(newActivity) }
                towersList[towerIndex] = tower.copy(activities = newActivities)

                // ── Mirror to the other tower ──
                val otherTowerIndex = if (towerIndex == 0) 1 else 0
                val otherTower = towersList.getOrNull(otherTowerIndex)
                if (otherTower != null) {
                    val mirrorPayload = JSONObject(payload.toString()).apply {
                        put("tower_id", otherTower.id)
                    }
                    val mirrorResult = supabase.insertActivity(mirrorPayload)
                    val mirrorRow = mirrorResult.getOrNull()?.optJSONObject(0)
                    if (mirrorRow != null) {
                        val mirrorId = mirrorRow.getInt("id")
                        val mirrorStatuses = mutableMapOf<Int, FlatStatus>()
                        val mirrorPercentages = mutableMapOf<Int, Int>()
                        Activity.FLAT_NUMBERS.forEach {
                            mirrorStatuses[it] = FlatStatus.EMPTY
                            if (usePercentage) mirrorPercentages[it] = 0
                        }

                        val mirrorFlatArray = JSONArray()
                        Activity.FLAT_NUMBERS.forEach { flatNum ->
                            mirrorFlatArray.put(JSONObject().apply {
                                put("activity_id", mirrorId)
                                put("flat_number", flatNum)
                                put("status", "empty")
                                put("percentage", 0)
                            })
                        }
                        supabase.upsertFlatStatuses(mirrorFlatArray)

                        val mirrorActivity = newActivity.copy(
                            id = mirrorId, towerId = otherTower.id,
                            statuses = mirrorStatuses, percentages = mirrorPercentages
                        )
                        val newOtherActivities = otherTower.activities.toMutableList().apply { add(mirrorActivity) }
                        towersList[otherTowerIndex] = otherTower.copy(activities = newOtherActivities)
                    }
                }

                _towers.value = towersList.toList()
            }
            _statusMessage.value = "Activity added: $activityName"
        }
    }

    fun renameActivity(
        towerIndex: Int,
        activityIndex: Int,
        newName: String,
        contractor: String = "",
        categories: List<String> = emptyList(),
        groupName: String = "",
        usePercentage: Boolean = false,
        weightage: Int = 5
    ) {
        viewModelScope.launch {
            val towersList = _towers.value.toMutableList()
            val tower = towersList.getOrNull(towerIndex) ?: return@launch
            val activity = tower.activities.getOrNull(activityIndex) ?: return@launch
            val oldName = activity.name
            val resolvedGroup = groupName.ifBlank { activity.groupName }
            val isFloorBased = resolvedGroup.contains("Common", ignoreCase = true)

            withContext(Dispatchers.IO) {
                val payload = JSONObject().apply {
                    put("name", newName)
                    put("group_name", resolvedGroup)
                    put("contractor", contractor)
                    put("categories", Activity.serializeCategories(categories))
                    put("weightage", weightage)
                    put("use_percentage", usePercentage)
                    put("is_floor_based", isFloorBased)
                }

                supabase.updateActivity(activity.id, payload)

                val newActivity = activity.copy(
                    name = newName, contractor = contractor, categories = categories,
                    groupName = resolvedGroup, groupIndex = Activity.groupIndexFor(resolvedGroup),
                    usePercentage = usePercentage, isFloorBased = isFloorBased, weightage = weightage
                )
                val newActivities = tower.activities.toMutableList()
                newActivities[activityIndex] = newActivity
                towersList[towerIndex] = tower.copy(activities = newActivities)

                // ── Mirror rename to the other tower ──
                val otherTowerIndex = if (towerIndex == 0) 1 else 0
                val otherTower = towersList.getOrNull(otherTowerIndex)
                if (otherTower != null) {
                    val otherIdx = otherTower.activities.indexOfFirst { it.name == oldName }
                    if (otherIdx >= 0) {
                        val otherActivity = otherTower.activities[otherIdx]
                        supabase.updateActivity(otherActivity.id, payload)
                        val updatedOther = otherActivity.copy(
                            name = newName, contractor = contractor, categories = categories,
                            groupName = resolvedGroup, groupIndex = Activity.groupIndexFor(resolvedGroup),
                            usePercentage = usePercentage, isFloorBased = isFloorBased, weightage = weightage
                        )
                        val newOtherActivities = otherTower.activities.toMutableList()
                        newOtherActivities[otherIdx] = updatedOther
                        towersList[otherTowerIndex] = otherTower.copy(activities = newOtherActivities)
                    }
                }

                _towers.value = towersList.toList()
            }
            _statusMessage.value = "Updated: $newName"
        }
    }

    fun deleteActivity(towerIndex: Int, activityIndex: Int) {
        viewModelScope.launch {
            val towersList = _towers.value.toMutableList()
            val tower = towersList.getOrNull(towerIndex) ?: return@launch
            val activity = tower.activities.getOrNull(activityIndex) ?: return@launch
            val activityName = activity.name

            withContext(Dispatchers.IO) {
                supabase.deleteActivity(activity.id)

                val newActivities = tower.activities.toMutableList().apply { removeAt(activityIndex) }
                towersList[towerIndex] = tower.copy(activities = newActivities)

                // ── Mirror deletion to the other tower ──
                val otherTowerIndex = if (towerIndex == 0) 1 else 0
                val otherTower = towersList.getOrNull(otherTowerIndex)
                if (otherTower != null) {
                    val otherIdx = otherTower.activities.indexOfFirst { it.name == activityName }
                    if (otherIdx >= 0) {
                        val otherActivity = otherTower.activities[otherIdx]
                        supabase.deleteActivity(otherActivity.id)
                        val newOtherActivities = otherTower.activities.toMutableList().apply { removeAt(otherIdx) }
                        towersList[otherTowerIndex] = otherTower.copy(activities = newOtherActivities)
                    }
                }

                _towers.value = towersList.toList()
            }
            _statusMessage.value = "Deleted: $activityName"
        }
    }

    // ── XLSX Export ──────────────────────────────────────────────

    fun saveExcelToDownloads(): Uri? {
        val app = getApplication<Application>()
        try {
            val towers = _towers.value
            if (towers.isEmpty()) return null

            val fileName = "Phase3_${System.currentTimeMillis()}.xlsx"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    app.contentResolver.openOutputStream(it)?.use { out ->
                        val wb = excelManager.buildWorkbook(towers)
                        excelManager.writeWorkbook(wb, out)
                    }
                }
                _statusMessage.value = "Saved to Downloads: $fileName"
                return uri
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val outFile = File(downloadsDir, fileName)
                outFile.outputStream().use { out ->
                    val wb = excelManager.buildWorkbook(towers)
                    excelManager.writeWorkbook(wb, out)
                }
                _statusMessage.value = "Saved to Downloads: $fileName"
                return Uri.fromFile(outFile)
            }
        } catch (e: Exception) {
            _statusMessage.value = "Save failed: ${e.message}"
            return null
        }
    }

    // ── Filters ─────────────────────────────────────────────────

    fun toggleStatusFilter(filter: StatusFilter) {
        val current = _selectedStatusFilters.value.toMutableSet()
        if (current.contains(filter)) current.remove(filter) else current.add(filter)
        _selectedStatusFilters.value = current
    }

    fun toggleCategoryFilter(category: String) {
        val current = _selectedCategories.value.toMutableSet()
        if (current.contains(category)) current.remove(category) else current.add(category)
        _selectedCategories.value = current
    }

    fun setContractorFilter(contractor: String) {
        _selectedContractor.value = contractor
    }

    fun getAllContractors(): List<String> {
        return _towers.value.flatMap { it.activities }.map { it.contractor }
            .filter { it.isNotBlank() }.distinct().sorted()
    }

    fun getAllGroupNames(): List<String> {
        val fromData = _towers.value.flatMap { it.activities }.map { it.groupName }
            .filter { it.isNotBlank() }.distinct()
        val defaults = Activity.DEFAULT_GROUP_NAMES
        return (fromData + defaults).distinct().sorted()
    }

    fun getFilteredActivities(tower: Tower): List<Activity> {
        val statusFilters = _selectedStatusFilters.value
        val categoryFilters = _selectedCategories.value
        val contractor = _selectedContractor.value

        return tower.activities.filter { activity ->
            val statusMatch = statusFilters.isEmpty() || statusFilters.any { filter ->
                when (filter) {
                    StatusFilter.COMPLETED -> activity.isFullyComplete
                    StatusFilter.ONGOING -> activity.isOngoing
                    StatusFilter.EMPTY -> activity.isFullyEmpty
                }
            }
            val categoryMatch = categoryFilters.isEmpty() || activity.categories.any { it in categoryFilters }
            val contractorMatch = contractor == "All" || activity.contractor.equals(contractor, ignoreCase = true)
            statusMatch && categoryMatch && contractorMatch
        }.sortedBy { it.name.lowercase() }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
