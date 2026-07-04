package com.mj.yata.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tag_groups")
data class TagGroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: String
)

@Entity(tableName = "person_groups")
data class PersonGroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: String
)
