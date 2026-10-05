package com.vaultzero.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.vaultzero.app.data.local.dao.EntryDao
import com.vaultzero.app.data.local.dao.GroupDao
import com.vaultzero.app.data.local.dao.VaultMetadataDao
import com.vaultzero.app.data.local.entity.EntryEntity
import com.vaultzero.app.data.local.entity.GroupEntity
import com.vaultzero.app.data.local.entity.VaultMetadataEntity

/**
 * Room database for VaultZero.
 *
 * This database lives inside a SQLCipher-encrypted SQLite file. The passphrase is
 * provided at runtime via [net.sqlcipher.database.SupportFactory]; Room itself
 * is unaware of the encryption.
 *
 * Schema version history:
 *   1 - Initial schema (metadata, entries, groups)
 */
@Database(
    entities = [
        VaultMetadataEntity::class,
        GroupEntity::class,
        EntryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultMetadataDao(): VaultMetadataDao
    abstract fun groupDao(): GroupDao
    abstract fun entryDao(): EntryDao
}
