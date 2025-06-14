// SmsEntity.kt
package com.example.lta.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sms_log")
data class SmsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val address: String,
    val body: String,
    val date: Long, // Store timestamp as Long
    val type: Int, // e.g., Telephony.Sms.MESSAGE_TYPE_INBOX
    val synced: Boolean = false // <-- NEW: Flag for sync status
)
