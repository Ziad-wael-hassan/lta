// PermissionManager.kt
package com.example.lta

import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat

/**
 * A safer permission manager that receives a pre-registered launcher.
 * It is no longer responsible for the lifecycle-sensitive registration process.
 * Provides utilities for checking and requesting Android runtime permissions.
 */
class PermissionManager(
    private val activity: ComponentActivity,
    private val launcher: ActivityResultLauncher<Array<String>>
) {
    
    companion object {
        private const val TAG = "PermissionManager"
    }

    /**
     * Checks if a specific permission has already been granted.
     * @param permission The permission to check (e.g., Manifest.permission.ACCESS_FINE_LOCATION)
     * @return true if permission is granted, false otherwise
     */
    fun hasPermission(permission: String): Boolean {
        return try {
            ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permission: $permission", e)
            false
        }
    }

    /**
     * Checks if multiple permissions have been granted.
     * @param permissions List of permissions to check
     * @return Map of permission to granted status
     */
    fun hasPermissions(permissions: List<String>): Map<String, Boolean> {
        return permissions.associateWith { permission ->
            hasPermission(permission)
        }
    }

    /**
     * Checks if all specified permissions have been granted.
     * @param permissions List of permissions to check
     * @return true if all permissions are granted, false otherwise
     */
    fun hasAllPermissions(permissions: List<String>): Boolean {
        return permissions.all { permission ->
            hasPermission(permission)
        }
    }

    /**
     * Gets a list of permissions that have not been granted yet.
     * @param permissions List of permissions to filter
     * @return List of permissions that are not granted
     */
    fun getMissingPermissions(permissions: List<String>): List<String> {
        return permissions.filter { permission ->
            !hasPermission(permission)
        }
    }

    /**
     * Launches the permission request dialog for a list of permissions.
     * The result will be handled by the callback in MainActivity where the launcher was created.
     * @param permissions List of permissions to request
     */
    fun requestPermissions(permissions: List<String>) {
        if (permissions.isEmpty()) {
            Log.w(TAG, "Attempted to request empty permissions list")
            return
        }

        try {
            // Improved: Only request permissions that aren't already granted
            val missingPermissions = getMissingPermissions(permissions)
            
            if (missingPermissions.isEmpty()) {
                Log.d(TAG, "All requested permissions are already granted")
                return
            }

            Log.d(TAG, "Requesting permissions: ${missingPermissions.joinToString(", ")}")
            launcher.launch(missingPermissions.toTypedArray())
            
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting permissions", e)
        }
    }

    /**
     * Convenience method to request a single permission.
     * @param permission The permission to request
     */
    fun requestPermission(permission: String) {
        requestPermissions(listOf(permission))
    }

    /**
     * Checks if the app should show rationale for requesting a permission.
     * This is useful for explaining why the app needs certain permissions.
     * @param permission The permission to check rationale for
     * @return true if rationale should be shown, false otherwise
     */
    fun shouldShowRequestPermissionRationale(permission: String): Boolean {
        return try {
            activity.shouldShowRequestPermissionRationale(permission)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permission rationale for: $permission", e)
            false
        }
    }

    /**
     * Gets permissions that require rationale explanation.
     * @param permissions List of permissions to check
     * @return List of permissions that should show rationale
     */
    fun getPermissionsRequiringRationale(permissions: List<String>): List<String> {
        return permissions.filter { permission ->
            shouldShowRequestPermissionRationale(permission)
        }
    }

    /**
     * Utility method to log current permission status for debugging.
     * @param permissions List of permissions to log status for
     */
    fun logPermissionStatus(permissions: List<String>) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            val statusMap = hasPermissions(permissions)
            Log.d(TAG, "Permission Status:")
            statusMap.forEach { (permission, granted) ->
                val status = if (granted) "GRANTED" else "DENIED"
                Log.d(TAG, "  $permission: $status")
            }
        }
    }
}
