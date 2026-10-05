package com.mj.yata.util.nl

import java.time.DayOfWeek
import java.time.LocalDate

data class LanguagePack(
    val id: String,
    val weekdays: Map<String, DayOfWeek> = emptyMap(),
    val months: Map<String, Int> = emptyMap(),
    val relativeDateWords: Map<String, (LocalDate) -> LocalDate> = emptyMap()
)
