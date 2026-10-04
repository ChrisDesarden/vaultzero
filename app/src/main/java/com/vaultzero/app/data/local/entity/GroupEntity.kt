package com.vaultzero.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for vault groups/folders.
 * Groups form a tree via parentUuid; root groups have parentUuid = null.
 */
@Entity(
    tableName = "groups",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["parent_uuid"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["parent_uuid"]),
        Index(value = ["name"])
    ]
)
data class GroupEntity(
    @PrimaryKey
    @ColumnInfo(name = "uuid")
    val uuid: String,

    @ColumnInfo(name = "name")
    val name: String,

    /** null for root-level groups */
    @ColumnInfo(name = "parent_uuid")
    val parentUuid: String? = null,

    /** optional icon name / identifier */
    @ColumnInfo(name = "icon")
    val icon: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
