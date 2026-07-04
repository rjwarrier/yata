package com.mj.yata.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "people")
data class PersonEntity(
    @PrimaryKey val id: String,
    val name: String,
    val initials: String,
    val color: String,
    val photoUri: String? = null,
    val isMe: Boolean = false,
    val groupId: String? = null,
    val starred: Boolean = false
)
