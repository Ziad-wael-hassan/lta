// DefaultAppContainer.kt
package com.elfinsaddle.util

import android.content.Context
import com.elfinsaddle.R // <-- IMPORTANT: Import R to access resources
import com.elfinsaddle.data.local.AppDatabase
import com.elfinsaddle.data.local.AppPreferences
import com.elfinsaddle.data.remote.ApiClient
import com.elfinsaddle.data.repository.DeviceRepository

/**
 * Default implementation of AppContainer that provides app-wide dependencies.
 */
class DefaultAppContainer(private val context: Context) : AppContainer {

    // REMOVED: The companion object with the hardcoded BASE_URL is no longer needed.

    override val apiClient: ApiClient by lazy {
        // CHANGED: We now fetch the URL from strings.xml at runtime using the context.
        // This is the proper Android way to handle configuration strings.
        ApiClient(context.getString(R.string.server_base_url))
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
