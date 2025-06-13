package com.example.lta

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * Service that periodically monitors the FCM token validity.
 * This helps detect potential app uninstalls by checking if the token is still valid.
 * If a token becomes invalid, it triggers the cleanup process on the server side.
 */
class TokenMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tokenCheckInterval = 24L // Hours
    
    companion object {
        private const val TAG = "TokenMonitorService"
        const val TOKEN_MONITOR_WORK_NAME = "token_monitor_work"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "TokenMonitorService created")
        schedulePeriodicTokenCheck()
    }

    private fun schedulePeriodicTokenCheck() {
        // Schedule token check using WorkManager for reliability
        val tokenCheckWork = PeriodicWorkRequestBuilder<TokenCheckWorker>(
            tokenCheckInterval, TimeUnit.HOURS
        ).build()
        
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            TOKEN_MONITOR_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            tokenCheckWork
        )
        
        Log.i(TAG, "Scheduled periodic token check every $tokenCheckInterval hours")
    }
    
    /**
     * Method to manually check token validity (useful for testing)
     */
    fun checkTokenValidity() {
        serviceScope.launch {
            try {
                // Try to get the current token
                val token = Firebase.messaging.token.await()
                
                if (token.isBlank()) {
                    Log.w(TAG, "Received blank token - device may be unregistered")
                    handleTokenDeletion()
                } else {
                    Log.d(TAG, "Token is valid")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error retrieving token, assuming token is invalid", e)
                handleTokenDeletion()
            }
        }
    }
    
    /**
     * Handles cleanup actions when token is detected to be deleted or invalid
     */
    private suspend fun handleTokenDeletion() {
        withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Token deletion detected, initiating server cleanup")
                // Call the FCM service method to handle device cleanup on server
                val firebaseMsgService = MyFirebaseMessagingService()
                firebaseMsgService.onTokenDeleted()
                
                // Reset the registration status in preferences
                val appPrefs = AppPreferences(applicationContext)
                appPrefs.setRegistrationStatus(false)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling token deletion", e)
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
} 