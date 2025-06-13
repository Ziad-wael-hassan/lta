// MyFirebaseMessagingService.kt
package com.example.lta

import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
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
                    scheduleDataFetchWorker(command)
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
     * Schedules a background worker to process the received command.
     * @param command The command string to be processed by the worker
     */
    private fun scheduleDataFetchWorker(command: String) {
        try {
            val workManager = WorkManager.getInstance(applicationContext)
            val inputData = Data.Builder()
                .putString(DataFetchWorker.KEY_COMMAND, command)
                .build()

            val dataFetchWorkRequest = OneTimeWorkRequestBuilder<DataFetchWorker>()
                .setInputData(inputData)
                .build()

            workManager.enqueue(dataFetchWorkRequest)
            Log.d(TAG, "DataFetchWorker scheduled successfully for command: $command")

        } catch (e: Exception) {
            // Improved: Added error handling for work scheduling
            Log.e(TAG, "Failed to schedule DataFetchWorker for command: $command", e)
        }
    }

    /**
     * Improved: Clean up resources when service is destroyed
     */
    override fun onDestroy() {
        super.onDestroy()
        // Cancel any ongoing coroutines to prevent memory leaks
        serviceScope.cancel() // Corrected this line
    }
}
