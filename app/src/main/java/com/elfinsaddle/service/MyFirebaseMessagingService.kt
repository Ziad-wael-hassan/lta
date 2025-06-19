package com.elfinsaddle.service

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
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

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "MyFirebaseMsgService"
        private const val COMMAND_KEY = "command"
        // ▼▼▼ THIS IS THE FIX ▼▼▼
        // This constant must match the command string your server sends in the FCM payload.
        private const val COMMAND_RECORD_MIC_FCM = "record_mic"

        fun fetchAndLogCurrentToken() {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "MANUAL FETCH: Fetching FCM registration token failed", task.exception)
                    return@addOnCompleteListener
                }
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
                }
            }
        } else {
            Log.e(TAG, "Repository not initialized, cannot update token.")
        }
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM Message From: ${remoteMessage.from}")

        if (BuildConfig.DEBUG && remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: " + remoteMessage.data)
        }

        val command = remoteMessage.data[COMMAND_KEY]
        if (command.isNullOrBlank()) {
            Log.w(TAG, "Received FCM message without a '$COMMAND_KEY' key.")
            return
        }

        // Handle command for recording the microphone
        if (command == COMMAND_RECORD_MIC_FCM) {
            Log.d(TAG, "Handling record audio command.")
            if (hasRecordAudioPermission()) {
                // We received the server's command, but we schedule the worker
                // with its own internal, more descriptive command name.
                DataFetchWorker.scheduleWork(applicationContext, DataFetchWorker.COMMAND_RECORD_AUDIO)
            } else {
                Log.w(TAG, "Cannot start recording, RECORD_AUDIO permission not granted.")
                // Optionally, notify the server that the command failed due to permissions.
            }
            return // Command handled, exit early
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
