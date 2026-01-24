package com.lomo.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_ops")
data class PendingOpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // CREATE, UPDATE, DELETE
    val payload: String, // JSON or simple content
    val timestamp: Long,
)
