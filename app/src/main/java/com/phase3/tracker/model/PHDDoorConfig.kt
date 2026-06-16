package com.phase3.tracker.model

/**
 * Pre Hung Doors — hardcoded door type configuration per tower + unit type.
 *
 * Tower 09:
 *   A/C units (digits 1, 3): 9 doors
 *   B/D units (digits 2, 4): 11 doors
 *
 * Tower 10:
 *   A/B units (digits 1, 2): 13 doors
 *   C/D units (digits 3, 4): 14 doors
 */
object PHDDoorConfig {

    // Tower 09 — A, C type units
    private val T9_AC = listOf(
        "Main Door",
        "MBR",
        "M. Toilet",
        "BD1",
        "BD2",
        "BD2 Toilet",
        "C. Toilet",
        "Utility",
        "U. Toilet"
    )

    // Tower 09 — B, D type units
    private val T9_BD = listOf(
        "Main Door",
        "MBR",
        "M. Toilet",
        "BD1",
        "BD2",
        "BD2 Toilet",
        "BD3",
        "BD3 Toilet",
        "C. Toilet",
        "Utility",
        "U. Toilet"
    )

    // Tower 10 — A, B type units
    private val T10_AB = listOf(
        "Main Door",
        "MBR",
        "M. Toilet",
        "BD1",
        "BD1 Toilet",
        "BD2",
        "BD2 Toilet",
        "BD3",
        "BD3 Toilet",
        "C. Toilet",
        "Utility",
        "U. Toilet",
        "Dress"
    )

    // Tower 10 — C, D type units
    private val T10_CD = listOf(
        "Main Door",
        "MBR",
        "M. Toilet",
        "BD1",
        "BD1 Toilet",
        "BD2",
        "BD2 Toilet",
        "BD3",
        "BD3 Toilet",
        "BD4",
        "BD4 Toilet",
        "C. Toilet",
        "Utility",
        "U. Toilet"
    )

    /**
     * Returns the ordered list of door types for the given tower and unit digit.
     *
     * @param towerSheetName "T9" or "T10"
     * @param unitDigit 1=A, 2=B, 3=C, 4=D
     */
    fun getDoorTypes(towerSheetName: String, unitDigit: Int): List<String> {
        return when (towerSheetName) {
            "T9" -> when (unitDigit) {
                1, 3 -> T9_AC   // A, C
                2, 4 -> T9_BD   // B, D
                else -> T9_AC
            }
            "T10" -> when (unitDigit) {
                1, 2 -> T10_AB  // A, B
                3, 4 -> T10_CD  // C, D
                else -> T10_AB
            }
            else -> T9_AC
        }
    }

    /** All unique door types across all configurations */
    val ALL_DOOR_TYPES: Set<String> = (T9_AC + T9_BD + T10_AB + T10_CD).toSet()
}
