package com.mj.yata.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

fun LocalDate.localized(style: FormatStyle = FormatStyle.MEDIUM): String =
    format(DateTimeFormatter.ofLocalizedDate(style).withLocale(Locale.getDefault()))

fun LocalDateTime.localized(): String =
    format(
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(Locale.getDefault())
    )

fun Instant.localized(zoneId: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.getDefault())
        .withZone(zoneId)
        .format(this)
