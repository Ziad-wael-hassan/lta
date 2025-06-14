package com.example.lta

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.lta.receiver.BootReceiver
import com.example.lta.util.AppContainer
import com.example.lta.util.DefaultAppContainer
import com.example.lta.worker.TokenCheckWorker
import java.util.concurrent.TimeUnit

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

            notificationManager.createNotificationChannels(listOf(locationChannel, appChannel))
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
