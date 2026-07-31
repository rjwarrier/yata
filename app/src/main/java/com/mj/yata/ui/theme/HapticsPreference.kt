package com.mj.yata.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/** Provided once at the MainActivity root from the Haptics Settings toggle — read directly by
 * whichever composables trigger haptic feedback (TaskRow, DragDropReorderableColumn), instead of
 * threading a boolean through every call site down to them. */
val LocalHapticsEnabled = staticCompositionLocalOf { true }

/** Global row-swipe preference. Screens can still disable swipes for selection/drag states,
 * while this controls the user's default everywhere TaskRow is used. */
val LocalTaskSwipeActionsEnabled = staticCompositionLocalOf { true }

/**
 * The Reduce Motion setting, for animations that should be skipped outright rather than shortened.
 *
 * [YataDur.applyReduceMotion] covers the ordinary case by dividing every duration by three, which
 * is right for transitions that still have to communicate something. It is not right for motion
 * that is purely decorative — that should not play at all — and there was no way to ask.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Whether each task draws as its own raised card rather than a flat row. Read by TaskRow itself
 * so every list — Today, Upcoming, Project, List, Tag, Person, Search, Next Days — picks it up
 * from one place, instead of each screen passing a styling flag down. */
val LocalTaskCardBackground = staticCompositionLocalOf { false }

/**
 * What a swipe in each direction does. Read by TaskRow for the same reason as
 * [LocalTaskSwipeActionsEnabled] — one provider at the root rather than two more parameters on
 * every list screen's row call. The defaults match the behaviour from before they were
 * configurable, so an install that never opens the setting behaves exactly as it did.
 */
val LocalSwipeRightAction = staticCompositionLocalOf { com.mj.yata.domain.model.SwipeAction.COMPLETE }
val LocalSwipeLeftAction = staticCompositionLocalOf { com.mj.yata.domain.model.SwipeAction.DELETE }
