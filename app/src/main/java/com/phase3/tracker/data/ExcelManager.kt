package com.phase3.tracker.data

import com.phase3.tracker.model.Activity
import com.phase3.tracker.model.FlatStatus
import com.phase3.tracker.model.Tower
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFColor
import org.apache.poi.xssf.usermodel.XSSFFont
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.io.OutputStream

/**
 * Generates and parses XLSX exports from in-memory data.
 * Supabase is the source of truth; this class only handles the file layer.
 */
class ExcelManager {

    companion object {
        private const val FLAT_START_COL = 5  // Column F (0-indexed)
        private const val GROUP_COL = 0
        private const val ACTIVITY_COL = 1
        private const val CONTRACTOR_COL = 2
        private const val CATEGORY_COL = 3
        private const val WEIGHTAGE_COL = 4

        // Byte colours
        private val COLOR_HEADER   = byteArrayOf(0x26.toByte(), 0x8B.toByte(), 0x8B.toByte()) // teal
        private val COLOR_GROUP_BG = byteArrayOf(0xE8.toByte(), 0xF5.toByte(), 0xE9.toByte()) // light green
        private val COLOR_COMPLETE = byteArrayOf(0x81.toByte(), 0xC7.toByte(), 0x84.toByte()) // green
        private val COLOR_WIP      = byteArrayOf(0xFF.toByte(), 0xF1.toByte(), 0x76.toByte()) // yellow
        private val COLOR_PCT_LOW  = byteArrayOf(0xFF.toByte(), 0xCC.toByte(), 0x80.toByte()) // amber
        private val COLOR_PCT_HIGH = byteArrayOf(0xA5.toByte(), 0xD6.toByte(), 0xA7.toByte()) // light green

        fun flatToColIndex(flatNumber: Int): Int {
            val floor = flatNumber / 100
            val unit = flatNumber % 100
            return FLAT_START_COL + (floor - Activity.FIRST_FLOOR) * Activity.FLATS_PER_FLOOR + (unit - 1)
        }

        fun colIndexToFlat(colIndex: Int): Int? {
            if (colIndex < FLAT_START_COL) return null
            val offset = colIndex - FLAT_START_COL
            val floor = offset / Activity.FLATS_PER_FLOOR + Activity.FIRST_FLOOR
            val unit = offset % Activity.FLATS_PER_FLOOR + 1
            if (floor > Activity.LAST_FLOOR || unit > Activity.FLATS_PER_FLOOR) return null
            return floor * 100 + unit
        }
    }

    // ── Style factories ──────────────────────────────────────────────────

    private fun headerStyle(wb: XSSFWorkbook): XSSFCellStyle {
        val font: XSSFFont = wb.createFont() as XSSFFont
        font.bold = true
        font.color = IndexedColors.WHITE.index
        font.fontHeightInPoints = 10
        val style = wb.createCellStyle() as XSSFCellStyle
        style.setFont(font)
        style.setFillForegroundColor(XSSFColor(COLOR_HEADER, null))
        style.fillPattern = FillPatternType.SOLID_FOREGROUND
        style.alignment = HorizontalAlignment.CENTER
        style.setBorderBottom(BorderStyle.THIN)
        return style
    }

    private fun groupStyle(wb: XSSFWorkbook): XSSFCellStyle {
        val font: XSSFFont = wb.createFont() as XSSFFont
        font.bold = true
        font.fontHeightInPoints = 10
        val style = wb.createCellStyle() as XSSFCellStyle
        style.setFont(font)
        style.setFillForegroundColor(XSSFColor(COLOR_GROUP_BG, null))
        style.fillPattern = FillPatternType.SOLID_FOREGROUND
        return style
    }

    private fun completeStyle(wb: XSSFWorkbook): XSSFCellStyle {
        val style = wb.createCellStyle() as XSSFCellStyle
        style.setFillForegroundColor(XSSFColor(COLOR_COMPLETE, null))
        style.fillPattern = FillPatternType.SOLID_FOREGROUND
        style.alignment = HorizontalAlignment.CENTER
        return style
    }

    private fun wipStyle(wb: XSSFWorkbook): XSSFCellStyle {
        val style = wb.createCellStyle() as XSSFCellStyle
        style.setFillForegroundColor(XSSFColor(COLOR_WIP, null))
        style.fillPattern = FillPatternType.SOLID_FOREGROUND
        style.alignment = HorizontalAlignment.CENTER
        return style
    }

    private fun pctStyleLow(wb: XSSFWorkbook): XSSFCellStyle {
        val style = wb.createCellStyle() as XSSFCellStyle
        style.setFillForegroundColor(XSSFColor(COLOR_PCT_LOW, null))
        style.fillPattern = FillPatternType.SOLID_FOREGROUND
        style.alignment = HorizontalAlignment.CENTER
        val fmt = wb.createDataFormat()
        style.dataFormat = fmt.getFormat("0\"%\"")
        return style
    }

    private fun pctStyleHigh(wb: XSSFWorkbook): XSSFCellStyle {
        val style = wb.createCellStyle() as XSSFCellStyle
        style.setFillForegroundColor(XSSFColor(COLOR_PCT_HIGH, null))
        style.fillPattern = FillPatternType.SOLID_FOREGROUND
        style.alignment = HorizontalAlignment.CENTER
        val fmt = wb.createDataFormat()
        style.dataFormat = fmt.getFormat("0\"%\"")
        return style
    }

    private fun pctStyleBlank(wb: XSSFWorkbook): XSSFCellStyle {
        val style = wb.createCellStyle() as XSSFCellStyle
        style.alignment = HorizontalAlignment.CENTER
        val fmt = wb.createDataFormat()
        style.dataFormat = fmt.getFormat("0\"%\"")
        return style
    }

    private fun centreStyle(wb: XSSFWorkbook): XSSFCellStyle {
        val style = wb.createCellStyle() as XSSFCellStyle
        style.alignment = HorizontalAlignment.CENTER
        return style
    }

    // ── Build workbook ───────────────────────────────────────────────────

    /**
     * Build a complete XLSX workbook from the current tower data.
     * Activities are sorted **alphabetically** within each tower sheet.
     *
     * Format:
     *   Col A = Group|% flag, Col B = Activity Name, Col C = Contractor,
     *   Col D = Categories, Col E = Weightage, Col F+ = flat statuses
     */
    fun buildWorkbook(towers: List<Tower>): XSSFWorkbook {
        val wb = XSSFWorkbook()

        val hdrStyle  = headerStyle(wb)
        val grpStyle  = groupStyle(wb)
        val doneStyle = completeStyle(wb)
        val wipStyle  = wipStyle(wb)
        val ctrStyle  = centreStyle(wb)
        val pctLow    = pctStyleLow(wb)
        val pctHigh   = pctStyleHigh(wb)
        val pctBlank  = pctStyleBlank(wb)

        for (tower in towers) {
            val sheet = wb.createSheet(tower.sheetName)

            // ── Header row ──────────────────────────────────────
            val headerRow = sheet.createRow(0)
            listOf("Group", "Activity", "Contractor", "Category", "Weightage").forEachIndexed { i, title ->
                headerRow.createCell(i).apply {
                    setCellValue(title)
                    cellStyle = hdrStyle
                }
            }
            for (flatNum in Activity.FLAT_NUMBERS) {
                val colIdx = flatToColIndex(flatNum)
                headerRow.createCell(colIdx).apply {
                    setCellValue(flatNum.toDouble())
                    cellStyle = hdrStyle
                }
            }

            // ── Blank separator (row 1) ─────────────────────────
            sheet.createRow(1)

            // ── Freeze top row ──────────────────────────────────
            sheet.createFreezePane(0, 1)

            // ── Column widths ───────────────────────────────────
            sheet.setColumnWidth(GROUP_COL,      22 * 256)
            sheet.setColumnWidth(ACTIVITY_COL,   40 * 256)
            sheet.setColumnWidth(CONTRACTOR_COL, 22 * 256)
            sheet.setColumnWidth(CATEGORY_COL,   22 * 256)
            sheet.setColumnWidth(WEIGHTAGE_COL,   8 * 256)
            for (flatNum in Activity.FLAT_NUMBERS) {
                sheet.setColumnWidth(flatToColIndex(flatNum), 6 * 256)
            }

            // ── Data rows: sorted alphabetically ───────────────
            val sorted = tower.activities.sortedBy { it.name.lowercase() }
            sorted.forEachIndexed { idx, activity ->
                val row = sheet.createRow(idx + 2)

                val groupCol = if (activity.usePercentage) "${activity.groupName}|%" else activity.groupName
                row.createCell(GROUP_COL).apply {
                    setCellValue(groupCol)
                    cellStyle = grpStyle
                }
                row.createCell(ACTIVITY_COL).setCellValue(activity.name)
                row.createCell(CONTRACTOR_COL).setCellValue(activity.contractor)
                row.createCell(CATEGORY_COL).setCellValue(Activity.serializeCategories(activity.categories))
                row.createCell(WEIGHTAGE_COL).apply {
                    setCellValue(activity.weightage.toDouble())
                    cellStyle = ctrStyle
                }

                // Flat cells
                for (flatNum in Activity.FLAT_NUMBERS) {
                    val colIdx = flatToColIndex(flatNum)
                    val cell = row.createCell(colIdx)

                    if (activity.usePercentage) {
                        val pct = activity.percentages[flatNum] ?: 0
                        cell.setCellValue(pct.toDouble())
                        cell.cellStyle = when {
                            pct == 0  -> pctBlank
                            pct < 85  -> pctLow
                            else      -> pctHigh
                        }
                    } else {
                        val status = activity.statuses[flatNum] ?: FlatStatus.EMPTY
                        when (status) {
                            FlatStatus.COMPLETE -> {
                                cell.setCellValue("C")
                                cell.cellStyle = doneStyle
                            }
                            FlatStatus.WIP -> {
                                cell.setCellValue("W")
                                cell.cellStyle = wipStyle
                            }
                            FlatStatus.EMPTY -> { /* leave blank */ }
                        }
                    }
                }
            }
        }

        return wb
    }

    /** Write workbook to an output stream */
    fun writeWorkbook(workbook: XSSFWorkbook, outputStream: OutputStream) {
        workbook.write(outputStream)
        workbook.close()
    }

    // ── Parse workbook ───────────────────────────────────────────────────

    /**
     * Data class representing one parsed activity row from the import file.
     */
    data class ParsedActivity(
        val sheetName: String,
        val groupName: String,
        val usePercentage: Boolean,
        val name: String,
        val contractor: String,
        val categories: List<String>,
        val weightage: Int,
        /** flat_number -> status string ("complete"/"wip"/"empty") */
        val statuses: Map<Int, String>,
        /** flat_number -> percentage 0-100 (only meaningful if usePercentage) */
        val percentages: Map<Int, Int>
    )

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

    /**
     * Parse an Activities XLSX file (same format as [buildWorkbook]) into a list of
     * [ParsedActivity] objects. Rows 0 (header) and 1 (blank) are skipped.
     */
    fun parseWorkbook(inputStream: InputStream): List<ParsedActivity> {
        val wb = XSSFWorkbook(inputStream)
        val results = mutableListOf<ParsedActivity>()

        for (sheetIdx in 0 until wb.numberOfSheets) {
            val sheet = wb.getSheetAt(sheetIdx)
            val sheetName = wb.getSheetName(sheetIdx)

            // Build flat-column mapping from header row
            val headerRow = sheet.getRow(0) ?: continue
            val flatCols = mutableMapOf<Int, Int>() // colIndex -> flatNumber
            for (c in FLAT_START_COL until (headerRow.lastCellNum.toInt())) {
                val cell = headerRow.getCell(c) ?: continue
                val flatNumStr = cellString(cell) ?: continue
                val flatNum = flatNumStr.toDoubleOrNull()?.toInt() ?: continue
                if (flatNum > 0) flatCols[c] = flatNum
            }

            // Data starts at row 2 (row 0=header, row 1=blank)
            for (rowIdx in 2..sheet.lastRowNum) {
                val row = sheet.getRow(rowIdx) ?: continue

                val rawGroup = cellString(row.getCell(GROUP_COL)) ?: continue
                val name = cellString(row.getCell(ACTIVITY_COL))?.takeIf { it.isNotBlank() } ?: continue
                val contractor = cellString(row.getCell(CONTRACTOR_COL)) ?: ""
                val categoriesRaw = cellString(row.getCell(CATEGORY_COL)) ?: ""
                val weightage = row.getCell(WEIGHTAGE_COL)?.let { cell ->
                    try {
                        when (cell.cellType) {
                            org.apache.poi.ss.usermodel.CellType.NUMERIC -> cell.numericCellValue.toInt()
                            org.apache.poi.ss.usermodel.CellType.STRING -> cell.stringCellValue.trim().toIntOrNull() ?: 5
                            else -> cellString(cell)?.toDoubleOrNull()?.toInt() ?: 5
                        }
                    } catch (_: Exception) { 5 }
                } ?: 5

                val usePercentage = rawGroup.endsWith("|%")
                val groupName = if (usePercentage) rawGroup.removeSuffix("|%").trim() else rawGroup

                val statuses = mutableMapOf<Int, String>()
                val percentages = mutableMapOf<Int, Int>()

                for ((colIdx, flatNum) in flatCols) {
                    val cell = row.getCell(colIdx)
                    if (usePercentage) {
                        val pct = try {
                            when (cell?.cellType) {
                                org.apache.poi.ss.usermodel.CellType.NUMERIC ->
                                    cell.numericCellValue.toInt().coerceIn(0, 100)
                                org.apache.poi.ss.usermodel.CellType.STRING -> {
                                    val s = cell.stringCellValue.trim().removeSuffix("%")
                                    s.toIntOrNull()?.coerceIn(0, 100) ?: 0
                                }
                                else -> {
                                    val s = cellString(cell)?.removeSuffix("%")
                                    s?.toDoubleOrNull()?.toInt()?.coerceIn(0, 100) ?: 0
                                }
                            }
                        } catch (_: Exception) { 0 }
                        percentages[flatNum] = pct
                        statuses[flatNum] = "empty"
                    } else {
                        val raw = try {
                            when (cell?.cellType) {
                                org.apache.poi.ss.usermodel.CellType.STRING ->
                                    cell.stringCellValue.trim().uppercase()
                                else -> cellString(cell)?.uppercase() ?: ""
                            }
                        } catch (_: Exception) { "" }
                        statuses[flatNum] = when (raw) {
                            "C" -> "complete"
                            "W" -> "wip"
                            else -> "empty"
                        }
                        percentages[flatNum] = 0
                    }
                }

                results.add(
                    ParsedActivity(
                        sheetName = sheetName,
                        groupName = groupName,
                        usePercentage = usePercentage,
                        name = name,
                        contractor = contractor,
                        categories = Activity.parseCategories(categoriesRaw),
                        weightage = weightage,
                        statuses = statuses,
                        percentages = percentages
                    )
                )
            }
        }

        wb.close()
        return results
    }
}
