package com.mj.yata

import com.mj.yata.domain.model.Recurrence
import com.mj.yata.domain.model.RecurrenceEnds
import com.mj.yata.util.CURRENT_BACKUP_VERSION
import com.mj.yata.util.RecurrenceEvaluator
import com.mj.yata.util.isRecognizedBackup
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StabilityFuzzSmokeTest {

    @Test
    fun recurrenceOddInputsDoNotCrash() {
        val recurrences = listOf(
            Recurrence("weekly", 0, listOf("??", "", "MO"), null, RecurrenceEnds.Never),
            Recurrence("monthly", -12, null, 999, RecurrenceEnds.After(-1)),
            Recurrence("yearly", Int.MIN_VALUE, null, null, RecurrenceEnds.On("not-a-date")),
            Recurrence("nonsense", 1, listOf("NOPE"), -99, RecurrenceEnds.Never)
        )

        recurrences.forEach { recurrence ->
            assertTrue(runCatching {
                RecurrenceEvaluator.calculateNextOccurrence(recurrence, "not-a-date")
            }.isSuccess)
        }
    }

    @Test
    fun malformedBackupHeadersAreRejectedWithoutThrowing() {
        val payloads = listOf(
            JSONObject(),
            JSONObject().put("version", CURRENT_BACKUP_VERSION + 1).put("tasks", JSONArray()),
            JSONObject().put("version", CURRENT_BACKUP_VERSION).put("tasks", "not-array"),
            JSONObject().put("version", -1).put("tasks", JSONArray())
        )

        payloads.forEach { payload ->
            assertFalse(isRecognizedBackup(payload))
        }
    }
}
