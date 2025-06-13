// NotificationDao.kt
package com.example.lta

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NotificationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: NotificationEntity)

    @Query("SELECT * FROM notifications")
    suspend fun getAllNotifications(): List<NotificationEntity>

    // NEW: Deletes a list of notifications by their primary keys (IDs).
    @Query("DELETE FROM notifications WHERE id IN (:notificationIds)")
    suspend fun deleteByIds(notificationIds: List<Int>)
}
