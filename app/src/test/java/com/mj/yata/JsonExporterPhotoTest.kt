package com.mj.yata

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the shape of the photo fields in a backup payload. The encode/decode helpers themselves
 * need a real Context (filesDir, Base64), so what's asserted here is the JSON contract those
 * helpers depend on — specifically the two behaviours that made photos not survive a restore:
 * a missing key must read as null rather than the string "null", and an absent photoData must
 * leave the legacy photoUri fallback reachable.
 */
class JsonExporterPhotoTest {

    @Test
    fun absentProfilePhoto_readsAsNull_notTheStringNull() {
        val root = JSONObject().apply { put("version", 4) }
        // optString(name, null) is the accessor the importer uses; verify it really yields null
        // for a key that was never written, since optString(name) alone returns "" instead.
        assertNull(root.optString("profilePhoto", null))
    }

    @Test
    fun presentProfilePhoto_roundTripsThroughJson() {
        val encoded = "aGVsbG8td29ybGQ="
        val root = JSONObject().apply { put("profilePhoto", encoded) }
        assertEquals(encoded, root.optString("profilePhoto", null))
    }

    @Test
    fun personWithoutPhotoData_stillExposesLegacyPhotoUri() {
        // A backup written before photoData existed: photoUri must remain readable so those
        // restores are no worse than they were.
        val person = JSONObject().apply {
            put("id", "p1")
            put("photoUri", "file:///data/user/0/com.mj.yata/files/avatars/a.png")
        }
        assertNull(person.optString("photoData", null))
        assertTrue(person.optString("photoUri").endsWith("a.png"))
    }

    @Test
    fun personWithNullPhotoUri_isDetectedAsNull() {
        val person = JSONObject().apply { put("photoUri", JSONObject.NULL) }
        assertTrue(person.isNull("photoUri"))
    }

    @Test
    fun profileNameAndEmail_roundTripThroughJson() {
        val root = JSONObject().apply {
            put("profileName", "Ada Lovelace")
            put("profileEmail", "ada@example.com")
        }
        assertEquals("Ada Lovelace", root.optString("profileName", null))
        assertEquals("ada@example.com", root.optString("profileEmail", null))
    }

    @Test
    fun backupWrittenBeforeProfileFieldsExisted_readsThemAsNull() {
        // An older backup carries neither key. Both must read as null so the importer skips them
        // and leaves a name/email set since that backup was taken intact, rather than blanking
        // them out with "".
        val root = JSONObject().apply { put("version", 4) }
        assertNull(root.optString("profileName", null))
        assertNull(root.optString("profileEmail", null))
    }

    @Test
    fun settingsRoundTripThroughJson_carryingTheirTypeTag() {
        val arr = org.json.JSONArray()
        arr.put(JSONObject().apply { put("name", "theme_mode"); put("type", "string"); put("value", "AMOLED") })
        arr.put(JSONObject().apply { put("name", "haptics_enabled"); put("type", "bool"); put("value", false) })
        arr.put(JSONObject().apply { put("name", "cloud_interval"); put("type", "long"); put("value", 1440L) })
        val root = JSONObject().apply { put("settings", arr) }

        // Serialised and re-parsed, because that is what a backup file actually does — and it is
        // where the type information is lost. In memory org.json keeps a Long as a Long; only
        // after a text round trip does 1440 come back as an Integer.
        val read = JSONObject(root.toString()).getJSONArray("settings")
        assertEquals(3, read.length())
        assertEquals("AMOLED", read.getJSONObject(0).getString("value"))
        assertEquals(false, read.getJSONObject(1).getBoolean("value"))
        // The reason the type tag exists: rebuilding a longPreferencesKey from this would reject
        // an Integer, so importPortableSettings coerces through Number using the tag.
        assertEquals("long", read.getJSONObject(2).getString("type"))
        assertTrue(read.getJSONObject(2).get("value") is Int)
        assertEquals(1440L, (read.getJSONObject(2).get("value") as Number).toLong())
    }

    @Test
    fun stringSetSetting_survivesAsAJsonArray() {
        val values = org.json.JSONArray().apply { put("a"); put("b") }
        val entry = JSONObject().apply { put("name", "saved_filters"); put("type", "stringSet"); put("value", values) }
        val raw = entry.get("value")
        assertTrue(raw is org.json.JSONArray)
        val decoded = (0 until (raw as org.json.JSONArray).length()).mapNotNull { raw.optString(it, null) }.toSet()
        assertEquals(setOf("a", "b"), decoded)
    }

    @Test
    fun backupWithoutSettings_leavesCurrentSettingsAlone() {
        // An older backup has no settings array; the importer must skip rather than clear.
        val root = JSONObject().apply { put("version", 4) }
        assertNull(root.optJSONArray("settings"))
    }

    @Test
    fun blankProfileFields_areSkippedByTheImportGuard() {
        // The exporter omits blank values, but a hand-edited or third-party file could still carry
        // them; the importer's isNotBlank() guard is what stops those from wiping a set profile.
        val root = JSONObject().apply {
            put("profileName", "")
            put("profileEmail", "   ")
        }
        assertNull(root.optString("profileName", null)?.takeIf { it.isNotBlank() })
        assertNull(root.optString("profileEmail", null)?.takeIf { it.isNotBlank() })
    }
}
