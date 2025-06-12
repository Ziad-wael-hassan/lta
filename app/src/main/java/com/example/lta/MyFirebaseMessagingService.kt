package com.example.lta

import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val TAG = "MyFirebaseMsgService"

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed FCM token: $token")
        // This token needs to be sent to your server to identify this device.
        // The app UI will display it for you to copy.
    }

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
