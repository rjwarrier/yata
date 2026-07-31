package com.mj.yata.data.local.db.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class TaskWithRelations(
    @Embedded val task: TaskEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = TaskPersonCrossRef::class,
            parentColumn = "taskId",
            entityColumn = "personId"
        )
    )
    val assignees: List<PersonEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = TaskTagCrossRef::class,
            parentColumn = "taskId",
            entityColumn = "tagId"
        )
    )
    val tags: List<TagEntity>,
    /**
     * Carried on the list projection, not just [TaskDetailWithRelations], because two things
     * outside the detail screen need them: the "3/5" progress a row shows when a task has
     * subtasks, and the client-side search filter that Archive and Trash go through — the SQL
     * search joins subtasks itself, but archived/deleted rows bypass that query, so with this
     * empty they matched on every field *except* subtask title, silently and only there.
     *
     * Room satisfies this with one extra `WHERE taskId IN (...)` per emission rather than a row
     * multiplier on the main query, which is the same shape as the two junction relations above.
     */
    @Relation(
        parentColumn = "id",
        entityColumn = "taskId"
    )
    val subtaskEntities: List<SubtaskEntity>
)

data class TaskDetailWithRelations(
    @Embedded val task: TaskEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = TaskPersonCrossRef::class,
            parentColumn = "taskId",
            entityColumn = "personId"
        )
    )
    val assignees: List<PersonEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = TaskTagCrossRef::class,
            parentColumn = "taskId",
            entityColumn = "tagId"
        )
    )
    val tags: List<TagEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "taskId"
    )
    val subtaskEntities: List<SubtaskEntity>
)
