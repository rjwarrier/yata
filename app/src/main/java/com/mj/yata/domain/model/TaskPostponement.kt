package com.mj.yata.domain.model

import java.time.LocalDate

const val DEFAULT_POSTPONEMENT_WARNING_THRESHOLD = 3
const val MIN_POSTPONEMENT_WARNING_THRESHOLD = 1
const val MAX_POSTPONEMENT_WARNING_THRESHOLD = 10
const val POSTPONEMENT_WARNING_THRESHOLD = DEFAULT_POSTPONEMENT_WARNING_THRESHOLD

fun postponementWarningThresholdFor(priority: String?, normalThreshold: Int): Int {
    val base = normalThreshold.coerceIn(
        MIN_POSTPONEMENT_WARNING_THRESHOLD,
        MAX_POSTPONEMENT_WARNING_THRESHOLD
    )
    return when (priority?.lowercase()) {
        "high" -> if (base <= 2) 0 else (base - 4).coerceAtLeast(1)
        "med", "medium" -> (base - 2).coerceAtLeast(1)
        else -> base.coerceAtLeast(1)
    }
}

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
