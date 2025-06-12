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

        // Trigger the device registration process on app startup.
        registerDeviceOnFirstLoad()
    }

    /**
     * Creates notification channels required by the app for Android O and above.
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel(
                LOCATION_CHANNEL_ID,
                "Location Updates",
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

    /**
     * Fetches the FCM token and device model, then sends them to the backend server
     * for registration. This runs in a background coroutine to avoid blocking the main thread.
     */
    private fun registerDeviceOnFirstLoad() {
        applicationScope.launch {
            try {
                // 1. Initialize the necessary managers
                val apiClient = ApiClient(getString(R.string.server_base_url))
                val systemInfoManager = SystemInfoManager(applicationContext)

                // 2. Get the FCM token and device model
                val fcmToken = Firebase.messaging.token.await()
                val deviceModel = systemInfoManager.getDeviceModel()

                Log.d("MainApplication", "Preparing to register device: Model=$deviceModel, Token=$fcmToken")

                // 3. Send the registration data to the server
                val success = apiClient.registerDevice(fcmToken, deviceModel)

                if (success) {
                    Log.i("MainApplication", "Device registration request sent successfully.")
                } else {
                    Log.e("MainApplication", "Device registration request failed. Will retry on next app start.")
                }
            } catch (e: Exception) {
                Log.e("MainApplication", "Could not get FCM token for registration", e)
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()
}
