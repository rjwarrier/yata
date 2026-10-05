package com.mj.yata.domain.model

/**
 * What happens to the parent task when the last open subtask is checked.
 *
 * [ASK] preserves the current behavior: subtasks are saved, then the user decides whether the
 * parent task should be completed too.
 */
enum class SubtaskCompletionAction {
    ASK,
    AUTO_COMPLETE,
    NOTHING
}
