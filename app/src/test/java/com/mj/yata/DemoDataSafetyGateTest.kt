package com.mj.yata

import com.mj.yata.data.demo.DemoDataSafetyGate
import com.mj.yata.data.demo.DemoModeDataAccessException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DemoDataSafetyGateTest {
    @Test fun demoBlocksOperationsBeforeTheirSideEffects() = runTest {
        val gate = DemoDataSafetyGate()
        var called = false
        try {
            gate.withRealData({ true }, { "Paused" }) { called = true }
            fail("Demo transfer should be rejected")
        } catch (e: DemoModeDataAccessException) {
            assertEquals("Paused", e.message)
        }
        assertFalse(called)
    }

    @Test fun modeCannotSwitchDuringTransferAndNestedExportsDoNotDeadlock() = runTest {
        val gate = DemoDataSafetyGate()
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var demo = false
        var checks = 0
        val transfer = async {
            gate.withRealData({ checks++; demo }, { "Paused" }) {
                entered.complete(Unit)
                finish.await()
                gate.withRealData({ checks++; demo }, { "Paused" }) { assertFalse(demo) }
            }
        }
        entered.await()
        val switch = async { gate.changeMode { demo = true } }
        runCurrent()
        assertFalse(switch.isCompleted)
        assertFalse(demo)
        finish.complete(Unit)
        transfer.await()
        switch.await()
        assertTrue(demo)
        assertEquals(1, checks)
        try {
            gate.withRealData({ demo }, { "Paused" }) { fail("Queued transfer ran in demo mode") }
            fail("Expected rejection")
        } catch (_: DemoModeDataAccessException) { }
    }

    @Test fun cancelledTransferReleasesModeSwitch() = runTest {
        val gate = DemoDataSafetyGate()
        val entered = CompletableDeferred<Unit>()
        var demo = false
        val transfer = launch {
            gate.withRealData({ demo }, { "Paused" }) { entered.complete(Unit); awaitCancellation() }
        }
        entered.await()
        transfer.cancelAndJoin()
        gate.changeMode { demo = true }
        assertTrue(demo)
        gate.changeMode { demo = false }
        assertEquals("real", gate.withRealData({ demo }, { "Paused" }) { "real" })
    }
}
