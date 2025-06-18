package com.elfinsaddle.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val packageName: String,
    val title: String?,
    val text: String?,
    val postTime: Long,
    val synced: Boolean = false // <-- ADDED
)
