package com.elfinsaddle.ui.main

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
import com.elfinsaddle.MainApplication
import com.elfinsaddle.data.remote.NetworkResult
import com.elfinsaddle.data.repository.DeviceRepository
import com.elfinsaddle.util.SystemInfoManager
import com.elfinsaddle.worker.DataFetchWorker
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

        // Corrected First-Launch Logic
        if (deviceRepository.isFirstLaunch()) {
            viewModelScope.launch {
                Log.i("MainViewModel", "First launch detected, attempting auto-registration.")
                // Attempt to register first
                val result = deviceRepository.registerDevice(initialDeviceName)

                // Only mark as initialized if registration was successful
                if (result is NetworkResult.Success) {
                    Log.i("MainViewModel", "Auto-registration successful, marking app as initialized.")
                    deviceRepository.markAppInitialized()
                } else {
                    Log.e("MainViewModel", "Auto-registration failed.")
                }
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

            val message = when (result) {
                is NetworkResult.Success -> "Device registered successfully!"
                is NetworkResult.ApiError -> "Registration failed: Server error ${result.code}. Please try again later."
                is NetworkResult.NetworkError -> "Registration failed: Please check your internet connection."
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
