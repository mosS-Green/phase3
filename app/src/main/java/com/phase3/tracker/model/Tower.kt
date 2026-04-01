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

    companion object {
        val GROUPS = listOf(
            GroupInfo("Apartments (Pre Final Stage)", 3, 31, 0),
            GroupInfo("Handing Over Stage", 32, 39, 1),
            GroupInfo("Common Area", 40, 64, 2),
            GroupInfo("External Façade & Painting", 65, 72, 3),
        )

        fun groupForRow(row: Int): GroupInfo? = GROUPS.find { row in it.startRow..it.endRow }
    }
}

data class GroupInfo(
    val name: String,
    val startRow: Int,
    val endRow: Int,
    val index: Int
)
