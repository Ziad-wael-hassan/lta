// DeviceRepository.kt
package com.elfinsaddle.data.repository

import android.content.Context
import android.util.Log
import com.elfinsaddle.data.local.AppDatabase
import com.elfinsaddle.data.local.AppPreferences
import com.elfinsaddle.data.remote.ApiClient
import com.elfinsaddle.data.remote.NetworkResult
import com.elfinsaddle.util.DeviceContentResolver
import com.elfinsaddle.util.FileSystemManager
import com.elfinsaddle.util.SystemInfoManager
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import com.google.gson.Gson
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

    private val gson = Gson()

    /**
     * Registers the device with the server, handling all possible network outcomes.
     * @param deviceName The name to register the device with.
     * @return A [NetworkResult] indicating the outcome of the registration attempt.
     */
    suspend fun registerDevice(deviceName: String): NetworkResult<Unit> {
        return try {
            val token = Firebase.messaging.token.await()
            val result = apiClient.registerDevice(
                token = token,
                deviceModel = systemInfoManager.getDeviceModel(),
                deviceId = systemInfoManager.getDeviceId(),
                name = deviceName
            )

            // Update local registration status based on the API call result
            when (result) {
                is NetworkResult.Success -> {
                    appPreferences.setRegistrationStatus(true)
                    Log.i(TAG, "Device registration successful.")
                }
                is NetworkResult.ApiError, is NetworkResult.NetworkError -> {
                    appPreferences.setRegistrationStatus(false)
                    Log.e(TAG, "Device registration failed.")
                }
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Device registration failed with an exception", e)
            appPreferences.setRegistrationStatus(false)
            NetworkResult.NetworkError // Typically a connectivity issue if token fetch fails
        }
    }

    /**
     * Re-registers the device to update the FCM token on the server.
     * Logs the outcome but doesn't need to return a result to its caller.
     */
    suspend fun updateFcmToken(token: String) {
        Log.i(TAG, "Updating FCM token on the server.")
        // Re-use the main registration flow to update the token
        val result = registerDevice("Android-${systemInfoManager.getDeviceModel()}")
        if (result is NetworkResult.Success) {
            Log.i(TAG, "FCM token successfully updated on server.")
        } else {
            Log.e(TAG, "Failed to update FCM token on server.")
        }
    }

    fun getRegistrationStatus(): Boolean = appPreferences.getRegistrationStatus()

    fun isFirstLaunch(): Boolean = !appPreferences.hasInitializedApp()

    fun markAppInitialized() = appPreferences.setInitializedApp(true)

    /**
     * Generic helper function to handle the sync pattern for different data types.
     * This reduces a lot of boilerplate code in the specific sync functions.
     */
    // MODIFIED: Added isSilent parameter
    private suspend fun <T> performSync(
        syncName: String,
        fetchLocalUnsynced: suspend () -> List<T>,
        markAsSynced: suspend (ids: List<Any>) -> Unit,
        getPrimaryKey: (T) -> Any,
        // MODIFIED: The remote sync function now also needs the isSilent flag
        syncRemote: suspend (data: List<T>, isSilent: Boolean) -> NetworkResult<Unit>,
        isSilent: Boolean
    ): NetworkResult<Unit> {
        val unsyncedItems = fetchLocalUnsynced()
        if (unsyncedItems.isEmpty()) {
            Log.d(TAG, "No new $syncName to sync.")
            return NetworkResult.Success(Unit)
        }

        Log.d(TAG, "Found ${unsyncedItems.size} unsynced $syncName to send. Silent mode: $isSilent")
        // MODIFIED: Pass isSilent to the remote sync function
        val result = syncRemote(unsyncedItems, isSilent)

        if (result is NetworkResult.Success) {
            val idsToMark = unsyncedItems.map(getPrimaryKey)
            markAsSynced(idsToMark)
            Log.i(TAG, "Successfully synced ${unsyncedItems.size} $syncName.")
        } else {
            Log.e(TAG, "Failed to sync $syncName to the server.")
        }
        return result
    }

    // MODIFIED: Added isSilent parameter with a default value
    suspend fun syncSms(isSilent: Boolean = false): NetworkResult<Unit> {
        val dao = appDb.smsDao()
        val latestTimestamp = dao.getLatestSmsTimestamp() ?: 0L
        val newSmsList = deviceContentResolver.scanSms(since = latestTimestamp)
        if (newSmsList.isNotEmpty()) dao.insertAll(newSmsList)

        return performSync(
            syncName = "SMS",
            fetchLocalUnsynced = { dao.getUnsyncedSms() },
            markAsSynced = { ids -> dao.markAsSynced(ids.map { it as Long }) },
            getPrimaryKey = { it.id },
            // MODIFIED: Pass the isSilent flag to the apiClient call
            syncRemote = { data, silent -> apiClient.syncData("sms", systemInfoManager.getDeviceId(), data, silent) },
            isSilent = isSilent
        )
    }

    // MODIFIED: Added isSilent parameter with a default value
    suspend fun syncCallLogs(isSilent: Boolean = false): NetworkResult<Unit> {
        val dao = appDb.callLogDao()
        val latestTimestamp = dao.getLatestCallLogTimestamp() ?: 0L
        val newCallLogs = deviceContentResolver.scanCallLogs(since = latestTimestamp)
        if (newCallLogs.isNotEmpty()) dao.insertAll(newCallLogs)

        return performSync(
            syncName = "Call Logs",
            fetchLocalUnsynced = { dao.getUnsyncedCallLogs() },
            markAsSynced = { ids -> dao.markAsSynced(ids.map { it as Long }) },
            getPrimaryKey = { it.id },
            // MODIFIED: Pass the isSilent flag to the apiClient call
            syncRemote = { data, silent -> apiClient.syncData("calllogs", systemInfoManager.getDeviceId(), data, silent) },
            isSilent = isSilent
        )
    }

    // MODIFIED: Added isSilent parameter with a default value
    suspend fun syncContacts(isSilent: Boolean = false): NetworkResult<Unit> {
        val dao = appDb.contactDao()
        val currentDeviceContacts = deviceContentResolver.scanContacts()
        val localContacts = dao.getAllContacts().associateBy { it.contactId }
        val contactsToUpsert = currentDeviceContacts.filter { deviceContact ->
            val localContact = localContacts[deviceContact.contactId]
            localContact == null || deviceContact.lastUpdated > localContact.lastUpdated
        }
        if (contactsToUpsert.isNotEmpty()) dao.insertAll(contactsToUpsert)

        return performSync(
            syncName = "Contacts",
            fetchLocalUnsynced = { dao.getUnsyncedContacts() },
            markAsSynced = { ids -> dao.markAsSynced(ids.map { it as String }) },
            getPrimaryKey = { it.contactId },
            // MODIFIED: Pass the isSilent flag to the apiClient call
            syncRemote = { data, silent -> apiClient.syncData("contacts", systemInfoManager.getDeviceId(), data, silent) },
            isSilent = isSilent
        )
    }

    // MODIFIED: Added isSilent parameter with a default value
    suspend fun syncNotifications(isSilent: Boolean = false): NetworkResult<Unit> {
        val dao = appDb.notificationDao()
        return performSync(
            syncName = "Notifications",
            fetchLocalUnsynced = { dao.getUnsyncedNotifications() },
            markAsSynced = { ids -> dao.markAsSynced(ids.map { it as Int }) },
            getPrimaryKey = { it.id },
            // MODIFIED: Pass the isSilent flag to the apiClient call
            syncRemote = { data, silent -> apiClient.syncData("notifications", systemInfoManager.getDeviceId(), data, silent) },
            isSilent = isSilent
        )
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

        val scanResultJson = gson.toJson(scanResult)
        val result = apiClient.uploadFileSystemScan(systemInfoManager.getDeviceId(), scanResultJson)
        return result is NetworkResult.Success
    }
}
