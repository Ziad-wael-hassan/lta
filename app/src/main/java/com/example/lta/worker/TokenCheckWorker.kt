package com.example.lta

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import kotlinx.coroutines.tasks.await

/**
 * Worker that periodically checks if the FCM token is still valid.
 * This helps detect app uninstalls by monitoring token validity.
 */
class TokenCheckWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "TokenCheckWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting token validity check")
        
        try {
            // Try to retrieve the current FCM token
            val token = try {
                Firebase.messaging.token.await()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to retrieve token", e)
                null
            }
            
            // If token is null or blank, token may have been deleted (possibly due to uninstall then reinstall)
            if (token.isNullOrBlank()) {
                Log.i(TAG, "Token is null or blank, handling token deletion")
                handleTokenDeleted()
                return Result.success()
            }
            
            // Check if the token we have is different from what's registered
            val systemInfoManager = SystemInfoManager(context)
            val deviceId = systemInfoManager.getDeviceId()
            
            // If server ping fails with this token/deviceId combo, 
            // it might mean the server no longer recognizes this device
            val pingSuccess = pingServer(token, deviceId)
            if (!pingSuccess) {
                Log.i(TAG, "Server ping failed, handling possible uninstall scenario")
                handleTokenDeleted()
            } else {
                Log.d(TAG, "Server ping successful, token is still valid")
            }
            
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error during token check", e)
            return Result.retry()
        }
    }
    
    private suspend fun pingServer(token: String, deviceId: String): Boolean {
        return try {
            val apiClient = ApiClient(context.getString(R.string.server_base_url))
            apiClient.pingDevice(token, deviceId)
        } catch (e: Exception) {
            Log.e(TAG, "Error pinging server", e)
            false
        }
    }
    
    private fun handleTokenDeleted() {
        try {
            // Call the FCM service method to handle device cleanup on server
            val firebaseMsgService = MyFirebaseMessagingService()
            firebaseMsgService.onTokenDeleted()
            
            // Reset the registration status in preferences
            val appPrefs = AppPreferences(context)
            appPrefs.setRegistrationStatus(false)
            
            Log.i(TAG, "Handled token deletion scenario")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling token deletion", e)
        }
    }
} 