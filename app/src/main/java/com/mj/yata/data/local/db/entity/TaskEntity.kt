package com.mj.yata.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = ListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    // deletedAt leads the composite index since it's the common predicate across the three
    // hottest task queries (getTasks/getDeletedTasks filter on it alone; the Today query adds
    // done + dueDate on top) — SQLite can use a leading prefix of a composite index, so this one
    // index serves all three instead of needing three separate ones.
    indices = [Index("listId"), Index("projectId"), Index(value = ["deletedAt", "done", "dueDate"]), Index("seriesId")]
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val listId: String?,
    val projectId: String?,
    val section: String, // "Morning" | "Afternoon"
    val dueDate: String?, // "YYYY-MM-DD"
    // The date this becomes actionable — "not before", the mirror of dueDate's "not after". Null
    // means available immediately, which is every task that existed before this column. A task
    // whose startDate is in the future is *deferred*: still live, still in its project/list, just
    // filtered out of Today so the day only shows what can actually be worked on.
    val startDate: String? = null, // "YYYY-MM-DD"
    val dueTime: String?, // "2:00 PM"
    val reminder: String?, // "15 min before"
    val priority: String, // "none" | "low" | "med" | "high"
    val flag: Boolean,
    val done: Boolean,
    val completedAt: Long? = null,
    // When the task was first created. Null for every row that predates this column — there is no
    // honest way to backfill it, and guessing (e.g. treating them as created "now") would make
    // every pre-existing task look brand new and skew age/turnaround metrics badly. Analytics
    // excludes null rows from those metrics rather than assuming a value.
    val createdAt: Long? = null,
    val deletedAt: Long? = null,
    val notes: String?,
    val recurrenceJson: String?, // JSON representation of Recurrence
    val sortOrder: Int = 0,
    // Links a recurring task's live row to every historical completed instance forked off it
    // (see YataRepositoryImpl.toggleTaskDone) — null for non-recurring tasks and for any
    // historical row completed before this column existed. Lazily assigned on first completion.
    val seriesId: String? = null,
    // Archived is distinct from deletedAt/Trash: the row stays fully intact and recoverable from
    // the Archive screen, it's just excluded from the default task listings. Mirrors the same
    // flag on Project/Person/YataList. A task can be archived without being deleted, and the two
    // states are independent — archiving does not touch deletedAt.
    val archived: Boolean = false,
    // epoch millis — "come back and check on this" date for a delegated task. See
    // Task.isWaitingOn in domain/model/DomainModels.kt for the filtering semantics.
    val followUpAt: Long? = null,
    // Planned effort in minutes; null = unestimated, which is not the same as 0. See
    // Task.estimateMinutes in domain/model/DomainModels.kt.
    val estimateMinutes: Int? = null
)
