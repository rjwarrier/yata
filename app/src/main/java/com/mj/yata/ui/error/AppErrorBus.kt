package com.mj.yata.ui.error

import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-shot error messages travelling from background work to whatever screen happens to be on
 * top. A singleton rather than state on [com.mj.yata.ui.screen.main.MainViewModel] because the
 * ViewModel is scoped to the Main nav entry, while the failure it reports can happen while the
 * user is several destinations deep — the collector lives in MainActivity, above the NavHost, so
 * it covers every screen.
 *
 * Carries a string resource, not a formatted string: the emitter is a ViewModel or a worker with
 * no composable scope, and resolving the text at the collector keeps it on the user's chosen
 * app language even if that changed after the failure.
 */
@Singleton
class AppErrorBus @Inject constructor() {

    // extraBufferCapacity keeps tryEmit non-suspending from any context; replay = 0 so a message
    // is shown once and not replayed onto a screen that rotates in later.
    private val _messages = MutableSharedFlow<Int>(replay = 0, extraBufferCapacity = 8)
    val messages: SharedFlow<Int> = _messages.asSharedFlow()

    fun emit(@StringRes messageRes: Int) {
        _messages.tryEmit(messageRes)
    }
}
