// NotificationTrackerService.kt
package com.example.lta

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Service that tracks notifications from specified apps and stores them in database.
 * Extends NotificationListenerService to receive notification events system-wide.
 */
class NotificationTrackerService : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationTracker"
        
        // Define the set of apps we want to track - made immutable for thread safety
        private val TARGET_APPS = setOf(
            "com.instagram.android",
            "com.whatsapp",
            "org.telegram.messenger"
        )
    }

    // Use SupervisorJob to prevent child coroutine failures from canceling the entire scope
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Lazy initialization to avoid potential initialization issues
    private val notificationDao: NotificationDao by lazy {
        NotificationDatabase.getDatabase(applicationContext).notificationDao()
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "NotificationTrackerService created and ready to track notifications")
    }

    /**
     * Called when a new notification is posted to the status bar
     * Filters notifications from target apps and saves relevant data to database
     */
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Early return if notification is not from a target app
        if (sbn.packageName !in TARGET_APPS) {
            return
        }

        try {
            processNotification(sbn)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing notification from ${sbn.packageName}", e)
        }
    }

    /**
     * Processes and stores notification data from target apps
     */
    private fun processNotification(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val notification = sbn.notification
        
        // Safely extract notification content
        val extras = notification.extras
        val title = extras?.getString(android.app.Notification.EXTRA_TITLE)
        val text = extras?.getString(android.app.Notification.EXTRA_TEXT)

        // Skip notifications with no meaningful content
        if (title.isNullOrBlank() && text.isNullOrBlank()) {
            Log.d(TAG, "Skipping empty notification from $packageName")
            return
        }

        Log.d(TAG, "Processing notification from: $packageName")
        
        val notificationEntity = NotificationEntity(
            packageName = packageName,
            title = title?.trim(), // Trim whitespace for cleaner data
            text = text?.trim(),
            postTime = sbn.postTime
        )

        // Save to database asynchronously with error handling
        serviceScope.launch {
            try {
                notificationDao.insert(notificationEntity)
                Log.d(TAG, "Successfully saved notification from $packageName to database")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save notification from $packageName to database", e)
            }
        }
    }

    /**
     * Called when a notification is removed from the status bar
     * Currently not used but available for future functionality
     */
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Could be used for tracking notification dismissals if needed
        super.onNotificationRemoved(sbn)
    }

    /**
     * Called when the service is being destroyed
     * Properly cancels all coroutines to prevent memory leaks
     */
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "NotificationTrackerService destroyed and resources cleaned up")
    }

    /**
     * Called when the notification listener is connected
     * Useful for initialization or status logging
     */
    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "NotificationTrackerService connected to notification system")
    }

    /**
     * Called when the notification listener is disconnected
     * Useful for cleanup or status logging
     */
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "NotificationTrackerService disconnected from notification system")
    }
}
