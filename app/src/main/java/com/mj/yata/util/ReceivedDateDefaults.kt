package com.mj.yata.util

import java.time.LocalDate

/** Resolve at submit time: unedited fields follow the creation day, including after midnight.
 * A selected date wins over title parsing; each bulk row passes its own parsed date. */
fun receivedDateForCreation(
    selectedDate: String?,
    manuallySet: Boolean,
    parsedDate: String?,
    creationDate: String = LocalDate.now().toString()
): String? = if (manuallySet) selectedDate else parsedDate ?: creationDate
