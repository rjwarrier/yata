package com.mj.yata.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tags",
    foreignKeys = [
        ForeignKey(
            entity = TagGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("groupId")]
)
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: String,
    val groupId: String? = null,
    val starred: Boolean = false,
    val hideCompletedByDefault: Boolean = false
)
