package com.mj.yata

import com.mj.yata.data.local.backup.localBackupFingerprint
import com.mj.yata.data.local.backup.localBackupsToKeep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBackupRetentionTest {

    private fun name(day: String, time: String, fp: String? = "abc123") =
        "yata_local_${day}_$time" + (fp?.let { "_$it" } ?: "") + ".json.enc"

    @Test
    fun aBurstOfEditsDoesNotPushOutEarlierDays() {
        val yesterday = name("20261007", "180000")
        val twoDaysAgo = name("20261006", "090000")
        val todayBurst = (0 until 8).map { name("20261008", "1${it}0000") }
        val keep = localBackupsToKeep(todayBurst + yesterday + twoDaysAgo)
        assertTrue(yesterday in keep)
        assertTrue(twoDaysAgo in keep)
        // The three newest of today's burst, the other five pruned.
        assertEquals(todayBurst.sortedDescending().take(3).toSet() + yesterday + twoDaysAgo, keep)
    }

    @Test
    fun keepsOnlyTheNewestOfEachOfTheLastSevenDays() {
        val days = (1..10).map { name("202610%02d".format(it), "120000") }
        val keep = localBackupsToKeep(days)
        assertEquals(days.sortedDescending().take(7).toSet(), keep)
    }

    @Test
    fun readsTheFingerprintFromNewNamesOnly() {
        assertEquals("abc123", localBackupFingerprint(name("20261008", "120000")))
        assertNull(localBackupFingerprint(name("20261008", "120000", fp = null)))
    }
}
