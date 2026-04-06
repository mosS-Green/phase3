package com.phase3.tracker.viewmodel

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.phase3.tracker.data.ExcelManager
import com.phase3.tracker.data.GoogleSync
import com.phase3.tracker.model.Activity
import com.phase3.tracker.model.FlatStatus
import com.phase3.tracker.model.Tower
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    private var pendingSyncCount = 0
    private val syncCountLock = Any()

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

    data class UpdateRequest(val sheetName: String, val row: Int, val col: Int, val value: String)
    private val updateChannel = Channel<UpdateRequest>(Channel.UNLIMITED)

    init {
        loadExcel()

        // Background worker for robust sequential webhook updates
        viewModelScope.launch(Dispatchers.IO) {
            for (request in updateChannel) {
                try {
                    delay(50)
                    val result = googleSync.updateCell(request.sheetName, request.row, request.col, request.value)
                    result.onFailure {
                        _statusMessage.value = "Sync failed: ${it.message}"
                    }
                } catch (e: Exception) {
                    _statusMessage.value = "Sync queue error: ${e.message}"
                } finally {
                    val remaining = synchronized(syncCountLock) {
                        pendingSyncCount--
                        pendingSyncCount
                    }
                    if (remaining <= 0) {
                        _isSyncing.value = false
                        _syncDone.value = true
                        // Reset the done indicator after a delay
                        viewModelScope.launch {
                            delay(3000)
                            _syncDone.value = false
                        }
                    }
                }
            }
        }
    }

    private fun enqueueUpdate(sheetName: String, row: Int, col: Int, value: String) {
        synchronized(syncCountLock) {
            pendingSyncCount++
        }
        _isSyncing.value = true
        _syncDone.value = false
        updateChannel.trySend(UpdateRequest(sheetName, row, col, value))
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

    fun addActivity(towerIndex: Int, activityName: String, contractor: String = "", categories: List<String> = emptyList()) {
        viewModelScope.launch {
            val towersList = _towers.value.toMutableList()
            val tower = towersList.getOrNull(towerIndex) ?: return@launch
            val categoryStr = Activity.serializeCategories(categories)
            withContext(Dispatchers.IO) {
                val newRow = excelManager.addActivity(tower.sheetName, activityName, contractor, categoryStr)
                if (newRow > 0) {
                    val group = Tower.groupForRow(newRow)
                    val statuses = mutableMapOf<Int, FlatStatus>()
                    ExcelManager.FLAT_NUMBERS.forEach { statuses[it] = FlatStatus.EMPTY }

                    val newActivity = Activity(
                        name = activityName,
                        rowIndex = newRow,
                        groupName = group?.name ?: "Other",
                        groupIndex = group?.index ?: 0,
                        contractor = contractor,
                        categories = categories,
                        statuses = statuses
                    )
                    
                    val newActivities = tower.activities.toMutableList().apply { add(newActivity) }
                    val newTower = tower.copy(activities = newActivities)
                    towersList[towerIndex] = newTower

                    _towers.value = towersList.toList()
                    
                    // Push name, contractor, category to Google Sheets
                    enqueueUpdate(tower.sheetName, newRow, 2, activityName) // col B
                    enqueueUpdate(tower.sheetName, newRow, 3, contractor) // col C
                    enqueueUpdate(tower.sheetName, newRow, 4, categoryStr) // col D
                }
            }
            _statusMessage.value = "Activity added: $activityName"
        }
    }

    fun renameActivity(towerIndex: Int, activityIndex: Int, newName: String, contractor: String = "", categories: List<String> = emptyList()) {
        viewModelScope.launch {
            val towersList = _towers.value.toMutableList()
            val tower = towersList.getOrNull(towerIndex) ?: return@launch
            val activity = tower.activities.getOrNull(activityIndex) ?: return@launch
            val categoryStr = Activity.serializeCategories(categories)
            withContext(Dispatchers.IO) {
                excelManager.renameActivity(tower.sheetName, activity.rowIndex, newName, contractor, categoryStr)
                
                val newActivity = activity.copy(
                    name = newName,
                    contractor = contractor,
                    categories = categories
                )
                val newActivities = tower.activities.toMutableList()
                newActivities[activityIndex] = newActivity
                val newTower = tower.copy(activities = newActivities)
                towersList[towerIndex] = newTower
                
                _towers.value = towersList.toList()
                
                // Push changes
                enqueueUpdate(tower.sheetName, activity.rowIndex, 2, newName) // col B
                enqueueUpdate(tower.sheetName, activity.rowIndex, 3, contractor) // col C
                enqueueUpdate(tower.sheetName, activity.rowIndex, 4, categoryStr) // col D
            }
            _statusMessage.value = "Updated: $newName"
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

    /** Apply filters to get the filtered list of activities for a tower */
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
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        excelManager.close()
    }
}
