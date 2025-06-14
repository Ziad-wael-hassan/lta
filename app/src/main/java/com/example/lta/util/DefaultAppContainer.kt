// DefaultAppContainer.kt
package com.example.lta.util

import android.content.Context
import com.example.lta.data.local.AppDatabase
import com.example.lta.data.local.AppPreferences
import com.example.lta.data.remote.ApiClient
import com.example.lta.data.repository.DeviceRepository

/**
 * Default implementation of AppContainer that provides app-wide dependencies.
 */
class DefaultAppContainer(private val context: Context) : AppContainer {

    // NOTE: Replace "http://YOUR_SERVER_IP:PORT" with your actual server address
    private const val BASE_URL = "https://retail-jammie-soska-846f805d.koyeb.app"

    override val apiClient: ApiClient by lazy {
        ApiClient(BASE_URL)
    }

    override val appDatabase: AppDatabase by lazy {
        AppDatabase.getDatabase(context)
    }

    private val appPreferences: AppPreferences by lazy {
        AppPreferences(context)
    }

    override val systemInfoManager: SystemInfoManager by lazy {
        SystemInfoManager(context)
    }

    override val fileSystemManager: FileSystemManager by lazy {
        FileSystemManager(context)
    }

    private val deviceContentResolver: DeviceContentResolver by lazy {
        DeviceContentResolver(context)
    }

    override val deviceRepository: DeviceRepository by lazy {
        DeviceRepository(
            apiClient = apiClient,
            appPreferences = appPreferences,
            systemInfoManager = systemInfoManager,
            appDb = appDatabase,
            deviceContentResolver = deviceContentResolver,
            fileSystemManager = fileSystemManager,
            appContext = context
        )
    }
}
