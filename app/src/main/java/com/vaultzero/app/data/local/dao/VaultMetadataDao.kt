package com.vaultzero.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vaultzero.app.data.local.entity.VaultMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultMetadataDao {

    @Query("SELECT * FROM vault_metadata WHERE id = 1 LIMIT 1")
    suspend fun get(): VaultMetadataEntity?

    @Query("SELECT * FROM vault_metadata WHERE id = 1 LIMIT 1")
    fun observe(): Flow<VaultMetadataEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(metadata: VaultMetadataEntity)

    @Update
    suspend fun update(metadata: VaultMetadataEntity)

    @Query("DELETE FROM vault_metadata WHERE id = 1")
    suspend fun delete()
}
