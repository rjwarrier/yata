package com.mj.yata.data.local.db.entity

import androidx.room.Entity
import androidx.room.Fts4

@Fts4(contentEntity = TaskEntity::class)
@Entity(tableName = "tasks_fts")
data class TaskFtsEntity(
    val title: String,
    val notes: String?
)
