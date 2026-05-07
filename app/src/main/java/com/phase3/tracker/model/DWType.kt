package com.phase3.tracker.model

data class DWType(
    val id: Int = 0,
    val name: String,
    val kind: String,         // "door" or "window"
    val height: Double = 0.0,
    val breadth: Double = 0.0
) {
    val isDoor: Boolean get() = kind == "door"
    val isWindow: Boolean get() = kind == "window"

    /** Display label e.g. "Bedroom Door (2.1×0.9)" */
    val displayLabel: String
        get() = if (height > 0 && breadth > 0) "$name ($height×$breadth)" else name
}
