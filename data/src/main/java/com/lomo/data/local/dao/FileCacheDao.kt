package com.lomo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lomo.data.local.entity.FileCacheEntity

@Dao
interface FileCacheDao {
    @Query("SELECT * FROM file_cache WHERE filename = :filename")
    suspend fun getFileUri(filename: String): FileCacheEntity?

    @Query("SELECT * FROM file_cache")
    suspend fun getAllEntries(): List<FileCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FileCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<FileCacheEntity>)

    @Query("DELETE FROM file_cache WHERE filename = :filename")
    suspend fun delete(filename: String)

    @Query("DELETE FROM file_cache")
    suspend fun clearAll()
}
