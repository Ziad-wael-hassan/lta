// AppDaos.kt (you can put them all in one file or separate)
package com.example.lta

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface SmsDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(smsList: List<SmsEntity>)

    @Query("SELECT * FROM sms_log WHERE synced = 0") // Get only unsynced
    suspend fun getUnsyncedSms(): List<SmsEntity>

    @Query("UPDATE sms_log SET synced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Long>)

    @Query("SELECT MAX(date) FROM sms_log")
    suspend fun getLatestSmsTimestamp(): Long?
}

@Dao
interface CallLogDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(callLogs: List<CallLogEntity>)

    @Query("SELECT * FROM call_log WHERE synced = 0")
    suspend fun getUnsyncedCallLogs(): List<CallLogEntity>

    @Query("UPDATE call_log SET synced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Long>)

    @Query("SELECT MAX(date) FROM call_log")
    suspend fun getLatestCallLogTimestamp(): Long?
}

@Dao
interface ContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contacts: List<ContactEntity>)

    @Query("SELECT * FROM contacts_log WHERE synced = 0")
    suspend fun getUnsyncedContacts(): List<ContactEntity>

    @Query("UPDATE contacts_log SET synced = 1 WHERE contactId IN (:ids)")
    suspend fun markAsSynced(ids: List<String>)
    
    @Query("SELECT * FROM contacts_log")
    suspend fun getAllContacts(): List<ContactEntity>
}
