package com.example.lta

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NotificationTrackerService : NotificationListenerService() {

    private val TAG = "NotificationTracker"
    private lateinit var notificationDao: NotificationDao
    private lateinit var telegramBotApi: TelegramBotApi

    // TODO: Replace with your actual bot token and chat ID, ideally from a secure source
    private val BOT_TOKEN = "YOUR_TELEGRAM_BOT_TOKEN"
    private val CHAT_ID = "YOUR_TELEGRAM_CHAT_ID"

    override fun onCreate() {
        super.onCreate()
        notificationDao = NotificationDatabase.getDatabase(applicationContext).notificationDao()
        telegramBotApi = TelegramBotApi(BOT_TOKEN, CHAT_ID)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Log.d(TAG, "Notification Posted: ${sbn.packageName} - ${sbn.notification.tickerText}")
        val notificationEntity = NotificationEntity(
            packageName = sbn.packageName,
            title = sbn.notification.extras.getString(android.app.Notification.EXTRA_TITLE),
            text = sbn.notification.extras.getString(android.app.Notification.EXTRA_TEXT),
            postTime = sbn.postTime
        )
        lifecycleScope.launch {
            notificationDao.insert(notificationEntity)
            Log.d(TAG, "Notification saved to DB: ${notificationEntity.packageName}")

            // Attempt to send immediately
            sendNotificationToTelegram(notificationEntity)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        Log.d(TAG, "Notification Removed: ${sbn.packageName}")
        // No direct action needed here based on requirements, as removal from DB happens after sending to Telegram.
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
                // Keep in DB to retry later via WorkManager
            }
        }
    }
} 