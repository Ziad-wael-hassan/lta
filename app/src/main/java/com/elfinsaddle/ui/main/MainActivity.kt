// MainActivity.kt
package com.elfinsaddle.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.elfinsaddle.BuildConfig // Import BuildConfig
import com.elfinsaddle.service.MyFirebaseMessagingService // Import your service
import com.elfinsaddle.ui.theme.LtaTheme
import com.elfinsaddle.util.PermissionManager

class MainActivity : ComponentActivity() {

    private lateinit var permissionManager: PermissionManager
    private val viewModel: MainViewModel by viewModels { MainViewModelFactory(this) }

    // --- NEW: Launcher for the Android 13+ Notification Permission ---
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Notification permission granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Notifications will not be shown.", Toast.LENGTH_LONG).show()
        }
        viewModel.refreshUiState() // Refresh UI to reflect permission state if needed
    }
    // -------------------------------------------------------------------

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // If foreground location was just granted, prompt for background access if needed
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineLocationGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val backgroundPermission = Manifest.permission.ACCESS_BACKGROUND_LOCATION
            if (!permissionManager.hasPermission(backgroundPermission)) {
                showBackgroundLocationDialog()
            }
        }
        viewModel.refreshUiState()
    }

    private val manageExternalStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshUiState()
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else true
        if (hasPermission) {
            Toast.makeText(this, "All files access granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "All files access is required for full functionality.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionManager = PermissionManager(this, permissionsLauncher)

        // --- NEW: Manually fetch and log FCM token on app start for debugging ---
        // This only runs in DEBUG builds and is useful for checking the token
        // in release builds when connected to Logcat.
        if (BuildConfig.DEBUG) {
            MyFirebaseMessagingService.fetchAndLogCurrentToken()
        }
        // -----------------------------------------------------------------------

        setContent {
            LtaTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val uiState = viewModel.uiState

                    LaunchedEffect(uiState.registrationMessage) {
                        uiState.registrationMessage?.let { message ->
                            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                            viewModel.clearRegistrationMessage()
                        }
                    }

                    ControlPanelScreen(
                        modifier = Modifier,
                        permissionManager = permissionManager,
                        uiState = uiState,
                        onDeviceNameChange = viewModel::onDeviceNameChange,
                        onRegisterClick = viewModel::registerDevice,
                        onScanFileSystem = {
                            Toast.makeText(this, "Starting filesystem scan...", Toast.LENGTH_SHORT).show()
                            viewModel.scanFileSystem()
                        },
                        onRequestBackgroundLocation = { showBackgroundLocationDialog() },
                        onRequestManageExternalStorage = { requestManageExternalStoragePermission() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshUiState()
        // --- NEW: Check for and request notification permission on resume ---
        checkAndRequestNotificationPermission()
        // --------------------------------------------------------------------
    }

    // --- NEW: Function to handle notification permission request ---
    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            val isGranted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            if (!isGranted) {
                // This will trigger the launcher defined above
                notificationPermissionLauncher.launch(permission)
            }
        }
    }
    // ---------------------------------------------------------------

    private fun showBackgroundLocationDialog() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Toast.makeText(this, "Please select 'Allow all the time' in app settings for location access.", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
        }
    }

    private fun requestManageExternalStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                manageExternalStorageLauncher.launch(intent)
            } catch (e: Exception) {
                // Fallback for some devices
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                manageExternalStorageLauncher.launch(intent)
            }
        }
    }
}
