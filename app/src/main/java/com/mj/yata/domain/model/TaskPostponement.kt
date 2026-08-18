package com.mj.yata.domain.model

import java.time.LocalDate

const val POSTPONEMENT_WARNING_THRESHOLD = 3

fun isPostponedLater(previousDue: String?, nextDue: String?): Boolean {
    if (previousDue == null || nextDue == null) return false
    val previous = runCatching { LocalDate.parse(previousDue) }.getOrNull() ?: return false
    val next = runCatching { LocalDate.parse(nextDue) }.getOrNull() ?: return false
    return next.isAfter(previous)
}

fun nextPostponementCount(
    previousDue: String?,
    nextDue: String?,
    previousCount: Int
): Int =
    if (isPostponedLater(previousDue, nextDue)) previousCount + 1 else previousCount
