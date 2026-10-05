package com.vaultzero.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for vault groups/folders.
 * Groups form a tree via parentId; root groups have parentId = null.
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

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as GroupEntity
        return uuid == other.uuid &&
                name == other.name &&
                parentUuid == other.parentUuid &&
                icon == other.icon &&
                sortOrder == other.sortOrder &&
                createdAt == other.createdAt &&
                updatedAt == other.updatedAt
    }

    override fun hashCode(): Int {
        var result = uuid.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + (parentUuid?.hashCode() ?: 0)
        result = 31 * result + (icon?.hashCode() ?: 0)
        result = 31 * result + sortOrder
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        return result
    }
}
