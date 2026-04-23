package com.phase3.tracker.model

data class Activity(
    val name: String,
    val rowIndex: Int,           // Excel row (3-based)
    val groupName: String,
    val groupIndex: Int,         // 0-3 for color coding
    val contractor: String = "",
    val categories: List<String> = emptyList(),
    val usePercentage: Boolean = false,   // Track by percentage instead of C/W/E
    val isFloorBased: Boolean = false,    // Common area: 1 cell per floor
    val statuses: MutableMap<Int, FlatStatus>,  // flatNumber -> status
    val percentages: MutableMap<Int, Int> = mutableMapOf()  // flatNumber -> 0-100
) {
    /** All relevant flat/floor keys for this activity */
    private val relevantSlots: List<Int>
        get() = if (isFloorBased) {
            statuses.keys.filter { it % 100 == 1 }.toList()
        } else {
            statuses.keys.toList()
        }

    val completionPercent: Float
        get() {
            if (usePercentage) {
                val slots = relevantSlots
                if (slots.isEmpty()) return 0f
                // Missing entries default to 0 — blanks count as 0%
                return slots.map { (percentages[it] ?: 0).toFloat() }.average().toFloat()
            }
            val relevantStatuses = if (isFloorBased) {
                statuses.filter { it.key % 100 == 1 }.values
            } else {
                statuses.values
            }
            if (relevantStatuses.isEmpty()) return 0f
            val complete = relevantStatuses.count { it == FlatStatus.COMPLETE }
            return complete.toFloat() / relevantStatuses.size * 100f
        }

    val wipPercent: Float
        get() {
            if (usePercentage) {
                val slots = relevantSlots
                if (slots.isEmpty()) return 0f
                val wip = slots.count { (percentages[it] ?: 0) in 1..84 }
                return wip.toFloat() / slots.size * 100f
            }
            val relevantStatuses = if (isFloorBased) {
                statuses.filter { it.key % 100 == 1 }.values
            } else {
                statuses.values
            }
            if (relevantStatuses.isEmpty()) return 0f
            val wip = relevantStatuses.count { it == FlatStatus.WIP }
            return wip.toFloat() / relevantStatuses.size * 100f
        }

    val emptyPercent: Float
        get() {
            if (usePercentage) {
                val slots = relevantSlots
                if (slots.isEmpty()) return 0f
                val empty = slots.count { (percentages[it] ?: 0) == 0 }
                return empty.toFloat() / slots.size * 100f
            }
            val relevantStatuses = if (isFloorBased) {
                statuses.filter { it.key % 100 == 1 }.values
            } else {
                statuses.values
            }
            if (relevantStatuses.isEmpty()) return 0f
            val empty = relevantStatuses.count { it == FlatStatus.EMPTY }
            return empty.toFloat() / relevantStatuses.size * 100f
        }

    val isFullyComplete: Boolean
        get() {
            if (usePercentage) {
                val slots = relevantSlots
                return slots.isNotEmpty() && slots.all { (percentages[it] ?: 0) >= 85 }
            }
            val relevantStatuses = if (isFloorBased) {
                statuses.filter { it.key % 100 == 1 }.values
            } else {
                statuses.values
            }
            return relevantStatuses.all { it == FlatStatus.COMPLETE }
        }

    val isFullyEmpty: Boolean
        get() {
            if (usePercentage) {
                val slots = relevantSlots
                return slots.isEmpty() || slots.all { (percentages[it] ?: 0) == 0 }
            }
            val relevantStatuses = if (isFloorBased) {
                statuses.filter { it.key % 100 == 1 }.values
            } else {
                statuses.values
            }
            return relevantStatuses.all { it == FlatStatus.EMPTY }
        }

    val isOngoing: Boolean
        get() = !isFullyComplete && !isFullyEmpty

    companion object {
        /** Parse pipe-delimited or comma-delimited category string into a list */
        fun parseCategories(raw: String?): List<String> {
            if (raw.isNullOrBlank()) return emptyList()
            return raw.split("|", ",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }

        /** Serialize category list to pipe-delimited string for Excel */
        fun serializeCategories(categories: List<String>): String {
            return categories.joinToString(" | ")
        }

        val VALID_CATEGORIES = listOf(
            "Int. Flat", "Lobby", "Shaft", "Staircase", "Civil", "MEP", "Ext. Works"
        )

        /** Default group names for row-range fallback */
        val DEFAULT_GROUP_NAMES = listOf(
            "Apartments (Pre Final Stage)",
            "Handing Over Stage",
            "Common Area",
            "External Façade & Painting"
        )

        /** Map group name to an index for color coding */
        fun groupIndexFor(name: String): Int {
            return when {
                name.contains("Apartment", ignoreCase = true) ||
                name.contains("Pre Final", ignoreCase = true) -> 0
                name.contains("Handing", ignoreCase = true) -> 1
                name.contains("Common", ignoreCase = true) -> 2
                name.contains("Façade", ignoreCase = true) ||
                name.contains("Facade", ignoreCase = true) ||
                name.contains("External", ignoreCase = true) ||
                name.contains("Painting", ignoreCase = true) -> 3
                else -> 0
            }
        }
    }
}
