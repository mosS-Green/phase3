package com.phase3.tracker.model

data class Activity(
    val name: String,
    val rowIndex: Int,           // Excel row (3-based)
    val groupName: String,
    val groupIndex: Int,         // 0-3 for color coding
    val statuses: MutableMap<Int, FlatStatus>  // flatNumber -> status
) {
    val completionPercent: Float
        get() {
            if (statuses.isEmpty()) return 0f
            val complete = statuses.values.count { it == FlatStatus.COMPLETE }
            return complete.toFloat() / statuses.size * 100f
        }

    val wipPercent: Float
        get() {
            if (statuses.isEmpty()) return 0f
            val wip = statuses.values.count { it == FlatStatus.WIP }
            return wip.toFloat() / statuses.size * 100f
        }

    val emptyPercent: Float
        get() {
            if (statuses.isEmpty()) return 0f
            val empty = statuses.values.count { it == FlatStatus.EMPTY }
            return empty.toFloat() / statuses.size * 100f
        }

    val isFullyComplete: Boolean
        get() = statuses.values.all { it == FlatStatus.COMPLETE }

    val isFullyEmpty: Boolean
        get() = statuses.values.all { it == FlatStatus.EMPTY }

    val isOngoing: Boolean
        get() = !isFullyComplete && !isFullyEmpty
}
