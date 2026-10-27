package com.mj.yata.util

/**
 * Decides whether the *periodic, automatic* backup run (`UnifiedBackupWorker`) can skip doing any
 * work at all this cycle, because nothing has changed since the last time it settled. Never
 * applies to a manually triggered sync/backup or to the debounced run that follows a real edit —
 * both of those only fire because something did change, so there is nothing to short-circuit.
 *
 * The fingerprint comparison alone is what decides "nothing to do here" — deliberately
 * unconditional on how long it has been since the last settled run. The periodic schedule's own
 * interval (minimum 15 minutes, see `UnifiedBackupWorker.schedule`) already rate-limits how often
 * this is even asked; gating the skip itself on a short time floor on top of that would mean the
 * fingerprint check (the actual point of this gate) almost never gets reached in practice, since a
 * real periodic tick is always further apart than any reasonable floor.
 */
object AutoBackupGate {
    /**
     * True when [lastFingerprint] exists and matches [currentFingerprint] — i.e. nothing in the
     * portable snapshot has changed since the last settled run. `null` (no prior settled run
     * recorded yet) always means "don't skip".
     */
    fun shouldSkip(lastFingerprint: String?, currentFingerprint: String): Boolean {
        return lastFingerprint != null && currentFingerprint == lastFingerprint
    }
}
