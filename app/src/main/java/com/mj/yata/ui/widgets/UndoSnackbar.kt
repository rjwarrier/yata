package com.mj.yata.ui.widgets

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
