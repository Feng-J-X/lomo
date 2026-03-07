package com.lomo.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "MemoPin",
    indices = [Index(value = ["pinnedAt"])],
)
data class MemoPinEntity(
    @PrimaryKey val memoId: String,
    val pinnedAt: Long,
)
