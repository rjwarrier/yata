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
    val description: String? = null, // max 100 chars, enforced in ProjectEditorSheet
    val excludeFromToday: Boolean = false,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
    // Section names joined with U+001E (Record Separator) rather than a comma — unlike
    // commonTagIds these are free-typed display text, not IDs, so a comma could legitimately
    // appear in a name.
    val sectionNames: String = ""
)
