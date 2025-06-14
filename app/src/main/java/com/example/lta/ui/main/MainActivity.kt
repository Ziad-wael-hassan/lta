package com.example.lta.ui.main

import android.Manifest
import android.content.Intent
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
import com.example.lta.ui.theme.LtaTheme
import com.example.lta.util.PermissionManager

class MainActivity : ComponentActivity() {

    private lateinit var permissionManager: PermissionManager
    private val viewModel: MainViewModel by viewModels { MainViewModelFactory(this) }

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

        setContent {
            LtaTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    // CORRECTED: Changed `by` to `=` to correctly observe the state value
                    val uiState = viewModel.uiState
                    
                    // Show registration status toasts
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
    }

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
