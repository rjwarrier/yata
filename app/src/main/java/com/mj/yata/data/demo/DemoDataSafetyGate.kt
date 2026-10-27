package com.mj.yata.data.demo

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class DemoModeDataAccessException(message: String) : IllegalStateException(message)

/** Serializes mode changes against complete backup/sync/restore operations. Nested exporter
 * calls share the outer operation's permission, so a transfer can never change data sources. */
@Singleton
class DemoDataSafetyGate @Inject constructor() {
    private val mutex = Mutex()
    private class Permit(val gate: DemoDataSafetyGate) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<Permit>
    }

    suspend fun <T> withRealData(
        isDemoMode: suspend () -> Boolean,
        blockedMessage: () -> String,
        block: suspend () -> T
    ): T {
        if (currentCoroutineContext()[Permit]?.gate === this) return block()
        return mutex.withLock {
            if (isDemoMode()) throw DemoModeDataAccessException(blockedMessage())
            withContext(Permit(this)) { block() }
        }
    }

    suspend fun changeMode(block: suspend () -> Unit) {
        check(currentCoroutineContext()[Permit]?.gate !== this) { "Cannot change demo mode inside a data transfer" }
        mutex.withLock { block() }
    }
}
