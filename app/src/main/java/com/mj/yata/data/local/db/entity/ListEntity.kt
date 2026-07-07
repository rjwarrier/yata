package com.mj.yata.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lists")
data class ListEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: String,
    val icon: String,
    val starred: Boolean = false,
    val excludeFromToday: Boolean = false,
    val sortOrder: Int = 0
)
