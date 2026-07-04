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
    indices = [Index("listId"), Index("projectId")]
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
    val notes: String?,
    val recurrenceJson: String?, // JSON representation of Recurrence
    val subtasksJson: String? // JSON representation of List<Subtask>
)
