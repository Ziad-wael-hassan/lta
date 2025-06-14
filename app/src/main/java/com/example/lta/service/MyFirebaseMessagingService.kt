package com.example.lta.service

import android.util.Log
import com.example.lta.BuildConfig
import com.example.lta.MainApplication
import com.example.lta.data.repository.DeviceRepository
import com.example.lta.worker.DataFetchWorker
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
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var deviceRepository: DeviceRepository

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FirebaseMessagingService created - Build Type: ${if (BuildConfig.DEBUG) "DEBUG" else "RELEASE"}")
        if (applicationContext is MainApplication) {
            deviceRepository = (applicationContext as MainApplication).container.deviceRepository
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token received: ${token.take(15)}...")
        if (::deviceRepository.isInitialized) {
            serviceScope.launch {
                deviceRepository.updateFcmToken(token)
            }
        } else {
            Log.e(TAG, "Repository not initialized, cannot update token.")
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM Message From: ${remoteMessage.from}")

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
