package com.phase3.tracker.model

enum class FlatStatus {
    COMPLETE,  // "C" in Excel
    WIP,       // "W" in Excel
    EMPTY;     // null/empty in Excel

    fun toExcelValue(): String? = when (this) {
        COMPLETE -> "C"
        WIP -> "W"
        EMPTY -> null
    }

    /** Supabase DB status string */
    fun toDbValue(): String = when (this) {
        COMPLETE -> "complete"
        WIP -> "wip"
        EMPTY -> "empty"
    }

    fun next(): FlatStatus = when (this) {
        EMPTY -> COMPLETE
        COMPLETE -> WIP
        WIP -> EMPTY
    }

    companion object {
        fun fromExcel(value: Any?): FlatStatus = when (value?.toString()?.trim()?.uppercase()) {
            "C" -> COMPLETE
            "W" -> WIP
            else -> EMPTY
        }

        /** Try to parse a numeric percentage from the cell value. Returns null if not numeric. */
        fun parsePercentage(value: Any?): Int? {
            if (value == null) return null
            val str = value.toString().trim()
            if (str.isEmpty()) return null
            // Handle numeric cell values (doubles from POI)
            return try {
                val num = str.toDouble()
                num.toInt().coerceIn(0, 100)
            } catch (_: NumberFormatException) {
                null
            }
        }
    }
}
