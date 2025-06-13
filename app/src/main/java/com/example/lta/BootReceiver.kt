package com.example.lta

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * BroadcastReceiver that listens for system boot and app reinstall events.
 * Used to automatically start essential services when the device boots or
 * the app is reinstalled.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
        // HTC's quick boot intent action (commonly used by various OEMs)
        private const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received intent: ${intent.action}")

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, 
            ACTION_QUICKBOOT_POWERON,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.i(TAG, "Device boot or app reinstall detected, starting services")
                startServices(context)
            }
        }
    }

    private fun startServices(context: Context) {
        try {
            Log.d(TAG, "Starting token monitor service")
            
            // Start token monitor service
            val serviceIntent = Intent(context, TokenMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            
            // Also check if we need to re-register the device
            val appPrefs = AppPreferences(context)
            if (!appPrefs.getRegistrationStatus()) {
                Log.i(TAG, "Device not registered, triggering auto-registration")
                triggerAppInitialization(context)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting services after boot/reinstall", e)
        }
    }
    
    /**
     * Triggers app initialization flow as if it was first launch.
     * Used after package replacement to ensure device is registered.
     */
    private fun triggerAppInitialization(context: Context) {
        try {
            val appPrefs = AppPreferences(context)
            appPrefs.setInitializedApp(false)
            
            // Launch main activity to trigger auto-registration
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(launchIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering app initialization", e)
        }
    }
} 