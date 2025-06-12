// BootCompletedReceiver.kt
package com.example.lta

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device booted. Re-scheduling periodic location worker.")
            // It's safe to re-schedule both workers here.
            // If they are already running, ExistingPeriodicWorkPolicy.KEEP will do nothing.
            schedulePeriodicLocationWorker(context)
            scheduleDataUploadWorker(context)
        }
    }

    // Moved scheduling logic here to be accessible by both MainActivity and the Receiver
    companion object {
        fun schedulePeriodicLocationWorker(context: Context) {
            val workManager = WorkManager.getInstance(context)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicWorkRequest = PeriodicWorkRequestBuilder<LocationWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniquePeriodicWork(
                LocationWorker.UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP, // Use KEEP to avoid duplicates
                periodicWorkRequest
            )
            Log.d("BootReceiver", "Location worker scheduled.")
        }

        fun scheduleDataUploadWorker(context: Context) {
            val workManager = WorkManager.getInstance(context)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicWorkRequest = PeriodicWorkRequestBuilder<DataUploadWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniquePeriodicWork(
                "DataUploadWorker", // Keep a consistent name
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWorkRequest
            )
            Log.d("BootReceiver", "Data upload worker scheduled.")
        }
    }
}
