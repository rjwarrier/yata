package com.mj.yata.ui.widgets

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Sentinel that marks a snackbar as a delete-undo one, so each screen's SnackbarHost knows to
 * render [DeleteUndoSnackbar] instead of a plain Snackbar.
 *
 * Deliberately NOT a translated string: it is never displayed — [DeleteUndoSnackbar] draws its
 * own button label with the countdown — and localizing it would break the host's equality check
 * in every non-English locale, silently reverting to the plain snackbar.
 */
const val UNDO_ACTION_LABEL = "Undo"

/** How long an undo stays available, in seconds. Provided from user preferences. */
val LocalUndoWindowSeconds = staticCompositionLocalOf { DEFAULT_UNDO_WINDOW_SECONDS }

const val DEFAULT_UNDO_WINDOW_SECONDS = 4

/**
 * The snackbar hosts of the screens currently composed, newest on top, so an app-level undo offer
 * ([com.mj.yata.ui.undo.AppUndoBus]) lands in the visible screen's own Scaffold host — positioned
 * above its bottom bar and FAB — rather than in one activity-wide host that would sit over them.
 * Only touched from composition and the main-thread collector, so it needs no locking.
 */
class UndoSnackbarHosts {
    private val hosts = mutableListOf<SnackbarHostState>()

    val top: SnackbarHostState? get() = hosts.lastOrNull()

    fun push(host: SnackbarHostState) {
        hosts.remove(host)
        hosts.add(host)
    }

    fun remove(host: SnackbarHostState) {
        hosts.remove(host)
    }
}

val LocalUndoSnackbarHosts = staticCompositionLocalOf<UndoSnackbarHosts?> { null }

/**
 * Makes [host] the target for app-level undo offers while this screen is composed. During a
 * navigation transition both screens are composed briefly; the destination registers last, so it
 * is the one on top.
 */
@Composable
fun RegisterUndoSnackbarHost(host: SnackbarHostState) {
    val hosts = LocalUndoSnackbarHosts.current ?: return
    DisposableEffect(hosts, host) {
        hosts.push(host)
        onDispose { hosts.remove(host) }
    }
}

/**
 * Shows a delete-undo snackbar and suspends until the user either undoes it or the window
 * elapses. Returns true if undo was tapped, i.e. the caller must NOT perform the delete.
 *
 * SnackbarDuration only offers Short (~4s) and Long (~10s), neither of which is configurable, so
 * the window is driven here instead: the snackbar is shown as Indefinite and this coroutine times
 * it out. Cancelling showSnackbar dismisses it, so the timeout path both hides the snackbar and
 * reports "not undone" — matching what SnackbarResult.Dismissed used to mean at each call site.
 */
suspend fun showUndoSnackbar(
    hostState: SnackbarHostState,
    message: String,
    seconds: Int = DEFAULT_UNDO_WINDOW_SECONDS
): Boolean = withTimeoutOrNull(seconds * 1000L) {
    hostState.showSnackbar(
        message = message,
        actionLabel = UNDO_ACTION_LABEL,
        duration = SnackbarDuration.Indefinite
    ) == SnackbarResult.ActionPerformed
} ?: false
