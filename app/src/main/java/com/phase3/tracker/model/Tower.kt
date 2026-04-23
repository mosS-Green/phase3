package com.phase3.tracker.model

data class Tower(
    val name: String,        // "Tower 9" or "Tower 10"
    val sheetName: String,   // "T9" or "T10"
    val activities: MutableList<Activity>
) {
    val overallCompletion: Float
        get() {
            if (activities.isEmpty()) return 0f
            return activities.map { it.completionPercent }.average().toFloat()
        }

    val ongoingActivities: List<Activity>
        get() = activities.filter { it.isOngoing }

    /** Get all unique group names from activities in this tower */
    val groupNames: List<String>
        get() = activities.map { it.groupName }.distinct()

    companion object {
        val GROUPS = listOf(
            GroupInfo("Apartments (Pre Final Stage)", 3, 31, 0, isFloorBased = false),
            GroupInfo("Handing Over Stage", 32, 39, 1, isFloorBased = false),
            GroupInfo("Common Area", 40, 64, 2, isFloorBased = true),
            GroupInfo("External Façade & Painting", 65, 72, 3, isFloorBased = false),
        )

        fun groupForRow(row: Int): GroupInfo? = GROUPS.find { row in it.startRow..it.endRow }
    }
}

data class GroupInfo(
    val name: String,
    val startRow: Int,
    val endRow: Int,
    val index: Int,
    val isFloorBased: Boolean = false
)
