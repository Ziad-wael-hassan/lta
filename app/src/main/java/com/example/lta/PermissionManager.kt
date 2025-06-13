package com.example.lta

import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat

/**
 * A safer permission manager that receives a pre-registered launcher.
 * It is no longer responsible for the lifecycle-sensitive registration process.
 */
class PermissionManager(
    private val activity: ComponentActivity,
    private val launcher: ActivityResultLauncher<Array<String>>
) {
    /**
     * Checks if a specific permission has already been granted.
     */
    fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Launches the permission request dialog for a list of permissions.
     * The result will be handled by the callback in MainActivity where the launcher was created.
     */
    fun requestPermissions(permissions: List<String>) {
        launcher.launch(permissions.toTypedArray())
    }
}
