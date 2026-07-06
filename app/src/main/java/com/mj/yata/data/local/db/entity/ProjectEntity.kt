package com.mj.yata.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: String,
    val icon: String,
    val dueDate: String? = null,
    val starred: Boolean = false,
    val commonTagIds: String = "", // comma-separated tag IDs auto-applied (live) to every task in this project
    val defaultReminder: String? = null,
    val description: String? = null // max 100 chars, enforced in ProjectEditorSheet
)
