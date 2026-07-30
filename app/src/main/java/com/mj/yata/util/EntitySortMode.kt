package com.mj.yata.util

/** Sort modes shared by the People and Tags tabs, both of which replaced manual
 * drag-and-drop ordering with an explicit sort-by picker. */
enum class EntitySortMode { NAME_ASC, NAME_DESC, TASK_COUNT_DESC, TASK_COUNT_ASC, OPEN_TASK_COUNT_DESC, STARRED_FIRST }

fun <T> List<T>.sortedByEntityMode(
    mode: EntitySortMode,
    name: (T) -> String,
    starred: (T) -> Boolean,
    taskCount: (T) -> Int,
    /** Not-done tasks only — [taskCount] counts completed ones too, which buries whoever
     * actually has work left behind whoever has the longest history. */
    openTaskCount: (T) -> Int
): List<T> = when (mode) {
    EntitySortMode.NAME_ASC -> sortedBy { name(it).lowercase() }
    EntitySortMode.NAME_DESC -> sortedByDescending { name(it).lowercase() }
    EntitySortMode.TASK_COUNT_DESC -> sortedByDescending { taskCount(it) }
    EntitySortMode.TASK_COUNT_ASC -> sortedBy { taskCount(it) }
    EntitySortMode.OPEN_TASK_COUNT_DESC ->
        sortedWith(compareByDescending<T> { openTaskCount(it) }.thenBy { name(it).lowercase() })
    EntitySortMode.STARRED_FIRST -> sortedWith(compareByDescending<T> { starred(it) }.thenBy { name(it).lowercase() })
}
