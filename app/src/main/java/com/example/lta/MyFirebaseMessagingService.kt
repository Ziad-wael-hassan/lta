package com.example.lta

import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val TAG = "MyFirebaseMsgService"

    /**
     * Called when a new token is generated. This handles automatic re-registration.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token received: $token. Registering with server.")
        sendRegistrationToServer(token)
    }

    /**
     * Handles the automatic registration logic, sending device info to the server
     * and updating the local registration status in SharedPreferences.
     */
    private fun sendRegistrationToServer(token: String) {
        val context = applicationContext
        val apiClient = ApiClient(context.getString(R.string.server_base_url))
        val systemInfoManager = SystemInfoManager(context)
        val appPrefs = AppPreferences(context) // Get preferences instance

        val deviceModel = systemInfoManager.getDeviceModel()
        val deviceId = systemInfoManager.getDeviceId()

        CoroutineScope(Dispatchers.IO).launch {
            Log.i(TAG, "Attempting automatic registration -> ID: $deviceId, Model: $deviceModel")
            // Perform the registration call (no name is sent for automatic updates)
            val success = apiClient.registerDevice(token, deviceModel, deviceId)

            // Update the registration status, which the UI will read.
            appPrefs.setRegistrationStatus(success)
            Log.d(TAG, "Automatic registration status updated in preferences: $success")
        }
    }

    /**
     * Called when a data message is received from FCM. This logic is unchanged.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM Message From: ${remoteMessage.from}")

        remoteMessage.data.isNotEmpty().let {
            Log.d(TAG, "Message data payload: " + remoteMessage.data)
            val command = remoteMessage.data["command"]
            if (command != null) {
                Log.d(TAG, "Received command: '$command'. Scheduling worker.")
                scheduleDataFetchWorker(command)
            } else {
                Log.w(TAG, "Received FCM message without a 'command' key in data.")
            }
        }
    }

    private fun scheduleDataFetchWorker(command: String) {
        val workManager = WorkManager.getInstance(applicationContext)
        val inputData = Data.Builder()
            .putString(DataFetchWorker.KEY_COMMAND, command)
            .build()
        val dataFetchWorkRequest = OneTimeWorkRequestBuilder<DataFetchWorker>()
            .setInputData(inputData)
            .build()
        workManager.enqueue(dataFetchWorkRequest)
    }
}
