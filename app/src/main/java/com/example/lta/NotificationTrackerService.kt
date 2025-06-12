package com.example.lta

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

class NotificationTrackerService : NotificationListenerService() {

    private val TAG = "NotificationTracker"
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // MODIFIED: Use ApiClient instead of TelegramBotApi
    private lateinit var apiClient: ApiClient
    private lateinit var notificationDao: NotificationDao

    override fun onCreate() {
        super.onCreate()
        notificationDao = NotificationDatabase.getDatabase(applicationContext).notificationDao()
        // MODIFIED: Initialize ApiClient with the server URL from string resources
        apiClient = ApiClient(applicationContext.getString(R.string.server_base_url))
        Log.d(TAG, "NotificationTrackerService created.")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        // Optional: Ignore your own app's notifications
        if (packageName == applicationContext.packageName) {
            return
        }

        Log.d(TAG, "Notification Posted: ${sbn.packageName}")
        val notificationEntity = NotificationEntity(
            packageName = packageName,
            title = sbn.notification.extras.getString(android.app.Notification.EXTRA_TITLE),
            text = sbn.notification.extras.getString(android.app.Notification.EXTRA_TEXT),
            postTime = sbn.postTime
        )

        serviceScope.launch {
            // We can still save to the DB as a backup, but the primary action is to send immediately.
            notificationDao.insert(notificationEntity)
            Log.d(TAG, "Notification saved to DB: ${notificationEntity.packageName}")

            // Attempt to send immediately to the server
            sendNotificationToServer(notificationEntity)
        }
    }

    // MODIFIED: This method now sends data to your Express server
    private suspend fun sendNotificationToServer(notification: NotificationEntity) {
        withContext(Dispatchers.IO) {
            val message = "🔔 New Notification:\nApp: ${notification.packageName}\nTitle: ${notification.title ?: "N/A"}\nText: ${notification.text ?: "N/A"}"
            val success = apiClient.uploadText(message)

            if (success) {
                // If sent successfully, we can remove it from the local DB.
                notificationDao.delete(notification.id)
                Log.d(TAG, "Notification sent to server and removed from DB: ${notification.packageName}")
            } else {
                Log.e(TAG, "Failed to send notification to server: ${notification.packageName}. It will remain in DB for next sync.")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "NotificationTrackerService destroyed.")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // This is usually not needed, but you can log it for debugging
        // Log.d(TAG, "Notification Removed: ${sbn.packageName}")
    }
}
