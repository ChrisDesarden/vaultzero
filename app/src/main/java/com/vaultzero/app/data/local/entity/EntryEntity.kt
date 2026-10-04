package com.vaultzero.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for a password entry.
 *
 * SECURITY NOTE: password and notes are stored as encrypted ByteArray (AES-256-GCM
 * ciphertext). They are decrypted lazily by the repository layer into the domain model.
 * Never store plaintext in this entity.
 */
@Entity(
    tableName = "entries",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["group_uuid"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["group_uuid"]),
        Index(value = ["title"]),
        Index(value = ["favorite"])
    ]
)
data class EntryEntity(
    @PrimaryKey
    @ColumnInfo(name = "uuid")
    val uuid: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "username")
    val username: String = "",

    /** AES-256-GCM ciphertext of the password (includes nonce + tag) */
    @ColumnInfo(name = "password_cipher")
    val passwordCipher: ByteArray,

    @ColumnInfo(name = "url")
    val url: String = "",

    /** AES-256-GCM ciphertext of the notes (includes nonce + tag) */
    @ColumnInfo(name = "notes_cipher")
    val notesCipher: ByteArray? = null,

    @ColumnInfo(name = "tags")
    val tags: String = "",

    @ColumnInfo(name = "group_uuid")
    val groupUuid: String? = null,

    @ColumnInfo(name = "favorite")
    val favorite: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EntryEntity
        return uuid == other.uuid &&
                title == other.title &&
                username == other.username &&
                passwordCipher.contentEquals(other.passwordCipher) &&
                url == other.url &&
                (notesCipher == null && other.notesCipher == null || notesCipher != null && other.notesCipher != null && notesCipher.contentEquals(other.notesCipher)) &&
                tags == other.tags &&
                groupUuid == other.groupUuid &&
                favorite == other.favorite &&
                createdAt == other.createdAt &&
                updatedAt == other.updatedAt
    }

    override fun hashCode(): Int {
        var result = uuid.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + username.hashCode()
        result = 31 * result + passwordCipher.contentHashCode()
        result = 31 * result + url.hashCode()
        result = 31 * result + (notesCipher?.contentHashCode() ?: 0)
        result = 31 * result + tags.hashCode()
        result = 31 * result + (groupUuid?.hashCode() ?: 0)
        result = 31 * result + favorite.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        return result
    }
}
