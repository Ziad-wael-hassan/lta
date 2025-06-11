package com.example.lta

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import androidx.work.*
import java.util.concurrent.TimeUnit

class MainApplication : Application(), Configuration.Provider {

    companion object {
        const val LOCATION_CHANNEL_ID = "location_service_channel"
        const val NOTIFICATION_CHANNEL_ID = "notifications_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        setupWorkManager()
        startLocationService()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Location Service Channel
            NotificationChannel(
                LOCATION_CHANNEL_ID,
                "Location Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Used for background location tracking"
                getSystemService(NotificationManager::class.java).createNotificationChannel(this)
            }

            // General Notifications Channel
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

    fun setupWorkManager() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val dataUploadRequest = PeriodicWorkRequestBuilder<DataUploadWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DataUploadWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            dataUploadRequest
        )
    }

    private fun startLocationService() {
        val locationIntent = Intent(this, LocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(locationIntent)
        } else {
            startService(locationIntent)
        }
    }

    // FIXED: Changed from a function to a property override to correctly implement Configuration.Provider
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()
}
