package com.mj.yata.ui.undo

import androidx.annotation.PluralsRes
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * An already-applied change that can still be taken back: [undo] reverts it if the user taps Undo
 * within the window. The message is a plurals resource plus its count, resolved by the collector
 * so it follows the current app language.
 */
class UndoRequest(
    @PluralsRes val messageRes: Int,
    val count: Int,
    val undo: () -> Unit
)

/**
 * Undo offers for actions that can be started from many screens at once — quick snooze sits on
 * every task row, bulk reschedule on every multi-select toolbar — so wiring a snackbar into each
 * call site would mean a dozen copies. Same shape as [com.mj.yata.ui.error.AppErrorBus]: one
 * collector in MainActivity, which shows the offer on whichever screen is on top (see
 * `UndoSnackbarHosts`).
 *
 * Unlike the delete flow, which holds the delete back until the window passes, these actions are
 * applied straight away and [UndoRequest.undo] reverses them: a snoozed task should leave Today
 * the moment it's snoozed, not four seconds later.
 */
@Singleton
class AppUndoBus @Inject constructor() {

    private val _requests = MutableSharedFlow<UndoRequest>(replay = 0, extraBufferCapacity = 8)
    val requests: SharedFlow<UndoRequest> = _requests.asSharedFlow()

    fun offer(request: UndoRequest) {
        _requests.tryEmit(request)
    }
}
