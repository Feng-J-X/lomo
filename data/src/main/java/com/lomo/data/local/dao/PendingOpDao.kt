package com.lomo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lomo.data.local.entity.PendingOpEntity

@Dao
interface PendingOpDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(op: PendingOpEntity): Long

    @Query("DELETE FROM pending_ops WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM pending_ops ORDER BY timestamp ASC")
    suspend fun getAll(): List<PendingOpEntity>
}
