package com.example.lta

import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class PermissionManager(private val activity: ComponentActivity) {

    private lateinit var requestPermissionsLauncher: ActivityResultLauncher<Array<String>>

    fun register(onAllGranted: () -> Unit, onSomeDenied: () -> Unit) {
        requestPermissionsLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            // If the map of results does not contain any 'false' values, all permissions were granted.
            if (!permissions.containsValue(false)) {
                onAllGranted()
            } else {
                onSomeDenied()
            }
        }
    }

    fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun requestPermissions(permissions: List<String>) {
        requestPermissionsLauncher.launch(permissions.toTypedArray())
    }
}
