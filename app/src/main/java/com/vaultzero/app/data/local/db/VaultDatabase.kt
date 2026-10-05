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
 * SQLCipher-backed Room database for VaultZero.
 *
 * The actual encryption is handled by SQLCipher via [net.sqlcipher.database.SupportFactory]
 * passed to [Room.databaseBuilder] at repository construction time.
 *
 * This class is abstract; Room generates the implementation at compile time.
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
