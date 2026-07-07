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
    indices = [Index("listId"), Index("projectId"), Index(value = ["deletedAt", "done", "dueDate"])]
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val listId: String?,
    val projectId: String?,
    val section: String, // "Morning" | "Afternoon"
    val dueDate: String?, // "YYYY-MM-DD"
    val dueTime: String?, // "2:00 PM"
    val reminder: String?, // "15 min before"
    val priority: String, // "none" | "low" | "med" | "high"
    val flag: Boolean,
    val done: Boolean,
    val completedAt: Long? = null,
    val deletedAt: Long? = null,
    val notes: String?,
    val recurrenceJson: String?, // JSON representation of Recurrence
    val sortOrder: Int = 0
)
