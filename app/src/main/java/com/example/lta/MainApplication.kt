// MainApplication.kt
package com.example.lta

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.*

class MainApplication : Application(), Configuration.Provider {

    companion object {
        const val LOCATION_CHANNEL_ID = "location_service_channel"
        const val NOTIFICATION_CHANNEL_ID = "notifications_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        // WorkManager tasks should be scheduled from the UI after permissions are granted,
        // not on application startup.
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel(
                LOCATION_CHANNEL_ID,
                "Location Updates", // Renamed as it's no longer a service
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for location worker status."
                getSystemService(NotificationManager::class.java).createNotificationChannel(this)
            }
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "App Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "General app notifications"
                getSystemService(NotificationManager::class.java).createNotificationChannel(this)
            }
        }
    }

    // You can keep this function if you intend to schedule your DataUploadWorker from the UI later.
    fun setupDataUploadWorker() {
        // ... implementation for DataUploadWorker if needed
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()
}
