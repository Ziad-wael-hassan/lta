package com.example.lta

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Manages permission requests using the Activity Result API.
 * NOTE: This class is stateful. It is designed to handle one permission request at a time.
 * The build error associated with this class is resolved by updating the `androidx.fragment:fragment-ktx`
 * dependency to version 1.3.0+ in your app's build.gradle file.
 */
class PermissionManager(private val activity: ComponentActivity) {

    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private var onPermissionGranted: (() -> Unit)? = null
    private var onPermissionDenied: (() -> Unit)? = null

    init {
        // This registration must happen early in the Activity's lifecycle.
        requestPermissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                onPermissionGranted?.invoke()
            } else {
                onPermissionDenied?.invoke()
            }
        }
    }

    fun requestPermission(
        permission: String,
        onGranted: () -> Unit,
        onDenied: () -> Unit
    ) {
        when {
            ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED -> {
                onGranted.invoke()
            }
            else -> {
                // Set the callbacks for the next launch
                onPermissionGranted = onGranted
                onPermissionDenied = onDenied
                requestPermissionLauncher.launch(permission)
            }
        }
    }
}
