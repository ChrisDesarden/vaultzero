package com.vaultzero.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Singleton metadata row for the vault (id is forced to 1).
 * Stores KDF parameters and the encrypted database key (not the master key itself).
 * The database key is encrypted with AES-256-GCM using the derived master key.
 */
@Entity(tableName = "vault_metadata")
data class VaultMetadataEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = 1,

    /** KDF type: 1 = Argon2id, 2 = PBKDF2 */
    @ColumnInfo(name = "kdf_type")
    val kdfType: Int,

    /** 16-byte random salt used for key derivation */
    @ColumnInfo(name = "kdf_salt")
    val kdfSalt: ByteArray,

    /** JSON blob: {memoryKB, iterations, parallelism} for Argon2id or {iterations} for PBKDF2 */
    @ColumnInfo(name = "kdf_params")
    val kdfParams: ByteArray,

    /** AES-256-GCM ciphertext of the DB key (includes 12-byte nonce + 16-byte tag) */
    @ColumnInfo(name = "encrypted_db_key")
    val encryptedDbKey: ByteArray,

    /** Vault file format version */
    @ColumnInfo(name = "version")
    val version: Int = 1,

    /** Epoch millis */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    /** Epoch millis */
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VaultMetadataEntity
        return id == other.id &&
                kdfType == other.kdfType &&
                kdfSalt.contentEquals(other.kdfSalt) &&
                kdfParams.contentEquals(other.kdfParams) &&
                encryptedDbKey.contentEquals(other.encryptedDbKey) &&
                version == other.version &&
                createdAt == other.createdAt &&
                updatedAt == other.updatedAt
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + kdfType
        result = 31 * result + kdfSalt.contentHashCode()
        result = 31 * result + kdfParams.contentHashCode()
        result = 31 * result + encryptedDbKey.contentHashCode()
        result = 31 * result + version
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        return result
    }
}
