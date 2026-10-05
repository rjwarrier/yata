package com.mj.yata.util.nl

import java.time.DayOfWeek
import java.time.LocalDate

object NaturalLanguageLexicon {
    private val packs = listOf(latinScriptPack)

    val weekdayNames: Map<String, DayOfWeek> = packs
        .flatMap { it.weekdays.entries }
        .associate { it.key to it.value }

    val monthNames: Map<String, Int> = packs
        .flatMap { it.months.entries }
        .associate { it.key to it.value }

    val relativeDateWords: Map<String, (LocalDate) -> LocalDate> = packs
        .flatMap { it.relativeDateWords.entries }
        .associate { it.key to it.value }
}

private val latinScriptPack = LanguagePack(
    id = "latin-script",
    weekdays = mapOf(
        "montag" to DayOfWeek.MONDAY,
        "lunedi" to DayOfWeek.MONDAY,
        "maandag" to DayOfWeek.MONDAY,
        "senin" to DayOfWeek.MONDAY,
        "thu hai" to DayOfWeek.MONDAY,
        "pazartesi" to DayOfWeek.MONDAY,
        "wtorek" to DayOfWeek.TUESDAY,
        "carsamba" to DayOfWeek.WEDNESDAY,
        "vrijdag" to DayOfWeek.FRIDAY,
        "sabtu" to DayOfWeek.SATURDAY,
        "sonntag" to DayOfWeek.SUNDAY,
        "chu nhat" to DayOfWeek.SUNDAY
    ),
    months = mapOf(
        "ottobre" to 10,
        "kasim" to 11,
        "wrzesnia" to 9,
        "thang muoi mot" to 11,
        "januar" to 1,
        "gennaio" to 1,
        "maart" to 3,
        "mayis" to 5,
        "desember" to 12
    ),
    relativeDateWords = mapOf(
        "morgen" to { ref: LocalDate -> ref.plusDays(1) },
        "domani" to { ref: LocalDate -> ref.plusDays(1) },
        "besok" to { ref: LocalDate -> ref.plusDays(1) },
        "ngay mai" to { ref: LocalDate -> ref.plusDays(1) },
        "gisteren" to { ref: LocalDate -> ref.minusDays(1) },
        "wczoraj" to { ref: LocalDate -> ref.minusDays(1) },
        "hari ini" to { ref: LocalDate -> ref },
        "hom nay" to { ref: LocalDate -> ref }
    )
)
