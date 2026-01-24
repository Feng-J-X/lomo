package com.lomo.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "file_cache")
data class FileCacheEntity(
    @PrimaryKey val filename: String,
    val uriString: String,
    val lastModified: Long,
)
