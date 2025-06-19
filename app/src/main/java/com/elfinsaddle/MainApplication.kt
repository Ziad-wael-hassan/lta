// MainApplication.kt
package com.elfinsaddle

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.elfinsaddle.receiver.BootReceiver
import com.elfinsaddle.util.AppContainer
import com.elfinsaddle.util.DefaultAppContainer
import com.elfinsaddle.worker.TokenCheckWorker
import java.util.concurrent.TimeUnit

class MainApplication : Application(), Configuration.Provider {

    lateinit var container: AppContainer

    companion object {
        const val LOCATION_CHANNEL_ID = "location_service_channel"
        const val NOTIFICATION_CHANNEL_ID = "notifications_channel"
        const val AUDIO_RECORDING_CHANNEL_ID = "audio_recording_channel"
    }

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
        Log.d("MainApplication", "App container initialized.")

        createNotificationChannels()
        initializeTokenMonitoring()
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

            // NEW CHANNEL FOR SILENT SERVICE
            val audioChannel = NotificationChannel(
                AUDIO_RECORDING_CHANNEL_ID,
                "Background Tasks", // User-visible name in App Settings
                NotificationManager.IMPORTANCE_LOW // KEY CHANGE: Low importance makes it silent and non-intrusive
            ).apply { description = "For silent background service notifications." }

            notificationManager.createNotificationChannels(listOf(locationChannel, appChannel, audioChannel))
        }
    }

    private fun initializeTokenMonitoring() {
        val tokenCheckWork = PeriodicWorkRequestBuilder<TokenCheckWorker>(
            BootReceiver.TOKEN_CHECK_INTERVAL_HOURS, TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            BootReceiver.TOKEN_MONITOR_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            tokenCheckWork
        )
        Log.i("MainApplication", "Token monitoring worker enqueued.")
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.INFO)
            .build()
}
