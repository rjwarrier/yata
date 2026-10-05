package com.mj.yata

import com.mj.yata.domain.sync.SyncCommitMessage
import com.mj.yata.domain.sync.SyncDeviceLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncCommitMessageTest {

    @Test
    fun parsesWhatFormatWrote() {
        val message = SyncCommitMessage.format("Pixel 8", "42 tasks, 5 projects")
        val parsed = SyncCommitMessage.parse(message)
        assertEquals("Pixel 8", parsed.device)
        assertEquals("42 tasks, 5 projects", parsed.summary)
    }

    @Test
    fun parsesRealWorldCommitMessage() {
        val parsed = SyncCommitMessage.parse("YATA sync from Pixel 8 - 42 tasks, 5 projects")
        assertEquals("Pixel 8", parsed.device)
        assertEquals("42 tasks, 5 projects", parsed.summary)
    }

    @Test
    fun parsesUnsummarisedSnapshot() {
        // commitMessage() falls back to the literal "snapshot" when the payload can't be summarised.
        val parsed = SyncCommitMessage.parse("YATA sync from Pixel 8 - snapshot")
        assertEquals("Pixel 8", parsed.device)
        assertEquals("snapshot", parsed.summary)
    }

    @Test
    fun deviceNameContainingSeparatorKeepsItsSuffix() {
        // The split has to be the *last* separator: vendor model strings are arbitrary text and a
        // greedy first-match would cut the device name in half and swallow the counts.
        val parsed = SyncCommitMessage.parse("YATA sync from Galaxy S21 - Ultra - 7 tasks, 1 projects")
        assertEquals("Galaxy S21 - Ultra", parsed.device)
        assertEquals("7 tasks, 1 projects", parsed.summary)
    }

    @Test
    fun foreignCommitKeepsTextAsSummaryWithNoDevice() {
        // A commit made by hand or another tool must still appear in the feed, just unattributed.
        val parsed = SyncCommitMessage.parse("Update snapshot.json via web editor")
        assertNull(parsed.device)
        assertEquals("Update snapshot.json via web editor", parsed.summary)
    }

    @Test
    fun readsOnlyTheFirstLineOfAMultiLineMessage() {
        val parsed = SyncCommitMessage.parse("YATA sync from Pixel 8 - 3 tasks, 0 projects\n\nlong body text")
        assertEquals("Pixel 8", parsed.device)
        assertEquals("3 tasks, 0 projects", parsed.summary)
    }

    @Test
    fun blankMessageParsesToNothingRatherThanEmptyStrings() {
        val parsed = SyncCommitMessage.parse("")
        assertNull(parsed.device)
        assertNull(parsed.summary)
    }

    @Test
    fun prefixWithoutSeparatorStillAttributesTheDevice() {
        val parsed = SyncCommitMessage.parse("YATA sync from Pixel 8")
        assertEquals("Pixel 8", parsed.device)
        assertNull(parsed.summary)
    }

    @Test
    fun deviceLabelDropsManufacturerAlreadyRepeatedInModel() {
        assertEquals("Google Pixel 8", SyncDeviceLabel.fromModel("Google", "Google Pixel 8"))
    }

    @Test
    fun deviceLabelJoinsManufacturerAndModel() {
        assertEquals("Google Pixel 8", SyncDeviceLabel.fromModel("Google", "Pixel 8"))
    }

    @Test
    fun deviceLabelFallsBackWhenBuildFieldsAreEmpty() {
        assertEquals(SyncDeviceLabel.UNKNOWN, SyncDeviceLabel.fromModel("", ""))
        assertEquals(SyncDeviceLabel.UNKNOWN, SyncDeviceLabel.fromModel(null, null))
    }

    @Test
    fun deviceLabelSurvivesAMissingHalf() {
        assertEquals("Pixel 8", SyncDeviceLabel.fromModel(null, "Pixel 8"))
        assertEquals("Google", SyncDeviceLabel.fromModel("Google", null))
    }

    @Test
    fun userSetDeviceNameWinsOverTheModel() {
        assertEquals("Work phone", SyncDeviceLabel.of("Work phone", "Google", "Pixel 8"))
    }

    @Test
    fun unsetDeviceNameFallsBackToTheModel() {
        assertEquals("Google Pixel 8", SyncDeviceLabel.of(null, "Google", "Pixel 8"))
    }

    @Test
    fun blankDeviceNameFallsBackToTheModel() {
        // Some ROMs return an empty string rather than null when no name has been set; taking it
        // literally would label every snapshot from those devices with nothing at all.
        assertEquals("Google Pixel 8", SyncDeviceLabel.of("", "Google", "Pixel 8"))
        assertEquals("Google Pixel 8", SyncDeviceLabel.of("   ", "Google", "Pixel 8"))
    }

    @Test
    fun deviceNameIsTrimmedBeforeUse() {
        assertEquals("Work phone", SyncDeviceLabel.of("  Work phone  ", "Google", "Pixel 8"))
    }

    @Test
    fun aRenamedDeviceStillRoundTripsThroughTheCommitMessage() {
        val parsed = SyncCommitMessage.parse(
            SyncCommitMessage.format(SyncDeviceLabel.of("Ranjith's phone", "Google", "Pixel 8"), "9 tasks, 2 projects")
        )
        assertEquals("Ranjith's phone", parsed.device)
        assertEquals("9 tasks, 2 projects", parsed.summary)
    }
}
