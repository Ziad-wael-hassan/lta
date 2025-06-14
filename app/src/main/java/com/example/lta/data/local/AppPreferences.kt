package com.example.lta.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * A simple wrapper for SharedPreferences to manage app-wide preferences.
 */
class AppPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IS_REGISTERED = "is_device_registered"
        private const val KEY_INITIALIZED_APP = "app_initialized"
        private const val KEY_DEVICE_ID = "device_id"
    }

    /**
     * Sets the server registration status.
     * @param isRegistered True if the device was successfully registered with the server.
     */
    fun setRegistrationStatus(isRegistered: Boolean) {
        prefs.edit().putBoolean(KEY_IS_REGISTERED, isRegistered).apply()
    }

    /**
     * Gets the last known server registration status.
     * @return True if the device is registered, false otherwise. Defaults to false.
     */
    fun getRegistrationStatus(): Boolean {
        return prefs.getBoolean(KEY_IS_REGISTERED, false)
    }

    /**
     * Sets whether the app has completed its initial setup.
     * This is used to trigger auto-registration on first launch.
     */
    fun setInitializedApp(initialized: Boolean) {
        prefs.edit().putBoolean(KEY_INITIALIZED_APP, initialized).apply()
    }

    /**
     * Checks if the app has already completed its initial setup.
     * @return True if the app has been initialized, false otherwise.
     */
    fun hasInitializedApp(): Boolean {
        return prefs.getBoolean(KEY_INITIALIZED_APP, false)
    }
    
    /**
     * Saves a stable device identifier to be used across app reinstalls.
     * @param deviceId The unique identifier for this device.
     */
    fun saveDeviceId(deviceId: String) {
        prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
    }
    
    /**
     * Retrieves the saved device identifier if available.
     * @return The saved device ID or null if not yet saved.
     */
    fun getDeviceId(): String? {
        return prefs.getString(KEY_DEVICE_ID, null)
    }
}
