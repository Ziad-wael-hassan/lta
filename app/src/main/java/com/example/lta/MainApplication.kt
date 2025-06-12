package com.example.lta

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import androidx.work.Configuration
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainApplication : Application(), Configuration.Provider {

    // A coroutine scope for tasks that should live as long as the application.
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val LOCATION_CHANNEL_ID = "location_service_channel"
        const val NOTIFICATION_CHANNEL_ID = "notifications_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        // Trigger the initial token fetch. The actual registration is now handled
        // by MyFirebaseMessagingService.onNewToken()
        triggerInitialTokenFetch()
    }

    /**
     * Creates notification channels required by the app for Android O and above.
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            NotificationChannel(
                LOCATION_CHANNEL_ID,
                "Location Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for location worker status."
                notificationManager.createNotificationChannel(this)
            }
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "App Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "General app notifications"
                notificationManager.createNotificationChannel(this)
            }
        }
    }

    /**
     * Proactively fetches the FCM token on app start.
     * If a new token is generated, MyFirebaseMessagingService.onNewToken() will be
     * called automatically by the FCM SDK, which then handles server registration.
     * This ensures registration happens as early as possible.
     */
    private fun triggerInitialTokenFetch() {
        applicationScope.launch {
            try {
                val token = Firebase.messaging.token.await()
                Log.d("MainApplication", "Initial token fetch successful. Token starts with: ${token.take(10)}")
                // No further action is needed here. The service handles the registration logic.
            } catch (e: Exception) {
                Log.e("MainApplication", "Initial FCM token fetch failed", e)
            }
        }
    }

    /**
     * Provides a custom WorkManager configuration.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .build()
}
