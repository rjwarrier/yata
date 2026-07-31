package com.mj.yata.domain.model

/**
 * What a swipe across a task row does, per direction.
 *
 * Limited to the things a task row can already do on its own. Flag and archive are deliberately
 * absent: neither has a callback on `TaskRow`, and adding one would mean threading a new lambda
 * through every screen that lists tasks — a much larger change than the setting is worth.
 */
enum class SwipeAction {
    NONE,
    COMPLETE,
    DELETE,
    SNOOZE_TOMORROW,
    EDIT_TITLE
}

/**
 * Which tab the app opens on.
 *
 * [LAST_USED] is the existing behaviour and stays the default. The rest are the fixed tab ids from
 * `CustomBottomNav` — note those don't shift when a tab is hidden by a feature flag, so a startup
 * tab pointing at a disabled tab has to be resolved back to Today rather than trusted blindly.
 */
enum class StartupTab(val tabId: Int) {
    LAST_USED(-1),
    TODAY(0),
    PROJECTS(1),
    PEOPLE(2),
    TAGS(3),
    UPCOMING(4)
}
