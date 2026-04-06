package com.phase3.tracker.viewmodel

import android.app.Application
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
                    delay(50) // Tiny delay for sequential safety against API concurrent limits
                    val result = googleSync.updateCell(request.sheetName, request.row, request.col, request.value)
                    result.onFailure {
                        _statusMessage.value = "Sync failed: ${it.message}"
                    }
                } catch (e: Exception) {
                    _statusMessage.value = "Sync queue error: ${e.message}"
                }
            }
        }
    }

    fun loadExcel() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                withContext(Dispatchers.IO) {
                    if (!excelFile.exists()) {
                        val assets = getApplication<Application>().assets
                        assets.open("Ph-03 Tower Internal Finishing Work.xlsx").use { input ->
                            FileOutputStream(excelFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    val towers = excelFile.inputStream().use { stream ->
                        excelManager.loadWorkbook(stream)
                    }
                    _towers.value = towers
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error loading local data: ${e.message}"
            } finally {
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

        // Recompose instantly
        _towers.value = towersList.toList()

        // Persistent update in background via Webhook
        val colIdx = ExcelManager.flatToColIndex(flatNumber)
        val value = newStatus.toExcelValue() ?: ""
        updateChannel.trySend(UpdateRequest(tower.sheetName, activity.rowIndex, colIdx + 1, value))
        
        // Optionally update local POI state (we no longer save `saveExcelAsync()` to prevent lag/crashing)
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
            updateChannel.trySend(UpdateRequest(tower.sheetName, activity.rowIndex, colIdx + 1, value))
            excelManager.updateStatus(tower.sheetName, activity.rowIndex, flatNum, targetStatus)
        }

        val newActivity = activity.copy(statuses = newStatuses)
        val newActivities = tower.activities.toMutableList()
        newActivities[activityIndex] = newActivity
        
        val newTower = tower.copy(activities = newActivities)
        towersList[towerIndex] = newTower

        _towers.value = towersList.toList()
    }

    fun addActivity(towerIndex: Int, activityName: String) {
        viewModelScope.launch {
            val towersList = _towers.value.toMutableList()
            val tower = towersList.getOrNull(towerIndex) ?: return@launch
            withContext(Dispatchers.IO) {
                val newRow = excelManager.addActivity(tower.sheetName, activityName)
                if (newRow > 0) {
                    val group = Tower.groupForRow(newRow)
                    val statuses = mutableMapOf<Int, FlatStatus>()
                    ExcelManager.FLAT_NUMBERS.forEach { statuses[it] = FlatStatus.EMPTY }

                    val newActivity = Activity(
                        name = activityName,
                        rowIndex = newRow,
                        groupName = group?.name ?: "Other",
                        groupIndex = group?.index ?: 0,
                        statuses = statuses
                    )
                    
                    val newActivities = tower.activities.toMutableList().apply { add(newActivity) }
                    val newTower = tower.copy(activities = newActivities)
                    towersList[towerIndex] = newTower

                    _towers.value = towersList.toList()
                    
                    // Webhook append doesn't naturally support "add column" structural changes perfectly in this basic payload
                    // but we push it if Apps Script handles an updateCell gracefully or appendRow.
                    // For now, we update the cell holding the activity name
                    updateChannel.trySend(UpdateRequest(tower.sheetName, newRow, 2, activityName)) // col B is 2
                }
            }
            _statusMessage.value = "Activity added: $activityName"
        }
    }

    fun renameActivity(towerIndex: Int, activityIndex: Int, newName: String) {
        viewModelScope.launch {
            val towersList = _towers.value.toMutableList()
            val tower = towersList.getOrNull(towerIndex) ?: return@launch
            val activity = tower.activities.getOrNull(activityIndex) ?: return@launch
            withContext(Dispatchers.IO) {
                excelManager.renameActivity(tower.sheetName, activity.rowIndex, newName)
                
                val newActivity = activity.copy(name = newName)
                val newActivities = tower.activities.toMutableList()
                newActivities[activityIndex] = newActivity
                val newTower = tower.copy(activities = newActivities)
                towersList[towerIndex] = newTower
                
                _towers.value = towersList.toList()
                
                // Column B (1-indexed is 2) holds activity names
                updateChannel.trySend(UpdateRequest(tower.sheetName, activity.rowIndex, 2, newName))
            }
            _statusMessage.value = "Renamed to: $newName"
        }
    }

    fun downloadFromGoogleSheets() {
        viewModelScope.launch {
            _isDownloading.value = true
            try {
                val result = googleSync.downloadExcel()
                result.fold(
                    onSuccess = { bytes ->
                        withContext(Dispatchers.IO) {
                            excelFile.writeBytes(bytes)
                        }
                        // Reload data into UI
                        loadExcel()
                        _statusMessage.value = "Google Sheet Synced!"
                    },
                    onFailure = { _statusMessage.value = "Download failed: ${it.message}" }
                )
            } catch (e: Exception) {
                _statusMessage.value = "Download error: ${e.message}"
            } finally {
                _isDownloading.value = false
            }
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
