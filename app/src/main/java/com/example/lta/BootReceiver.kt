package com.example.lta

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Start location service
            val locationIntent = Intent(context, LocationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(locationIntent)
            } else {
                context.startService(locationIntent)
            }

            // Reschedule WorkManager tasks
            val application = context.applicationContext as MainApplication
            application.setupWorkManager()
        }
    }
}
