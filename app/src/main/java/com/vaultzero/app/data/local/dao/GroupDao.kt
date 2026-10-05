package com.vaultzero.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vaultzero.app.data.local.entity.GroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {

    @Query("SELECT * FROM groups ORDER BY name ASC")
    fun observeAll(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups ORDER BY name ASC")
    suspend fun getAll(): List<GroupEntity>

    @Query("SELECT * FROM groups WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): GroupEntity?

    @Query("SELECT * FROM groups WHERE parent_uuid = :parentUuid ORDER BY name ASC")
    fun observeByParent(parentUuid: String): Flow<List<GroupEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: GroupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(groups: List<GroupEntity>)

    @Update
    suspend fun update(group: GroupEntity)

    @Delete
    suspend fun delete(group: GroupEntity)

    @Query("DELETE FROM groups WHERE uuid = :uuid")
    suspend fun deleteByUuid(uuid: String)

    @Query("SELECT COUNT(*) FROM groups")
    suspend fun count(): Int
}
