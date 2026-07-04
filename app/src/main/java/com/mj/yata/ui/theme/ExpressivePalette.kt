package com.mj.yata.ui.theme

import androidx.compose.ui.graphics.Color

val AccentA = Color(0xFFF2B8A6)
val AccentB = Color(0xFFC7D08A)
val AccentC = Color(0xFFB8A8E8)
val AccentD = Color(0xFFF5D06F)
val AccentE = Color(0xFF8CC4A3)
val AccentF = Color(0xFFE69AB8)

fun expressiveAccent(index: Int): Color = when (index % 6) {
    0 -> AccentA
    1 -> AccentB
    2 -> AccentC
    3 -> AccentD
    4 -> AccentE
    else -> AccentF
}

fun parseHexColorOrFallback(hex: String?, fallback: Color): Color =
    try {
        if (hex.isNullOrBlank()) fallback else Color(android.graphics.Color.parseColor(hex))
    } catch (_: IllegalArgumentException) {
        fallback
    }
