// AppContainer.kt
package com.elfinsaddle.util

import com.elfinsaddle.data.local.AppDatabase
import com.elfinsaddle.data.remote.ApiClient
import com.elfinsaddle.data.repository.DeviceRepository

/**
 * Interface for the application's dependency container.
 */
interface AppContainer {
    val apiClient: ApiClient
    val appDatabase: AppDatabase
    val systemInfoManager: SystemInfoManager
    val fileSystemManager: FileSystemManager
    val deviceRepository: DeviceRepository
}
