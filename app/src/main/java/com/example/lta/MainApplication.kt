package com.example.lta

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class MainApplication : Application(), Configuration.Provider {

    // A coroutine scope for tasks that should live as long as the application.
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val LOCATION_CHANNEL_ID = "location_service_channel"
        const val NOTIFICATION_CHANNEL_ID = "notifications_channel"
        private const val TOKEN_CHECK_INTERVAL_HOURS = 24L
        const val TOKEN_MONITOR_WORK_NAME = "token_monitor_work"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        // Trigger the initial token fetch and auto-registration
        triggerInitialTokenFetch()
        
        // Initialize token monitoring
        initializeTokenMonitoring()
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
                
                // Check if we need to auto-register on first launch
                val appPrefs = AppPreferences(applicationContext)
                if (!appPrefs.hasInitializedApp()) {
                    Log.i("MainApplication", "First launch detected, initiating auto-registration")
                    
                    // We'll let the MainActivity handle the registration logic
                    // This ensures we have a proper UI context for any potential errors
                    appPrefs.setInitializedApp(true)
                }
                
            } catch (e: Exception) {
                Log.e("MainApplication", "Initial FCM token fetch failed", e)
            }
        }
    }
    
    /**
     * Initializes token monitoring system to detect token changes and app uninstalls.
     */
    private fun initializeTokenMonitoring() {
        // Schedule token check worker
        val tokenCheckWork = PeriodicWorkRequestBuilder<TokenCheckWorker>(
            TOKEN_CHECK_INTERVAL_HOURS, TimeUnit.HOURS
        ).build()
        
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            TOKEN_MONITOR_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            tokenCheckWork
        )
        
        // Remove the service startup to avoid ForegroundServiceStartNotAllowedException
        // The WorkManager will handle the token monitoring reliably
        // No need to start a foreground service from Application context
        
        Log.i("MainApplication", "Token monitoring initialized with WorkManager")
    }

    /**
     * Provides a custom WorkManager configuration.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .build()
}
