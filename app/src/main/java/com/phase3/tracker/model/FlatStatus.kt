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
    }
}
