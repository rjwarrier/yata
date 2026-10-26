package com.mj.yata

import com.mj.yata.util.AutoBackupGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoBackupGateTest {

    private val fingerprint = "hash-a"

    @Test
    fun noPriorSettledRunNeverSkips() {
        val skip = AutoBackupGate.shouldSkip(
            lastFingerprint = null,
            currentFingerprint = fingerprint
        )
        assertFalse(skip)
    }

    @Test
    fun unchangedFingerprintSkips() {
        val skip = AutoBackupGate.shouldSkip(
            lastFingerprint = fingerprint,
            currentFingerprint = fingerprint
        )
        assertTrue(skip)
    }

    @Test
    fun changedFingerprintNeverSkips() {
        val skip = AutoBackupGate.shouldSkip(
            lastFingerprint = fingerprint,
            currentFingerprint = "hash-b"
        )
        assertFalse(skip)
    }

    @Test
    fun unchangedFingerprintStillSkipsNoMatterHowLongSinceLastRun() {
        // The whole point of this gate is to skip a periodic tick that found nothing changed,
        // regardless of how long it has been since the last settled run — the periodic worker's
        // own schedule (every 15+ minutes) already rate-limits how often this is even asked, so a
        // separate short time floor here would mean the fingerprint check never actually fires in
        // practice (see git history of this file for the bug this test used to encode).
        val skip = AutoBackupGate.shouldSkip(
            lastFingerprint = fingerprint,
            currentFingerprint = fingerprint
        )
        assertTrue(skip)
    }
}
