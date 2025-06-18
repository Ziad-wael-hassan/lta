// ContactEntity.kt
package com.elfinsaddle.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts_log")
data class ContactEntity(
    @PrimaryKey val contactId: String, // Use the system's contact ID as primary key
    val name: String,
    val phoneNumbers: String, // Store as a serialized string (e.g., JSON or delimited)
    var lastUpdated: Long, // To detect changes in existing contacts
    val synced: Boolean = false // <-- NEW
)
