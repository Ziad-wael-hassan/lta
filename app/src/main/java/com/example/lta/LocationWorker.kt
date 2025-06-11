// LocationWorker.kt
package com.example.lta

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await

class LocationWorker(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val telegramBotApi: TelegramBotApi = TelegramBotApi(
        context.getString(R.string.telegram_bot_token),
        context.getString(R.string.telegram_chat_id)
    )

    companion object {
        const val TAG = "LocationWorker"
        const val UNIQUE_WORK_NAME = "PeriodicLocationUpload"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Worker started.")
        // Check for permissions inside the worker. If not granted, the work fails.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permission not granted. Stopping worker.")
            telegramBotApi.sendMessage("Location Worker failed: Permission not granted.")
            return Result.failure()
        }

        return try {
            Log.d(TAG, "Requesting current location...")
            // Request a fresh location. This is better than getLastKnownLocation for a worker.
            val location = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()

            if (location != null) {
                val locationMessage = "📍 Periodic Location Update:\nLat: ${location.latitude}\nLon: ${location.longitude}\nAccuracy: ${location.accuracy}m"
                Log.d(TAG, "Location found: $locationMessage")
                val success = telegramBotApi.sendMessage(locationMessage)
                if (success) {
                    Log.d(TAG, "Location sent successfully.")
                    Result.success()
                } else {
                    Log.e(TAG, "Failed to send location to Telegram. Retrying.")
                    Result.retry()
                }
            } else {
                Log.w(TAG, "Failed to get location (was null). Retrying.")
                telegramBotApi.sendMessage("Location Worker: Unable to get location.")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in LocationWorker", e)
            telegramBotApi.sendMessage("Location Worker failed with exception: ${e.message}")
            Result.failure()
        }
    }
}
