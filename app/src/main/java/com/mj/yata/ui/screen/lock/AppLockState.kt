package com.mj.yata.ui.screen.lock

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Process-scoped (plain JVM singleton, not tied to any Activity instance) lock state. Survives
 * `MainActivity` recreation on config changes (rotation, multi-window resize) — which would
 * otherwise re-run the "should I start locked?" check and spuriously re-lock — but resets
 * naturally on real process death, i.e. a genuine cold start. */
object AppLockState {
    var isLocked by mutableStateOf(false)
    var hasCheckedInitialLock = false
    var hasRegisteredProcessObserver = false

    /** Mirror of the app-lock DataStore pref, kept current across Activity recreation by each
     * `MainActivity` instance's own Compose tree — read from the process-lifecycle observer,
     * which is registered once and must not hold a stale reference to any one Activity instance. */
    var appLockEnabled = false

    /** Mirror of the auto-lock timeout pref (minutes; 0 = lock immediately on any backgrounding). */
    var appLockTimeoutMinutes = 0

    /** Set on ON_STOP, evaluated and cleared on the next ON_START — lets a brief backgrounding
     * (shorter than [appLockTimeoutMinutes]) skip re-locking instead of always locking instantly. */
    var backgroundedAtMillis: Long? = null
}
