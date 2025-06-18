// CallLogEntity.kt
package com.elfinsaddle.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_log")
data class CallLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    val type: Int, // e.g., CallLog.Calls.INCOMING_TYPE
    val date: Long,
    val duration: Long,
    val synced: Boolean = false // <-- NEW
)
