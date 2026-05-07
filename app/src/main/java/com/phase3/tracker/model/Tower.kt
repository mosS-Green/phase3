package com.phase3.tracker.model

data class Tower(
    val id: Int = 0,              // Supabase primary key
    val name: String,             // "Tower 9" or "Tower 10"
    val sheetName: String,        // "T9" or "T10" (backward compat key)
    val activities: List<Activity>
) {
    val overallCompletion: Float
        get() {
            if (activities.isEmpty()) return 0f
            val totalWeight = activities.sumOf { it.weightage }
            if (totalWeight == 0) return 0f
            return (activities.sumOf { it.completionPercent.toDouble() * it.weightage } / totalWeight).toFloat()
        }

    val ongoingActivities: List<Activity>
        get() = activities.filter { it.isOngoing }

    /** Get all unique group names from activities in this tower */
    val groupNames: List<String>
        get() = activities.map { it.groupName }.distinct()
}
