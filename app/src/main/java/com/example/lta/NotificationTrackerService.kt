package com.example.lta

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NotificationTrackerService : NotificationListenerService() {

    private val TAG = "NotificationTracker"
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Define the set of apps we want to track
    private val targetApps = setOf(
        "com.instagram.android",
        "com.whatsapp",
        "org.telegram.messenger"
    )

    private lateinit var notificationDao: NotificationDao

    override fun onCreate() {
        super.onCreate()
        notificationDao = NotificationDatabase.getDatabase(applicationContext).notificationDao()
        Log.d(TAG, "NotificationTrackerService created.")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName

        // 1. Check if the notification is from one of our target apps
        if (packageName !in targetApps) {
            return
        }

        // 2. Extract the data
        val notification = sbn.notification
        val title = notification.extras.getString(android.app.Notification.EXTRA_TITLE)
        val text = notification.extras.getString(android.app.Notification.EXTRA_TEXT)

        // Ignore notifications with no content
        if (title.isNullOrBlank() && text.isNullOrBlank()) {
            return
        }

        Log.d(TAG, "Tracking notification from: $packageName")
        val notificationEntity = NotificationEntity(
            packageName = packageName,
            title = title,
            text = text,
            postTime = sbn.postTime
        )

        // 3. Save it to the database. That's it!
        serviceScope.launch {
            notificationDao.insert(notificationEntity)
            Log.d(TAG, "Saved notification from $packageName to DB.")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "NotificationTrackerService destroyed.")
    }
}
