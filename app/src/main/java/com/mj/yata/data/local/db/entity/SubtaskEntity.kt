package com.mj.yata.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "subtasks",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SubtaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentSubtaskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("taskId"), Index("parentSubtaskId")]
)
data class SubtaskEntity(
    @androidx.room.PrimaryKey val id: String,
    val taskId: String,
    val parentSubtaskId: String?,
    val title: String,
    val done: Boolean,
    val sortOrder: Int
)
