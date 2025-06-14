// AppContainer.kt
package com.example.lta.util

import com.example.lta.data.local.AppDatabase
import com.example.lta.data.remote.ApiClient
import com.example.lta.data.repository.DeviceRepository

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
