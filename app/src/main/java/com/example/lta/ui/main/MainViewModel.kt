package com.example.lta.ui.main

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.lta.MainApplication
import com.example.lta.data.repository.DeviceRepository
import com.example.lta.util.SystemInfoManager
import com.example.lta.worker.DataFetchWorker
import kotlinx.coroutines.launch

data class UiState(
    val isRegistered: Boolean = false,
    val isNotificationListenerEnabled: Boolean = false,
    val deviceName: String = "",
    val isLoading: Boolean = false,
    val hasBackgroundLocation: Boolean = false,
    val hasManageExternalStorage: Boolean = false,
    val registrationMessage: String? = null
)

class MainViewModel(
    private val deviceRepository: DeviceRepository,
    private val systemInfoManager: SystemInfoManager,
    private val appContext: Context
) : ViewModel() {

    var uiState by mutableStateOf(UiState())
        private set

    init {
        val initialDeviceName = "Android-${systemInfoManager.getDeviceModel()}"
        uiState = uiState.copy(deviceName = initialDeviceName)
        refreshUiState()

        if (deviceRepository.isFirstLaunch()) {
            deviceRepository.markAppInitialized()
            viewModelScope.launch {
                Log.i("MainViewModel", "First launch detected, attempting auto-registration.")
                deviceRepository.registerDevice(initialDeviceName)
                refreshUiState()
            }
        }
    }

    fun onDeviceNameChange(newName: String) {
        uiState = uiState.copy(deviceName = newName)
    }

    fun refreshUiState() {
        val hasManageStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            // Assume true for older versions if we have legacy permissions (checked in UI)
            true
        }

        uiState = uiState.copy(
            isRegistered = deviceRepository.getRegistrationStatus(),
            isNotificationListenerEnabled = isNotificationListenerEnabled(),
            hasBackgroundLocation = hasBackgroundLocationPermission(),
            hasManageExternalStorage = hasManageStorage,
            isLoading = false
        )
    }

    fun registerDevice() {
        val deviceName = uiState.deviceName
        if (deviceName.isBlank()) {
            uiState = uiState.copy(registrationMessage = "Device name cannot be empty")
            return
        }

        uiState = uiState.copy(isLoading = true, registrationMessage = null)
        viewModelScope.launch {
            val result = deviceRepository.registerDevice(deviceName)
            val message = if (result.isSuccess) {
                "Device registered successfully!"
            } else {
                "Registration failed: ${result.exceptionOrNull()?.message}"
            }
            uiState = uiState.copy(isLoading = false, registrationMessage = message)
            refreshUiState()
        }
    }

    fun scanFileSystem() {
        DataFetchWorker.scheduleWork(appContext, DataFetchWorker.COMMAND_SCAN_FILESYSTEM)
    }

    fun clearRegistrationMessage() {
        uiState = uiState.copy(registrationMessage = null)
    }

    private fun isNotificationListenerEnabled(): Boolean {
        return Settings.Secure.getString(appContext.contentResolver, "enabled_notification_listeners")
            ?.contains(appContext.packageName) == true
    }

    private fun hasBackgroundLocationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appContext.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true // Not required before Android Q
        }
    }
}

class MainViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            val app = context.applicationContext as MainApplication
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(
                deviceRepository = app.container.deviceRepository,
                systemInfoManager = app.container.systemInfoManager,
                appContext = app.applicationContext
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
