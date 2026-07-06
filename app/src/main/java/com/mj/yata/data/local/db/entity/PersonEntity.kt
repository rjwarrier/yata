package com.mj.yata.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "people",
    foreignKeys = [
        ForeignKey(
            entity = PersonGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("groupId")]
)
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
