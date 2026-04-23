package com.phase3.tracker.ui.theme

import androidx.compose.ui.graphics.Color

// ── Sea Green / Teal primary tonal palette ──────────────────────────
val SeaGreen10  = Color(0xFF002019)
val SeaGreen20  = Color(0xFF00382D)
val SeaGreen25  = Color(0xFF004537)
val SeaGreen30  = Color(0xFF005141)
val SeaGreen40  = Color(0xFF006C57)
val SeaGreen50  = Color(0xFF00886E)
val SeaGreen60  = Color(0xFF1EA587)
val SeaGreen70  = Color(0xFF4AC1A0)
val SeaGreen80  = Color(0xFF6FDDBA)
val SeaGreen90  = Color(0xFF93F9D5)
val SeaGreen95  = Color(0xFFC3FFE9)
val SeaGreen99  = Color(0xFFF2FFF8)

// ── Secondary: muted teal ───────────────────────────────────────────
val Secondary10 = Color(0xFF0B1F1A)
val Secondary20 = Color(0xFF20352F)
val Secondary30 = Color(0xFF364B45)
val Secondary40 = Color(0xFF4D635C)
val Secondary50 = Color(0xFF667C75)
val Secondary60 = Color(0xFF7F968F)
val Secondary70 = Color(0xFF99B1A9)
val Secondary80 = Color(0xFFB4CCC4)
val Secondary90 = Color(0xFFD0E8E0)
val Secondary95 = Color(0xFFDEF7EE)
val Secondary99 = Color(0xFFF2FFF8)

// ── Tertiary: warm sage ─────────────────────────────────────────────
val Tertiary10  = Color(0xFF141E0E)
val Tertiary20  = Color(0xFF293422)
val Tertiary30  = Color(0xFF3F4B38)
val Tertiary40  = Color(0xFF57634E)
val Tertiary50  = Color(0xFF6F7C67)
val Tertiary60  = Color(0xFF889680)
val Tertiary70  = Color(0xFFA3B099)
val Tertiary80  = Color(0xFFBECCB4)
val Tertiary90  = Color(0xFFDAE8CF)
val Tertiary95  = Color(0xFFE8F6DD)
val Tertiary99  = Color(0xFFF7FFF0)

// ── Neutral tones (surfaces) ────────────────────────────────────────
val Neutral4   = Color(0xFF0C0F0E)
val Neutral6   = Color(0xFF111413)
val Neutral10  = Color(0xFF191C1B)
val Neutral12  = Color(0xFF1D201F)
val Neutral17  = Color(0xFF272B29)
val Neutral20  = Color(0xFF2E312F)
val Neutral22  = Color(0xFF323634)
val Neutral24  = Color(0xFF373A38)
val Neutral87  = Color(0xFFDADEDB)
val Neutral90  = Color(0xFFE1E3E0)
val Neutral92  = Color(0xFFE7E9E6)
val Neutral94  = Color(0xFFEDEFEC)
val Neutral95  = Color(0xFFF0F1EE)
val Neutral96  = Color(0xFFF3F5F2)
val Neutral98  = Color(0xFFF9FAF7)
val Neutral99  = Color(0xFFFCFDF9)

// ── Neutral variant (outlines, on-surface-variant) ──────────────────
val NeutralVar10 = Color(0xFF151D1A)
val NeutralVar20 = Color(0xFF29322E)
val NeutralVar30 = Color(0xFF404944)
val NeutralVar40 = Color(0xFF57605C)
val NeutralVar50 = Color(0xFF707974)
val NeutralVar60 = Color(0xFF89938E)
val NeutralVar70 = Color(0xFFA4ADA8)
val NeutralVar80 = Color(0xFFBFC8C3)
val NeutralVar90 = Color(0xFFDBE5DF)

// ── Error / Salmon red ──────────────────────────────────────────────
val Error10  = Color(0xFF410002)
val Error20  = Color(0xFF690005)
val Error30  = Color(0xFF93000A)
val Error40  = Color(0xFFBA1A1A)
val Error80  = Color(0xFFFFB4AB)
val Error90  = Color(0xFFFFDAD6)
val Error100 = Color(0xFFFFFFFF)

// ── Status colors: softer, more pleasing ────────────────────────────
val StatusComplete  = Color(0xFFA3B89A)  // Soft sage green
val StatusWip       = Color(0xFFF5E6A3)  // Pale warm yellow
val StatusEmpty     = Color(0xFFE8A598)  // Salmon / soft coral

// Dark mode status (slightly brighter for contrast on dark surfaces)
val StatusCompleteDark = Color(0xFFB4CCA9)
val StatusWipDark      = Color(0xFFF0DDA0)
val StatusEmptyDark    = Color(0xFFE0978A)

// ── Activity group colors (harmonious with sea green) ───────────────
val GroupApartments  = Color(0xFF80CBC4) // Teal 200
val GroupHandingOver = Color(0xFFFFCC80) // Amber 200
val GroupCommonArea  = Color(0xFFA5D6A7) // Green 200
val GroupFacade      = Color(0xFFCE93D8) // Purple 200

val GroupColors = listOf(GroupApartments, GroupHandingOver, GroupCommonArea, GroupFacade)

// ── 5-level percentage color scale ──────────────────────────────────
// Level 1: 0%         — salmon (empty)
// Level 2: 1-25%      — warm peach
// Level 3: 26-50%     — pale amber
// Level 4: 51-84%     — light sage
// Level 5: 85-100%    — sage green (complete)
val PctLevel1      = Color(0xFFE8A598)  // 0%
val PctLevel2      = Color(0xFFF0C5A0)  // 1-25%
val PctLevel3      = Color(0xFFF5E6A3)  // 26-50%
val PctLevel4      = Color(0xFFCCDDB3)  // 51-84%
val PctLevel5      = Color(0xFFA3B89A)  // 85-100%

val PctLevel1Dark  = Color(0xFFE0978A)
val PctLevel2Dark  = Color(0xFFE5B898)
val PctLevel3Dark  = Color(0xFFF0DDA0)
val PctLevel4Dark  = Color(0xFFC0D0A8)
val PctLevel5Dark  = Color(0xFFB4CCA9)
