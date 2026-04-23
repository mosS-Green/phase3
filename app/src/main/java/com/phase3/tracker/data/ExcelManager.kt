package com.phase3.tracker.data

import com.phase3.tracker.model.Activity
import com.phase3.tracker.model.FlatStatus
import com.phase3.tracker.model.Tower
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.io.OutputStream

class ExcelManager {

    private var workbook: Workbook? = null

    companion object {
        private const val FIRST_DATA_ROW = 3  // Row 3 is first activity
        private const val GROUP_COL = 0       // Column A — group/classification
        private const val CONTRACTOR_COL = 2  // Column C (0-indexed)
        private const val CATEGORY_COL = 3    // Column D (0-indexed)
        private const val FLAT_START_COL = 4  // Column E (0-indexed) — shifted by 2 for new Contractor+Category cols
        private const val FLATS_PER_FLOOR = 4
        private const val FIRST_FLOOR = 2
        private const val LAST_FLOOR = 34
        private const val TOTAL_FLOORS = 33 // floors 2 to 34

        val FLAT_NUMBERS: List<Int> = (FIRST_FLOOR..LAST_FLOOR).flatMap { floor ->
            (1..FLATS_PER_FLOOR).map { flat -> floor * 100 + flat }
        }

        /** Only first flat per floor (for floor-based activities) */
        val FLOOR_NUMBERS: List<Int> = (FIRST_FLOOR..LAST_FLOOR).map { floor -> floor * 100 + 1 }

        fun flatToColIndex(flatNumber: Int): Int {
            val floor = flatNumber / 100
            val unit = flatNumber % 100
            return FLAT_START_COL + (floor - FIRST_FLOOR) * FLATS_PER_FLOOR + (unit - 1)
        }

        fun colIndexToFlat(colIndex: Int): Int {
            val offset = colIndex - FLAT_START_COL
            val floor = offset / FLATS_PER_FLOOR + FIRST_FLOOR
            val unit = offset % FLATS_PER_FLOOR + 1
            return floor * 100 + unit
        }
    }

    fun loadWorkbook(inputStream: InputStream): List<Tower> {
        workbook = XSSFWorkbook(inputStream)
        return parseTowers()
    }

    private fun getCellString(row: org.apache.poi.ss.usermodel.Row?, colIdx: Int): String? {
        val cell = row?.getCell(colIdx) ?: return null
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue?.trim()
            CellType.NUMERIC -> cell.numericCellValue.toString()
            CellType.BLANK -> null
            else -> cell.toString().trim().ifEmpty { null }
        }
    }

    private fun getCellNumeric(row: org.apache.poi.ss.usermodel.Row?, colIdx: Int): Double? {
        val cell = row?.getCell(colIdx) ?: return null
        return when (cell.cellType) {
            CellType.NUMERIC -> cell.numericCellValue
            CellType.STRING -> cell.stringCellValue?.trim()?.toDoubleOrNull()
            CellType.BLANK -> null
            else -> null
        }
    }

    private fun parseTowers(): List<Tower> {
        val wb = workbook ?: return emptyList()
        val towers = mutableListOf<Tower>()

        val sheetMap = mapOf("T9" to "Tower 9", "T10" to "Tower 10")

        for ((sheetName, towerName) in sheetMap) {
            val sheet = wb.getSheet(sheetName) ?: continue
            val activities = mutableListOf<Activity>()

            val lastRow = sheet.lastRowNum

            for (rowIdx in (FIRST_DATA_ROW - 1)..lastRow) {
                val row = sheet.getRow(rowIdx) ?: continue
                val nameCell = row.getCell(1) // Column B (0-indexed = 1)
                val activityName = nameCell?.stringCellValue?.trim()
                if (activityName.isNullOrBlank()) continue

                val excelRow = rowIdx + 1 // Convert to 1-based
                val group = Tower.groupForRow(excelRow)

                // Read group/classification from column A, fall back to row-range detection
                val groupFromExcel = getCellString(row, GROUP_COL)
                val groupName = if (!groupFromExcel.isNullOrBlank()) {
                    groupFromExcel
                } else {
                    group?.name ?: "Other"
                }
                val groupIndex = Activity.groupIndexFor(groupName)
                val isFloorBased = group?.isFloorBased ?: groupName.contains("Common", ignoreCase = true)

                // Read contractor (column C) and category (column D)
                val contractor = getCellString(row, CONTRACTOR_COL) ?: ""
                val categoryRaw = getCellString(row, CATEGORY_COL) ?: ""
                val categories = Activity.parseCategories(categoryRaw)

                // Determine if this activity uses percentage tracking
                // Convention: if any flat cell has a numeric value (not "C"/"W"/""), it's percentage-based
                var hasPercentage = false
                val statuses = mutableMapOf<Int, FlatStatus>()
                val percentages = mutableMapOf<Int, Int>()

                for (flatNum in FLAT_NUMBERS) {
                    val colIdx = flatToColIndex(flatNum)
                    val cell = row.getCell(colIdx)
                    val value: Any? = when {
                        cell == null -> null
                        cell.cellType == CellType.STRING -> cell.stringCellValue
                        cell.cellType == CellType.NUMERIC -> cell.numericCellValue
                        else -> null
                    }

                    // Check if numeric (percentage)
                    val pct = FlatStatus.parsePercentage(value)
                    if (pct != null && cell?.cellType == CellType.NUMERIC) {
                        hasPercentage = true
                        percentages[flatNum] = pct
                    }

                    statuses[flatNum] = FlatStatus.fromExcel(value)
                }

                // If percentage mode detected, ensure all flats have a percentage entry
                if (hasPercentage) {
                    for (flatNum in FLAT_NUMBERS) {
                        if (flatNum !in percentages) {
                            percentages[flatNum] = 0
                        }
                    }
                }

                activities.add(
                    Activity(
                        name = activityName,
                        rowIndex = excelRow,
                        groupName = groupName,
                        groupIndex = groupIndex,
                        contractor = contractor,
                        categories = categories,
                        usePercentage = hasPercentage,
                        isFloorBased = isFloorBased,
                        statuses = statuses,
                        percentages = percentages
                    )
                )
            }

            towers.add(Tower(name = towerName, sheetName = sheetName, activities = activities))
        }

        return towers
    }

    fun updateStatus(sheetName: String, activityRow: Int, flatNumber: Int, status: FlatStatus) {
        val wb = workbook ?: return
        val sheet = wb.getSheet(sheetName) ?: return
        val row = sheet.getRow(activityRow - 1) ?: sheet.createRow(activityRow - 1)
        val colIdx = flatToColIndex(flatNumber)
        val cell = row.getCell(colIdx) ?: row.createCell(colIdx)

        val excelValue = status.toExcelValue()
        if (excelValue != null) {
            cell.setCellValue(excelValue)
        } else {
            cell.setCellValue("")
        }
    }

    fun updatePercentage(sheetName: String, activityRow: Int, flatNumber: Int, percentage: Int) {
        val wb = workbook ?: return
        val sheet = wb.getSheet(sheetName) ?: return
        val row = sheet.getRow(activityRow - 1) ?: sheet.createRow(activityRow - 1)
        val colIdx = flatToColIndex(flatNumber)
        val cell = row.getCell(colIdx) ?: row.createCell(colIdx)
        cell.setCellValue(percentage.toDouble())
    }

    fun addActivity(sheetName: String, activityName: String, contractor: String, categoryStr: String, groupName: String = ""): Int {
        val wb = workbook ?: return -1
        val sheet = wb.getSheet(sheetName) ?: return -1

        val newRowIdx = sheet.lastRowNum + 1
        val row = sheet.createRow(newRowIdx)

        // Column A - group/classification
        if (groupName.isNotBlank()) {
            row.createCell(GROUP_COL).setCellValue(groupName)
        }
        // Column B - activity name
        row.createCell(1).setCellValue(activityName)
        // Column C - contractor
        row.createCell(CONTRACTOR_COL).setCellValue(contractor)
        // Column D - category
        row.createCell(CATEGORY_COL).setCellValue(categoryStr)

        return newRowIdx + 1 // Return 1-based row number
    }

    fun renameActivity(sheetName: String, activityRow: Int, newName: String, contractor: String, categoryStr: String, groupName: String = "") {
        val wb = workbook ?: return
        val sheet = wb.getSheet(sheetName) ?: return
        val row = sheet.getRow(activityRow - 1) ?: return

        // Column A - group/classification
        (row.getCell(GROUP_COL) ?: row.createCell(GROUP_COL)).setCellValue(groupName)
        // Column B - name
        (row.getCell(1) ?: row.createCell(1)).setCellValue(newName)
        // Column C - contractor
        (row.getCell(CONTRACTOR_COL) ?: row.createCell(CONTRACTOR_COL)).setCellValue(contractor)
        // Column D - category
        (row.getCell(CATEGORY_COL) ?: row.createCell(CATEGORY_COL)).setCellValue(categoryStr)
    }

    fun saveWorkbook(outputStream: OutputStream) {
        workbook?.write(outputStream)
    }

    fun close() {
        workbook?.close()
    }
}
