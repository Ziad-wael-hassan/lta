package com.elfinsaddle

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import androidx.work.Configuration
import com.elfinsaddle.util.AppContainer
import com.elfinsaddle.util.DefaultAppContainer

class MainApplication : Application(), Configuration.Provider {

    lateinit var container: AppContainer

    companion object {
        const val LOCATION_CHANNEL_ID = "location_service_channel"
        const val NOTIFICATION_CHANNEL_ID = "notifications_channel"
    }

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
        Log.d("MainApplication", "App container initialized.")

        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            val locationChannel = NotificationChannel(
                LOCATION_CHANNEL_ID,
                "Location Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Notifications for location worker status." }

            val appChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "App Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "General app notifications" }
            
            notificationManager.createNotificationChannels(listOf(locationChannel, appChannel))
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.INFO)
            .build()
}
