// TokenCheckWorker.kt
package com.elfinsaddle.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.elfinsaddle.MainApplication
import com.elfinsaddle.data.remote.ApiClient
import com.elfinsaddle.data.remote.NetworkResult
import com.elfinsaddle.data.repository.DeviceRepository
import com.elfinsaddle.util.SystemInfoManager
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import kotlinx.coroutines.tasks.await

class TokenCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "TokenCheckWorker"
    }

    private val container = (applicationContext as MainApplication).container
    private val deviceRepository: DeviceRepository = container.deviceRepository
    private val apiClient: ApiClient = container.apiClient
    private val systemInfoManager: SystemInfoManager = container.systemInfoManager

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting token validity check")

        try {
            // Check registration status first to avoid unnecessary work if the user has manually unregistered.
            if (!deviceRepository.getRegistrationStatus()) {
                Log.i(TAG, "Token check skipped: Device is not marked as registered.")
                return Result.success()
            }

            val token = try {
                Firebase.messaging.token.await()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to retrieve FCM token", e)
                null
            }

            if (token.isNullOrBlank()) {
                Log.w(TAG, "FCM token is null or blank. Cannot perform check. No local action will be taken.")
                // We return success because retrying won't help if the token is permanently gone.
                // The user would need to re-register the app to generate a new one.
                return Result.success()
            }

            val deviceId = systemInfoManager.getDeviceId()
            val pingResult = apiClient.pingDevice(token, deviceId)

            // MODIFIED: Check if the result is NOT a success.
            if (pingResult !is NetworkResult.Success) {
                Log.w(TAG, "Server ping failed. The server may no longer recognize this token/device. No local action will be taken.")
                // REMOVED: The destructive call to deviceRepository.handleTokenPotentiallyDeleted() is gone.
            } else {
                Log.i(TAG, "Server ping successful, token and registration are valid.")
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error during token check worker", e)
            return Result.retry()
        }
    }
}
