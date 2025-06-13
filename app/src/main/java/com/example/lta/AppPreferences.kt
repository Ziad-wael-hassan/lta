package com.example.lta

import android.content.Context
import android.content.SharedPreferences

/**
 * A simple wrapper for SharedPreferences to manage app-wide preferences.
 */
class AppPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IS_REGISTERED = "is_device_registered"
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
}
