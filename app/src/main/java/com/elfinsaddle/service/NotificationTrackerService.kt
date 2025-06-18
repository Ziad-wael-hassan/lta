package com.elfinsaddle.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.elfinsaddle.MainApplication
import com.elfinsaddle.data.local.AppDatabase
import com.elfinsaddle.data.local.model.NotificationEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NotificationTrackerService : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationTracker"
        private val TARGET_APPS = setOf(
            "com.instagram.android",
            "com.whatsapp",
            "org.telegram.messenger"
        )
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val appDb: AppDatabase by lazy {
        (applicationContext as MainApplication).container.appDatabase
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName !in TARGET_APPS) return

        try {
            processNotification(sbn)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing notification from ${sbn.packageName}", e)
        }
    }

    private fun processNotification(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getString(android.app.Notification.EXTRA_TITLE)
        val text = extras.getString(android.app.Notification.EXTRA_TEXT)

        if (title.isNullOrBlank() && text.isNullOrBlank()) {
            Log.d(TAG, "Skipping empty notification from ${sbn.packageName}")
            return
        }

        Log.d(TAG, "Processing notification from: ${sbn.packageName}")
        
        val notificationEntity = NotificationEntity(
            packageName = sbn.packageName,
            title = title?.trim(),
            text = text?.trim(),
            postTime = sbn.postTime
        )

        serviceScope.launch {
            try {
                appDb.notificationDao().insert(notificationEntity)
                Log.d(TAG, "Successfully saved notification from ${sbn.packageName}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save notification to database", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "NotificationTrackerService destroyed.")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Notification listener connected.")
    }
}
