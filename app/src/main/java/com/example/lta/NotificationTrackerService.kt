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

class NotificationTrackerService : NotificationListenerService() {

    private val TAG = "NotificationTracker"
    private lateinit var notificationDao: NotificationDao
    private lateinit var telegramBotApi: TelegramBotApi

    // FIXED: Created a custom CoroutineScope for the service.
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        notificationDao = NotificationDatabase.getDatabase(applicationContext).notificationDao()
        // FIXED: Initialized API with string resources for consistency and best practice
        telegramBotApi = TelegramBotApi(
            applicationContext.getString(R.string.telegram_bot_token),
            applicationContext.getString(R.string.telegram_chat_id)
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Log.d(TAG, "Notification Posted: ${sbn.packageName} - ${sbn.notification.tickerText}")
        val notificationEntity = NotificationEntity(
            packageName = sbn.packageName,
            title = sbn.notification.extras.getString(android.app.Notification.EXTRA_TITLE),
            text = sbn.notification.extras.getString(android.app.Notification.EXTRA_TEXT),
            postTime = sbn.postTime
        )
        // FIXED: Used the custom serviceScope instead of the unavailable lifecycleScope
        serviceScope.launch {
            notificationDao.insert(notificationEntity)
            Log.d(TAG, "Notification saved to DB: ${notificationEntity.packageName}")

            // Attempt to send immediately
            sendNotificationToTelegram(notificationEntity)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        Log.d(TAG, "Notification Removed: ${sbn.packageName}")
    }

    // FIXED: Added onDestroy to cancel the CoroutineScope and prevent memory leaks.
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private suspend fun sendNotificationToTelegram(notification: NotificationEntity) {
        withContext(Dispatchers.IO) {
            val message = "New Notification:\nApp: ${notification.packageName}\nTitle: ${notification.title ?: "N/A"}\nText: ${notification.text ?: "N/A"}\nTime: ${java.util.Date(notification.postTime)}"
            val success = telegramBotApi.sendMessage(message)

            if (success) {
                notification.isSent = true
                notificationDao.update(notification) // Mark as sent in DB
                notificationDao.delete(notification.id) // Remove from DB after successful send
                Log.d(TAG, "Notification sent to Telegram and removed from DB: ${notification.packageName}")
            } else {
                Log.e(TAG, "Failed to send notification to Telegram: ${notification.packageName}")
            }
        }
    }
}
