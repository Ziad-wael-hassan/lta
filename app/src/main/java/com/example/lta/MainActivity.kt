package com.example.lta

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lta.ui.theme.LtaTheme

class MainActivity : ComponentActivity() {

    private lateinit var permissionManager: PermissionManager

    // State holders to trigger recomposition from outside Compose
    private val permissionCheckTrigger = mutableStateOf(0)
    private val notificationListenerState = mutableStateOf(false) // <-- NEW State

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (!permissions.containsValue(false)) {
            Toast.makeText(this, "All permissions granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Some permissions were denied.", Toast.LENGTH_LONG).show()
        }
        permissionCheckTrigger.value++
    }

    private val requiredPermissions = listOfNotNull(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_SMS,
        Manifest.permission.READ_CONTACTS,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            null
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionManager = PermissionManager(this, permissionsLauncher)

        setContent {
            LtaTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ControlPanelScreen(
                        modifier = Modifier.padding(innerPadding),
                        permissionManager = permissionManager,
                        requiredPermissions = requiredPermissions,
                        permissionCheckTrigger = permissionCheckTrigger.value,
                        isNotificationListenerEnabled = notificationListenerState.value // <-- Pass the state value
                    )
                }
            }
        }
    }

    // ✅ This function is called every time the user returns to the app.
    override fun onResume() {
        super.onResume()
        // Update the notification listener state here to force a UI refresh.
        notificationListenerState.value = isNotificationListenerEnabled()
    }

    // Helper function to check the special permission status
    private fun isNotificationListenerEnabled(): Boolean {
        return Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?.contains(packageName) == true
    }
}


@Composable
fun ControlPanelScreen(
    modifier: Modifier = Modifier,
    permissionManager: PermissionManager,
    requiredPermissions: List<String>,
    permissionCheckTrigger: Int,
    isNotificationListenerEnabled: Boolean // <-- Receive the state as a simple boolean
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Data Tracker Setup", style = MaterialTheme.typography.headlineSmall)
        Text("Grant permissions to enable all features.", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { permissionManager.requestPermissions(requiredPermissions) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Grant Standard Permissions")
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Enable Notification Service")
        }

        Spacer(modifier = Modifier.height(32.dp))
        Divider()
        Spacer(modifier = Modifier.height(16.dp))
        Text("Permission Status", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        key(permissionCheckTrigger, isNotificationListenerEnabled) {
            requiredPermissions.forEach { permission ->
                PermissionStatusRow(
                    permissionName = permission.substringAfterLast('.').replace("_", " "),
                    isGranted = permissionManager.hasPermission(permission)
                )
            }
            PermissionStatusRow(
                permissionName = "Notification Listener",
                isGranted = isNotificationListenerEnabled // <-- Use the passed-in state
            )
        }
    }
}

// PermissionStatusRow composable is unchanged
@Composable
fun PermissionStatusRow(permissionName: String, isGranted: Boolean) { /* ... */ }
