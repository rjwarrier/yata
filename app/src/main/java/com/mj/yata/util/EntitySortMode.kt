package com.mj.yata.util

/** Sort modes shared by the People and Tags tabs, both of which replaced manual
 * drag-and-drop ordering with an explicit sort-by picker. */
enum class EntitySortMode { NAME_ASC, NAME_DESC, TASK_COUNT_DESC, TASK_COUNT_ASC, STARRED_FIRST }

fun <T> List<T>.sortedByEntityMode(
    mode: EntitySortMode,
    name: (T) -> String,
    starred: (T) -> Boolean,
    taskCount: (T) -> Int
): List<T> = when (mode) {
    EntitySortMode.NAME_ASC -> sortedBy { name(it).lowercase() }
    EntitySortMode.NAME_DESC -> sortedByDescending { name(it).lowercase() }
    EntitySortMode.TASK_COUNT_DESC -> sortedByDescending { taskCount(it) }
    EntitySortMode.TASK_COUNT_ASC -> sortedBy { taskCount(it) }
    EntitySortMode.STARRED_FIRST -> sortedWith(compareByDescending<T> { starred(it) }.thenBy { name(it).lowercase() })
}
