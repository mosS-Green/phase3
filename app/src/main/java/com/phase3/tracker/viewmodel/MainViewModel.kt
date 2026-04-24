package com.phase3.tracker.viewmodel

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.phase3.tracker.data.CellUpdate
import com.phase3.tracker.data.ExcelManager
import com.phase3.tracker.data.GoogleSync
import com.phase3.tracker.model.Activity
import com.phase3.tracker.model.FlatStatus
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val excelManager = ExcelManager()
    private val googleSync = GoogleSync()

    private val _towers = MutableStateFlow<List<Tower>>(emptyList())
    val towers: StateFlow<List<Tower>> = _towers.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    // Sync queue tracking
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

    private val excelFile: File
        get() = File(getApplication<Application>().filesDir, "Ph-03 Tower Internal Finishing Work.xlsx")

    // ── Parallel sync system ────────────────────────────────────────
    // Debounces updates for 500ms, then fires all pending updates
    // using 5 concurrent individual updateCell calls (proven endpoint).
    private val pendingUpdates = mutableListOf<CellUpdate>()
    private val pendingLock = Any()
    private var debounceJob: Job? = null
    private val syncSemaphore = Semaphore(5) // max 5 concurrent HTTP calls

    companion object {
        private const val DEBOUNCE_MS = 500L
    }

    init {
        loadExcel()
    }

    private fun enqueueUpdate(sheetName: String, row: Int, col: Int, value: String) {
        synchronized(pendingLock) {
            pendingUpdates.add(CellUpdate(sheetName, row, col, value))
        }
        _isSyncing.value = true
        _syncDone.value = false

        // Only cancel the debounce delay — not any running sync
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            // Launch flush in a separate scope so debounce cancel doesn't kill it
            viewModelScope.launch { flushPendingUpdates() }
        }
    }

    /** Encode group name + usePercentage flag for Excel column A */
    private fun encodeGroupColumn(groupName: String, usePercentage: Boolean): String {
        return if (usePercentage) "$groupName|%" else groupName
    }

    private suspend fun flushPendingUpdates() {
        val updates: List<CellUpdate>
        synchronized(pendingLock) {
            updates = pendingUpdates.toList()
            pendingUpdates.clear()
        }

        if (updates.isEmpty()) {
            _isSyncing.value = false
            return
        }

        // NonCancellable: survive any future debounce cancellations
        withContext(NonCancellable + Dispatchers.IO) {
            var failCount = 0
            try {
                // Fire ALL updates concurrently, semaphore limits to 5 at a time
                val jobs = updates.map { update ->
                    async {
                        syncSemaphore.acquire()
                        try {
                            val result = googleSync.updateCell(
                                update.sheetName, update.row, update.col, update.value
                            )
                            if (result.isFailure) {
                                failCount++
                            }
                        } finally {
                            syncSemaphore.release()
                        }
                    }
                }
                jobs.awaitAll()

                if (failCount > 0) {
                    _statusMessage.value = "$failCount update(s) failed"
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

    fun loadExcel() {
        viewModelScope.launch {
            try {
                if (excelFile.exists()) {
                    // Load cached data immediately
                    withContext(Dispatchers.IO) {
                        val towers = excelFile.inputStream().use { stream ->
                            excelManager.loadWorkbook(stream)
                        }
                        _towers.value = towers
                    }
                    _isLoading.value = false
                    // Background sync from Google Sheets
                    downloadFromGoogleSheets(silent = true)
                } else {
                    // First boot: download from Google Sheets
                    _isLoading.value = true
                    downloadFromGoogleSheets(silent = false)
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error loading local data: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    fun toggleFlatStatus(towerIndex: Int, activityIndex: Int, flatNumber: Int) {
        val towersList = _towers.value.toMutableList()
        val tower = towersList.getOrNull(towerIndex) ?: return
        val activity = tower.activities.getOrNull(activityIndex) ?: return

        if (activity.isFloorBased) {
            // For floor-based, toggle all flats on the floor together
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
        
        val newTower = tower.copy(activities = newActivities)
        towersList[towerIndex] = newTower

        _towers.value = towersList.toList()

        val colIdx = ExcelManager.flatToColIndex(flatNumber)
        val value = newStatus.toExcelValue() ?: ""
        enqueueUpdate(tower.sheetName, activity.rowIndex, colIdx + 1, value)
        
        excelManager.updateStatus(tower.sheetName, activity.rowIndex, flatNumber, newStatus)
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
            
            val colIdx = ExcelManager.flatToColIndex(flatNum)
            val value = targetStatus.toExcelValue() ?: ""
            enqueueUpdate(tower.sheetName, activity.rowIndex, colIdx + 1, value)
            excelManager.updateStatus(tower.sheetName, activity.rowIndex, flatNum, targetStatus)
        }

        val newActivity = activity.copy(statuses = newStatuses)
        val newActivities = tower.activities.toMutableList()
        newActivities[activityIndex] = newActivity
        
        val newTower = tower.copy(activities = newActivities)
        towersList[towerIndex] = newTower

        _towers.value = towersList.toList()
    }

    fun updateFlatPercentage(towerIndex: Int, activityIndex: Int, flatNumber: Int, percentage: Int) {
        val towersList = _towers.value.toMutableList()
        val tower = towersList.getOrNull(towerIndex) ?: return
        val activity = tower.activities.getOrNull(activityIndex) ?: return

        val clamped = percentage.coerceIn(0, 100)
        val newPercentages = activity.percentages.toMutableMap()

        if (activity.isFloorBased) {
            // Update all 4 flats on the floor
            val floor = flatNumber / 100
            val flatsOnFloor = (1..4).map { floor * 100 + it }
            for (flatNum in flatsOnFloor) {
                newPercentages[flatNum] = clamped
                val colIdx = ExcelManager.flatToColIndex(flatNum)
                enqueueUpdate(tower.sheetName, activity.rowIndex, colIdx + 1, clamped.toString())
                excelManager.updatePercentage(tower.sheetName, activity.rowIndex, flatNum, clamped)
            }
        } else {
            newPercentages[flatNumber] = clamped
            val colIdx = ExcelManager.flatToColIndex(flatNumber)
            enqueueUpdate(tower.sheetName, activity.rowIndex, colIdx + 1, clamped.toString())
            excelManager.updatePercentage(tower.sheetName, activity.rowIndex, flatNumber, clamped)
        }

        val newActivity = activity.copy(percentages = newPercentages)
        val newActivities = tower.activities.toMutableList()
        newActivities[activityIndex] = newActivity

        val newTower = tower.copy(activities = newActivities)
        towersList[towerIndex] = newTower

        _towers.value = towersList.toList()
    }

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
            val categoryStr = Activity.serializeCategories(categories)
            val groupCol = encodeGroupColumn(groupName, usePercentage)
            withContext(Dispatchers.IO) {
                val newRow = excelManager.addActivity(tower.sheetName, activityName, contractor, categoryStr, groupCol, weightage)
                if (newRow > 0) {
                    val resolvedGroup = groupName.ifBlank { Tower.groupForRow(newRow)?.name ?: "Other" }
                    val isFloorBased = resolvedGroup.contains("Common", ignoreCase = true)
                    val statuses = mutableMapOf<Int, FlatStatus>()
                    val percentages = mutableMapOf<Int, Int>()
                    ExcelManager.FLAT_NUMBERS.forEach {
                        statuses[it] = FlatStatus.EMPTY
                        if (usePercentage) percentages[it] = 0
                    }

                    val newActivity = Activity(
                        name = activityName,
                        rowIndex = newRow,
                        groupName = resolvedGroup,
                        groupIndex = Activity.groupIndexFor(resolvedGroup),
                        contractor = contractor,
                        categories = categories,
                        usePercentage = usePercentage,
                        isFloorBased = isFloorBased,
                        weightage = weightage,
                        statuses = statuses,
                        percentages = percentages
                    )
                    
                    val newActivities = tower.activities.toMutableList().apply { add(newActivity) }
                    towersList[towerIndex] = tower.copy(activities = newActivities)

                    // Push metadata to Google Sheets (col A includes % flag)
                    enqueueUpdate(tower.sheetName, newRow, 1, groupCol)          // col A
                    enqueueUpdate(tower.sheetName, newRow, 2, activityName)       // col B
                    enqueueUpdate(tower.sheetName, newRow, 3, contractor)         // col C
                    enqueueUpdate(tower.sheetName, newRow, 4, categoryStr)        // col D
                    enqueueUpdate(tower.sheetName, newRow, 5, weightage.toString()) // col E

                    // ── Mirror to the other tower ───────────────────────────
                    val otherTowerIndex = if (towerIndex == 0) 1 else 0
                    val otherTower = towersList.getOrNull(otherTowerIndex)
                    if (otherTower != null) {
                        val mirrorRow = excelManager.addActivity(
                            otherTower.sheetName, activityName, contractor, categoryStr, groupCol, weightage
                        )
                        if (mirrorRow > 0) {
                            val mirrorStatuses = mutableMapOf<Int, FlatStatus>()
                            val mirrorPercentages = mutableMapOf<Int, Int>()
                            ExcelManager.FLAT_NUMBERS.forEach {
                                mirrorStatuses[it] = FlatStatus.EMPTY
                                if (usePercentage) mirrorPercentages[it] = 0
                            }
                            val mirrorActivity = Activity(
                                name = activityName,
                                rowIndex = mirrorRow,
                                groupName = resolvedGroup,
                                groupIndex = Activity.groupIndexFor(resolvedGroup),
                                contractor = contractor,
                                categories = categories,
                                usePercentage = usePercentage,
                                isFloorBased = isFloorBased,
                                weightage = weightage,
                                statuses = mirrorStatuses,
                                percentages = mirrorPercentages
                            )
                            val newOtherActivities = otherTower.activities.toMutableList().apply { add(mirrorActivity) }
                            towersList[otherTowerIndex] = otherTower.copy(activities = newOtherActivities)

                            enqueueUpdate(otherTower.sheetName, mirrorRow, 1, groupCol)
                            enqueueUpdate(otherTower.sheetName, mirrorRow, 2, activityName)
                            enqueueUpdate(otherTower.sheetName, mirrorRow, 3, contractor)
                            enqueueUpdate(otherTower.sheetName, mirrorRow, 4, categoryStr)
                            enqueueUpdate(otherTower.sheetName, mirrorRow, 5, weightage.toString())
                        }
                    }

                    _towers.value = towersList.toList()
                }
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
            val oldName = activity.name  // capture before overwrite for mirror lookup
            val categoryStr = Activity.serializeCategories(categories)
            val resolvedGroup = groupName.ifBlank { activity.groupName }
            val isFloorBased = resolvedGroup.contains("Common", ignoreCase = true)
            val groupCol = encodeGroupColumn(resolvedGroup, usePercentage)

            withContext(Dispatchers.IO) {
                excelManager.renameActivity(tower.sheetName, activity.rowIndex, newName, contractor, categoryStr, groupCol, weightage)

                val newActivity = activity.copy(
                    name = newName,
                    contractor = contractor,
                    categories = categories,
                    groupName = resolvedGroup,
                    groupIndex = Activity.groupIndexFor(resolvedGroup),
                    usePercentage = usePercentage,
                    isFloorBased = isFloorBased,
                    weightage = weightage
                )
                val newActivities = tower.activities.toMutableList()
                newActivities[activityIndex] = newActivity
                towersList[towerIndex] = tower.copy(activities = newActivities)
                
                // Push changes
                enqueueUpdate(tower.sheetName, activity.rowIndex, 1, groupCol)
                enqueueUpdate(tower.sheetName, activity.rowIndex, 2, newName)
                enqueueUpdate(tower.sheetName, activity.rowIndex, 3, contractor)
                enqueueUpdate(tower.sheetName, activity.rowIndex, 4, categoryStr)
                enqueueUpdate(tower.sheetName, activity.rowIndex, 5, weightage.toString())

                // ── Mirror rename to the other tower (match by old name) ──
                val otherTowerIndex = if (towerIndex == 0) 1 else 0
                val otherTower = towersList.getOrNull(otherTowerIndex)
                if (otherTower != null) {
                    val otherIdx = otherTower.activities.indexOfFirst { it.name == oldName }
                    if (otherIdx >= 0) {
                        val otherActivity = otherTower.activities[otherIdx]
                        excelManager.renameActivity(
                            otherTower.sheetName, otherActivity.rowIndex,
                            newName, contractor, categoryStr, groupCol, weightage
                        )
                        val updatedOther = otherActivity.copy(
                            name = newName,
                            contractor = contractor,
                            categories = categories,
                            groupName = resolvedGroup,
                            groupIndex = Activity.groupIndexFor(resolvedGroup),
                            usePercentage = usePercentage,
                            isFloorBased = isFloorBased,
                            weightage = weightage
                        )
                        val newOtherActivities = otherTower.activities.toMutableList()
                        newOtherActivities[otherIdx] = updatedOther
                        towersList[otherTowerIndex] = otherTower.copy(activities = newOtherActivities)

                        enqueueUpdate(otherTower.sheetName, otherActivity.rowIndex, 1, groupCol)
                        enqueueUpdate(otherTower.sheetName, otherActivity.rowIndex, 2, newName)
                        enqueueUpdate(otherTower.sheetName, otherActivity.rowIndex, 3, contractor)
                        enqueueUpdate(otherTower.sheetName, otherActivity.rowIndex, 4, categoryStr)
                        enqueueUpdate(otherTower.sheetName, otherActivity.rowIndex, 5, weightage.toString())
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
                // Blank the row locally and propagate to Sheets
                excelManager.deleteActivity(tower.sheetName, activity.rowIndex)
                for (col in 1..5) {
                    enqueueUpdate(tower.sheetName, activity.rowIndex, col, "")
                }

                val newActivities = tower.activities.toMutableList().apply { removeAt(activityIndex) }
                towersList[towerIndex] = tower.copy(activities = newActivities)

                // ── Mirror deletion to the other tower ──────────────────
                val otherTowerIndex = if (towerIndex == 0) 1 else 0
                val otherTower = towersList.getOrNull(otherTowerIndex)
                if (otherTower != null) {
                    val otherIdx = otherTower.activities.indexOfFirst { it.name == activityName }
                    if (otherIdx >= 0) {
                        val otherActivity = otherTower.activities[otherIdx]
                        excelManager.deleteActivity(otherTower.sheetName, otherActivity.rowIndex)
                        for (col in 1..5) {
                            enqueueUpdate(otherTower.sheetName, otherActivity.rowIndex, col, "")
                        }
                        val newOtherActivities = otherTower.activities.toMutableList().apply { removeAt(otherIdx) }
                        towersList[otherTowerIndex] = otherTower.copy(activities = newOtherActivities)
                    }
                }

                _towers.value = towersList.toList()
            }
            _statusMessage.value = "Deleted: $activityName"
        }
    }

    fun downloadFromGoogleSheets(silent: Boolean = false) {
        viewModelScope.launch {
            _isDownloading.value = true
            try {
                val result = googleSync.downloadExcel()
                result.fold(
                    onSuccess = { bytes ->
                        withContext(Dispatchers.IO) {
                            excelFile.writeBytes(bytes)
                            val towers = excelFile.inputStream().use { stream ->
                                excelManager.loadWorkbook(stream)
                            }
                            _towers.value = towers
                        }
                        if (!silent) {
                            _statusMessage.value = "Google Sheet Synced!"
                        }
                    },
                    onFailure = {
                        if (!silent) {
                            _statusMessage.value = "Download failed: ${it.message}"
                        }
                    }
                )
            } catch (e: Exception) {
                if (!silent) {
                    _statusMessage.value = "Download error: ${e.message}"
                }
            } finally {
                _isDownloading.value = false
                _isLoading.value = false
            }
        }
    }

    fun saveExcelToDownloads(): Uri? {
        val app = getApplication<Application>()
        try {
            if (!excelFile.exists()) return null

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
                        excelFile.inputStream().use { inp -> inp.copyTo(out) }
                    }
                }
                _statusMessage.value = "Saved to Downloads: $fileName"
                return uri
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val outFile = File(downloadsDir, fileName)
                excelFile.inputStream().use { inp ->
                    FileOutputStream(outFile).use { out -> inp.copyTo(out) }
                }
                _statusMessage.value = "Saved to Downloads: $fileName"
                return Uri.fromFile(outFile)
            }
        } catch (e: Exception) {
            _statusMessage.value = "Save failed: ${e.message}"
            return null
        }
    }

    // Filter functions
    fun toggleStatusFilter(filter: StatusFilter) {
        val current = _selectedStatusFilters.value.toMutableSet()
        if (current.contains(filter)) {
            current.remove(filter)
        } else {
            current.add(filter)
        }
        _selectedStatusFilters.value = current
    }

    fun toggleCategoryFilter(category: String) {
        val current = _selectedCategories.value.toMutableSet()
        if (current.contains(category)) {
            current.remove(category)
        } else {
            current.add(category)
        }
        _selectedCategories.value = current
    }

    fun setContractorFilter(contractor: String) {
        _selectedContractor.value = contractor
    }

    /** Get all unique contractors across all towers */
    fun getAllContractors(): List<String> {
        return _towers.value
            .flatMap { it.activities }
            .map { it.contractor }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    /** Get all unique group names across all towers */
    fun getAllGroupNames(): List<String> {
        val fromData = _towers.value
            .flatMap { it.activities }
            .map { it.groupName }
            .filter { it.isNotBlank() }
            .distinct()

        val defaults = Activity.DEFAULT_GROUP_NAMES
        return (fromData + defaults).distinct().sorted()
    }

    /** Apply filters to get the filtered + sorted list of activities for a tower */
    fun getFilteredActivities(tower: Tower): List<Activity> {
        val statusFilters = _selectedStatusFilters.value
        val categoryFilters = _selectedCategories.value
        val contractor = _selectedContractor.value

        return tower.activities.filter { activity ->
            // Status filter
            val statusMatch = statusFilters.isEmpty() || statusFilters.any { filter ->
                when (filter) {
                    StatusFilter.COMPLETED -> activity.isFullyComplete
                    StatusFilter.ONGOING -> activity.isOngoing
                    StatusFilter.EMPTY -> activity.isFullyEmpty
                }
            }

            // Category filter (multi-select: show if activity has ANY selected category)
            val categoryMatch = categoryFilters.isEmpty() || activity.categories.any { it in categoryFilters }

            // Contractor filter (single-select)
            val contractorMatch = contractor == "All" || activity.contractor.equals(contractor, ignoreCase = true)

            statusMatch && categoryMatch && contractorMatch
        }.sortedBy { it.name.lowercase() }  // alphabetical within filter results
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        excelManager.close()
    }
}
