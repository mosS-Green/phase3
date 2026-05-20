package com.phase3.tracker.model

/**
 * A room template within a tower column (Frame/Shutter/Glass).
 * Each room has assigned door/window types.
 * The same room definition applies to all 132 flats.
 */
data class DWRoom(
    val id: Int = 0,
    val towerId: Int = 0,
    val columnType: String = "frame",  // "frame", "shutter", "glass"
    val name: String,
    val sortOrder: Int = 0,
    val types: List<DWType> = emptyList(),
    // flatNumber -> (typeId -> isDone)
    val flatStatuses: MutableMap<Int, MutableMap<Int, Boolean>> = mutableMapOf()
) {
    /** Completion % for a specific flat — doors weigh 3×, windows weigh 1× */
    fun flatCompletion(flatNumber: Int): Float {
        if (types.isEmpty()) return 0f
        val statusMap = flatStatuses[flatNumber] ?: return 0f
        var totalWeight = 0
        var doneWeight = 0
        for (type in types) {
            if (!statusMap.containsKey(type.id)) continue
            val weight = if (type.isDoor) 3 else 1
            totalWeight += weight
            if (statusMap[type.id] == true) doneWeight += weight
        }
        return if (totalWeight == 0) 0f else doneWeight.toFloat() / totalWeight * 100f
    }
}

