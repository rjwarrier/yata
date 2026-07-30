package com.mj.yata.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/** Provided once at the MainActivity root from the Haptics Settings toggle — read directly by
 * whichever composables trigger haptic feedback (TaskRow, DragDropReorderableColumn), instead of
 * threading a boolean through every call site down to them. */
val LocalHapticsEnabled = staticCompositionLocalOf { true }

/** Global row-swipe preference. Screens can still disable swipes for selection/drag states,
 * while this controls the user's default everywhere TaskRow is used. */
val LocalTaskSwipeActionsEnabled = staticCompositionLocalOf { true }

/** Whether each task draws as its own raised card rather than a flat row. Read by TaskRow itself
 * so every list — Today, Upcoming, Project, List, Tag, Person, Search, Next Days — picks it up
 * from one place, instead of each screen passing a styling flag down. */
val LocalTaskCardBackground = staticCompositionLocalOf { false }
