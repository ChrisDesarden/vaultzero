package com.vaultzero.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vaultzero.app.data.local.entity.EntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {

    @Query("SELECT * FROM entries ORDER BY title ASC")
    fun observeAll(): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries ORDER BY title ASC")
    suspend fun getAll(): List<EntryEntity>

    @Query("SELECT * FROM entries WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): EntryEntity?

    @Query("SELECT * FROM entries WHERE group_uuid = :groupUuid ORDER BY title ASC")
    fun observeByGroup(groupUuid: String): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE favorite = 1 ORDER BY title ASC")
    fun observeFavorites(): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE title LIKE '%' || :query || '%' OR username LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' ORDER BY title ASC")
    suspend fun search(query: String): List<EntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: EntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<EntryEntity>)

    @Update
    suspend fun update(entry: EntryEntity)

    @Delete
    suspend fun delete(entry: EntryEntity)

    @Query("DELETE FROM entries WHERE uuid = :uuid")
    suspend fun deleteByUuid(uuid: String)

    @Query("DELETE FROM entries WHERE group_uuid = :groupUuid")
    suspend fun deleteByGroup(groupUuid: String)

    @Query("SELECT COUNT(*) FROM entries")
    suspend fun count(): Int
}
