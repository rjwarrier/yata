package com.mj.yata

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the backup payload's shape for the fields added in 0.88 — Task.followUpAt and
 * Project.sectionNames. Both shipped as DB columns without their JSON counterparts, so a
 * backup/restore round trip silently dropped them; these assert the contract the exporter and
 * importer now share. Same approach as [JsonExporterPhotoTest]: the exporter itself needs a real
 * Context, so what's pinned here is the JSON behaviour the read/write paths depend on.
 */
class JsonExporterFieldsTest {

    @Test
    fun absentFollowUpAt_readsAsNull_notEpochZero() {
        // The trap this guards: optLong on a missing key returns 0, which is a *valid* past
        // timestamp — a restored task would read as "follow-up set to Jan 1 1970". The importer
        // checks isNull first, which is true for a key that was never written.
        val task = JSONObject().apply { put("id", "t1") }
        assertTrue(task.isNull("followUpAt"))
        assertEquals(0L, task.optLong("followUpAt"))
    }

    @Test
    fun explicitNullFollowUpAt_isDetectedAsNull() {
        // What the exporter actually writes for a task with no follow-up set.
        val task = JSONObject().apply { put("followUpAt", JSONObject.NULL) }
        assertTrue(task.isNull("followUpAt"))
    }

    @Test
    fun followUpAt_survivesATextRoundTrip_asALong() {
        // Timestamps are far outside Int range, so unlike the settings values in
        // JsonExporterPhotoTest these come back as Long rather than Int — but the read still goes
        // through optLong so it holds either way.
        val millis = 1_785_000_000_000L
        val task = JSONObject().apply { put("followUpAt", millis) }
        val read = JSONObject(task.toString())
        assertTrue(!read.isNull("followUpAt"))
        assertEquals(millis, read.optLong("followUpAt"))
    }

    @Test
    fun absentSectionNames_readsAsNullArray_soImportYieldsEmptyList() {
        // A backup written before sections existed. optJSONArray returns null, and the importer
        // maps that to an empty list — correct, since that project genuinely had no sections.
        val project = JSONObject().apply { put("id", "p1") }
        assertNull(project.optJSONArray("sectionNames"))
    }

    @Test
    fun sectionNames_roundTripPreservesOrder() {
        // Order is display order in ProjectDetailScreen, so it has to survive verbatim.
        val names = JSONArray().apply { put("Design"); put("Backend"); put("QA") }
        val project = JSONObject().apply { put("sectionNames", names) }

        val read = JSONObject(project.toString()).optJSONArray("sectionNames")!!
        val decoded = (0 until read.length()).map { read.getString(it) }
        assertEquals(listOf("Design", "Backend", "QA"), decoded)
    }

    @Test
    fun sectionNameContainingAComma_survivesIntact() {
        // Section names are free-typed display text, not IDs. This is why the entity layer joins
        // them with a Record Separator instead of the comma commonTagIds uses — a name like this
        // would otherwise split into two on the way back out of the database.
        val names = JSONArray().apply { put("Design, final") }
        val project = JSONObject().apply { put("sectionNames", names) }

        val read = JSONObject(project.toString()).optJSONArray("sectionNames")!!
        assertEquals(1, read.length())
        assertEquals("Design, final", read.getString(0))
    }

    @Test
    fun emptySectionNames_roundTripsAsAnEmptyArray_notAMissingKey() {
        // A project that had sections and then had them all removed must restore with none,
        // rather than the key vanishing and the importer having nothing to distinguish it from
        // a pre-sections backup. Either way the result is the same empty list, which is why this
        // is safe — but the array is what the exporter writes.
        val project = JSONObject().apply { put("sectionNames", JSONArray()) }
        val read = JSONObject(project.toString()).optJSONArray("sectionNames")!!
        assertEquals(0, read.length())
    }
}
