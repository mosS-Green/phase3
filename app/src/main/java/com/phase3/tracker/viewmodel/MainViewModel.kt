package com.phase3.tracker.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.phase3.tracker.data.ExcelManager
import com.phase3.tracker.data.TelegramSync
import com.phase3.tracker.model.Activity
import com.phase3.tracker.model.FlatStatus
import com.phase3.tracker.model.Tower
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val excelManager = ExcelManager()
    private val telegramSync = TelegramSync()

    private val _towers = MutableStateFlow<List<Tower>>(emptyList())
    val towers: StateFlow<List<Tower>> = _towers.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val excelFile: File
        get() = File(getApplication<Application>().filesDir, "Ph-03 Tower Internal Finishing Work.xlsx")

    init {
        loadExcel()
    }

    fun loadExcel() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                withContext(Dispatchers.IO) {
                    if (!excelFile.exists()) {
                        // Copy from assets on first launch
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
                _statusMessage.value = "Error loading: ${e.message}"
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

        activity.statuses[flatNumber] = newStatus
        excelManager.updateStatus(tower.sheetName, activity.rowIndex, flatNumber, newStatus)

        // Trigger recomposition
        _towers.value = towersList.toList()

        // Save to file
        saveExcelAsync()
    }

    fun toggleFloorStatus(towerIndex: Int, activityIndex: Int, floor: Int) {
        val towersList = _towers.value.toMutableList()
        val tower = towersList.getOrNull(towerIndex) ?: return
        val activity = tower.activities.getOrNull(activityIndex) ?: return

        val flatsOnFloor = (1..4).map { floor * 100 + it }
        val currentStatuses = flatsOnFloor.map { activity.statuses[it] ?: FlatStatus.EMPTY }

        // If all same, cycle to next. If mixed, unify to most common first.
        val allSame = currentStatuses.distinct().size == 1
        val targetStatus = if (allSame) {
            currentStatuses.first().next()
        } else {
            // Find most common status
            currentStatuses.groupBy { it }.maxByOrNull { it.value.size }?.key ?: FlatStatus.EMPTY
        }

        for (flatNum in flatsOnFloor) {
            activity.statuses[flatNum] = targetStatus
            excelManager.updateStatus(tower.sheetName, activity.rowIndex, flatNum, targetStatus)
        }

        _towers.value = towersList.toList()
        saveExcelAsync()
    }

    fun addActivity(towerIndex: Int, activityName: String) {
        viewModelScope.launch {
            val tower = _towers.value.getOrNull(towerIndex) ?: return@launch
            withContext(Dispatchers.IO) {
                val newRow = excelManager.addActivity(tower.sheetName, activityName)
                if (newRow > 0) {
                    val group = Tower.groupForRow(newRow)
                    val statuses = mutableMapOf<Int, FlatStatus>()
                    ExcelManager.FLAT_NUMBERS.forEach { statuses[it] = FlatStatus.EMPTY }

                    tower.activities.add(
                        Activity(
                            name = activityName,
                            rowIndex = newRow,
                            groupName = group?.name ?: "Other",
                            groupIndex = group?.index ?: 0,
                            statuses = statuses
                        )
                    )
                    _towers.value = _towers.value.toList()
                    saveExcel()
                }
            }
            _statusMessage.value = "Activity added: $activityName"
        }
    }

    fun renameActivity(towerIndex: Int, activityIndex: Int, newName: String) {
        viewModelScope.launch {
            val tower = _towers.value.getOrNull(towerIndex) ?: return@launch
            val activity = tower.activities.getOrNull(activityIndex) ?: return@launch
            withContext(Dispatchers.IO) {
                excelManager.renameActivity(tower.sheetName, activity.rowIndex, newName)
                tower.activities[activityIndex] = activity.copy(name = newName)
                _towers.value = _towers.value.toList()
                saveExcel()
            }
            _statusMessage.value = "Renamed to: $newName"
        }
    }

    fun uploadToTelegram() {
        viewModelScope.launch {
            _isUploading.value = true
            try {
                saveExcel()
                val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
                val result = telegramSync.uploadFile(excelFile, deviceName)
                result.fold(
                    onSuccess = { _statusMessage.value = it },
                    onFailure = { _statusMessage.value = "Upload failed: ${it.message}" }
                )
            } catch (e: Exception) {
                _statusMessage.value = "Upload error: ${e.message}"
            } finally {
                _isUploading.value = false
            }
        }
    }

    fun downloadFromTelegram() {
        viewModelScope.launch {
            _isDownloading.value = true
            try {
                val result = telegramSync.downloadLatestFile()
                result.fold(
                    onSuccess = { bytes ->
                        withContext(Dispatchers.IO) {
                            excelFile.writeBytes(bytes)
                        }
                        loadExcel()
                        _statusMessage.value = "Download successful!"
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

    private fun saveExcelAsync() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                saveExcel()
            }
        }
    }

    private fun saveExcel() {
        try {
            FileOutputStream(excelFile).use { stream ->
                excelManager.saveWorkbook(stream)
            }
        } catch (e: Exception) {
            _statusMessage.value = "Save error: ${e.message}"
        }
    }

    override fun onCleared() {
        super.onCleared()
        excelManager.close()
    }
}
