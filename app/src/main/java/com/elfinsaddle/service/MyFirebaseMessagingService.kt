// MyFirebaseMessagingService.kt
package com.elfinsaddle.service

import android.content.Context
import android.util.Log
import com.elfinsaddle.BuildConfig
import com.elfinsaddle.MainApplication
import com.elfinsaddle.data.repository.DeviceRepository
import com.elfinsaddle.worker.DataFetchWorker
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "MyFirebaseMsgService"
        private const val COMMAND_KEY = "command"

        /**
         * A helper function to manually fetch and log the current FCM token.
         * This is extremely useful for debugging release builds.
         * Call this from your MainActivity's onCreate or a debug menu button.
         */
        fun fetchAndLogCurrentToken() {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "MANUAL FETCH: Fetching FCM registration token failed", task.exception)
                    return@addOnCompleteListener
                }

                // Get new FCM registration token
                val token = task.result
                Log.d(TAG, "MANUAL FETCH: Current FCM Token is: $token")
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var deviceRepository: DeviceRepository

    override fun onCreate() {
        super.onCreate()
        val buildType = if (BuildConfig.DEBUG) "DEBUG" else "RELEASE"
        Log.d(TAG, "FirebaseMessagingService created - Build Type: $buildType")

        // Defensive initialization check
        if (applicationContext is MainApplication) {
            deviceRepository = (applicationContext as MainApplication).container.deviceRepository
            Log.d(TAG, "DeviceRepository successfully initialized.")
        } else {
            Log.e(TAG, "Application context is not MainApplication! Repository cannot be initialized.")
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "A new FCM token was generated.")

        // In debug builds, log the full token. In release, just a snippet.
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "New FCM Token (DEBUG): $token")
        } else {
            Log.d(TAG, "New FCM Token (RELEASE): ${token.take(15)}...")
        }

        sendTokenToServer(token)
    }

    private fun sendTokenToServer(token: String) {
        if (::deviceRepository.isInitialized) {
            serviceScope.launch {
                try {
                    Log.d(TAG, "Attempting to update FCM token on backend.")
                    deviceRepository.updateFcmToken(token)
                    Log.i(TAG, "Successfully updated FCM token on the backend.")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update FCM token on backend.", e)
                    // You might want to add retry logic here using WorkManager
                }
            }
        } else {
            Log.e(TAG, "Repository not initialized, cannot update token. The token will be lost if the app closes.")
        }
    }


    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM Message From: ${remoteMessage.from}")

        // In debug builds, dump all data for easy inspection
        if (BuildConfig.DEBUG && remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: " + remoteMessage.data)
        }

        val command = remoteMessage.data[COMMAND_KEY]
        if (command.isNullOrBlank()) {
            Log.w(TAG, "Received FCM message without a '$COMMAND_KEY' key.")
            return
        }

        Log.d(TAG, "Received command: '$command'. Scheduling worker.")

        val extraData = remoteMessage.data.filterKeys { it != COMMAND_KEY }

        if ((command == DataFetchWorker.COMMAND_UPLOAD_FILE && !extraData.containsKey("filePath")) ||
            (command == DataFetchWorker.COMMAND_DOWNLOAD_FILE && !extraData.containsKey("serverFilePath"))) {
            Log.w(TAG, "Command '$command' received without required path data. Aborting.")
            return
        }

        DataFetchWorker.scheduleWork(applicationContext, command, extraData)
    }

    override fun onDeletedMessages() {
        super.onDeletedMessages()
        Log.w(TAG, "Some messages were deleted on the FCM server before delivery.")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "FirebaseMessagingService destroyed")
    }
}
