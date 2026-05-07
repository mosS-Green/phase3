package com.phase3.tracker.data

import com.phase3.tracker.model.Activity
import com.phase3.tracker.model.FlatStatus
import com.phase3.tracker.model.Tower
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.OutputStream

/**
 * Generates XLSX exports from in-memory data.
 * No longer reads or caches workbooks — Supabase is the source of truth.
 */
class ExcelManager {

    companion object {
        private const val FLAT_START_COL = 5  // Column F (0-indexed)
        private const val GROUP_COL = 0       // Column A
        private const val CONTRACTOR_COL = 2  // Column C
        private const val CATEGORY_COL = 3    // Column D
        private const val WEIGHTAGE_COL = 4   // Column E

        fun flatToColIndex(flatNumber: Int): Int {
            val floor = flatNumber / 100
            val unit = flatNumber % 100
            return FLAT_START_COL + (floor - Activity.FIRST_FLOOR) * Activity.FLATS_PER_FLOOR + (unit - 1)
        }
    }

    /**
     * Build a complete XLSX workbook from the current tower data.
     * Produces the same format as the original Google Sheet:
     *   Col A = Group|% flag, Col B = Name, Col C = Contractor,
     *   Col D = Categories, Col E = Weightage, Col F+ = flat statuses
     */
    fun buildWorkbook(towers: List<Tower>): XSSFWorkbook {
        val wb = XSSFWorkbook()

        for (tower in towers) {
            val sheet = wb.createSheet(tower.sheetName)

            // Row 0: header (flat numbers)
            val headerRow = sheet.createRow(0)
            headerRow.createCell(0).setCellValue("Group")
            headerRow.createCell(1).setCellValue("Activity")
            headerRow.createCell(2).setCellValue("Contractor")
            headerRow.createCell(3).setCellValue("Category")
            headerRow.createCell(4).setCellValue("Weightage")

            for (flatNum in Activity.FLAT_NUMBERS) {
                val colIdx = flatToColIndex(flatNum)
                headerRow.createCell(colIdx).setCellValue(flatNum.toDouble())
            }

            // Row 1: blank separator (matches original format)
            sheet.createRow(1)

            // Row 2+: activities (FIRST_DATA_ROW = 3 in 1-based = index 2 in 0-based)
            tower.activities.forEachIndexed { idx, activity ->
                val row = sheet.createRow(idx + 2)

                // Col A — group name with optional |% flag
                val groupCol = if (activity.usePercentage) {
                    "${activity.groupName}|%"
                } else {
                    activity.groupName
                }
                row.createCell(GROUP_COL).setCellValue(groupCol)

                // Col B — activity name
                row.createCell(1).setCellValue(activity.name)

                // Col C — contractor
                row.createCell(CONTRACTOR_COL).setCellValue(activity.contractor)

                // Col D — categories (pipe-delimited)
                row.createCell(CATEGORY_COL).setCellValue(
                    Activity.serializeCategories(activity.categories)
                )

                // Col E — weightage
                row.createCell(WEIGHTAGE_COL).setCellValue(activity.weightage.toDouble())

                // Flat status columns
                for (flatNum in Activity.FLAT_NUMBERS) {
                    val colIdx = flatToColIndex(flatNum)
                    val cell = row.createCell(colIdx)

                    if (activity.usePercentage) {
                        val pct = activity.percentages[flatNum] ?: 0
                        if (pct > 0) {
                            cell.setCellValue(pct.toDouble())
                        }
                    } else {
                        val status = activity.statuses[flatNum] ?: FlatStatus.EMPTY
                        val excelValue = status.toExcelValue()
                        if (excelValue != null) {
                            cell.setCellValue(excelValue)
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
}
