package com.mj.yata.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * "Today", readable from anywhere composition reaches. Same shape as [AppFormats]: plain snapshot
 * state rather than a CompositionLocal or ViewModel field, with a single writer.
 *
 * Screens across Today/Upcoming/Next 10 Days/Project/List/Tag/Person each used to compute their
 * own `remember { LocalDate.now() }` with no keys — correct on first composition, and then frozen
 * for the life of that composable. Leave the app open (or merely backgrounded, since the process
 * survives) across midnight and every one of those screens kept showing yesterday: Today's list
 * didn't pick up newly-due tasks, overdue badges stayed wrong, deferred tasks didn't un-defer, and
 * the progress ring kept counting against the wrong day.
 *
 * [YataApplication] owns the single writer: a coroutine that sleeps until the next local midnight
 * and loops, so it catches the rollover even if the app is never backgrounded. [MainActivity]
 * additionally calls [refresh] on every process-level `onStart`, the same trigger already used to
 * re-read the system's 12/24-hour setting — cheap insurance against clock/timezone changes made
 * while the app wasn't running to observe the midnight boundary itself.
 */
object AppClock {
    var today: LocalDate by mutableStateOf(LocalDate.now())
        private set

    /**
     * Now, truncated to the minute — the same idea as [today] one granularity down, for the
     * due-date countdown, which has to re-render as time passes rather than freeze at whatever
     * "in 5m" happened to be true when the row first composed.
     *
     * Truncated rather than raw so the value only changes on minute boundaries: it's Compose
     * snapshot state, so every change recomposes each reader, and nothing displayed off this is
     * finer-grained than a minute. Only composables that actually read it recompose, so rows
     * without a countdown are unaffected.
     *
     * Its writer is gated on the countdown setting being on — see [YataApplication] — since a
     * per-minute wakeup is worth avoiding entirely when nothing observes it, unlike [today]'s
     * once-a-day one.
     */
    var minute: LocalDateTime by mutableStateOf(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES))
        private set

    /** [today] as an ISO string, for the many call sites that compare dates as strings. */
    val todayString: String get() = today.toString()

    /** [today] as a cold [Flow], for non-Compose collectors (ViewModel `combine` chains) that
     * would otherwise capture `LocalDate.now()` once inside the chain and never see midnight. */
    val todayFlow: Flow<LocalDate> = snapshotFlow { today }

    fun refresh() {
        today = LocalDate.now()
        refreshMinute()
    }

    fun refreshMinute() {
        minute = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
    }
}
