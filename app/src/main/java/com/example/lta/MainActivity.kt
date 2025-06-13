package com.example.lta

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

    // Define all permissions your app needs
    private val requiredPermissions = listOfNotNull(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_SMS,
        Manifest.permission.READ_CONTACTS,
        // POST_NOTIFICATIONS is only needed on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            null
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionManager = PermissionManager(this)

        setContent {
            LtaTheme {
                // We need to trigger recomposition when permission states change.
                var permissionTrigger by remember { mutableStateOf(0) }
                val onPermissionsResult = { permissionTrigger++ }

                // Register the permission manager launcher
                LaunchedEffect(Unit) {
                    permissionManager.register(
                        onAllGranted = {
                            Toast.makeText(this@MainActivity, "All permissions granted!", Toast.LENGTH_SHORT).show()
                            onPermissionsResult()
                        },
                        onSomeDenied = {
                            Toast.makeText(this@MainActivity, "Some permissions were denied.", Toast.LENGTH_LONG).show()
                            onPermissionsResult()
                        }
                    )
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ControlPanelScreen(
                        modifier = Modifier.padding(innerPadding),
                        permissionManager = permissionManager,
                        requiredPermissions = requiredPermissions,
                        // Pass the trigger to force recomposition
                        permissionCheckTrigger = permissionTrigger
                    )
                }
            }
        }
    }
}

@Composable
fun ControlPanelScreen(
    modifier: Modifier = Modifier,
    permissionManager: PermissionManager,
    requiredPermissions: List<String>,
    permissionCheckTrigger: Int
) {
    val context = LocalContext.current

    // This function checks the status of the special Notification Listener permission
    val isNotificationListenerEnabled: () -> Boolean = {
        val enabledListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        enabledListeners?.contains(context.packageName) == true
    }

    // This state will update whenever we check permissions
    var notificationListenerState by remember { mutableStateOf(isNotificationListenerEnabled()) }

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

        // Button to request all standard permissions at once
        Button(
            onClick = { permissionManager.requestPermissions(requiredPermissions) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Grant Standard Permissions")
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Button to open settings for the Notification Listener
        Button(
            onClick = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                // A common pattern is to check again when the user comes back to the app,
                // but for simplicity, we'll rely on a manual re-check or app restart.
                // To make it more robust, you would use `rememberLauncherForActivityResult`.
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

        // Display the status of all permissions
        // The `key` forces this block to re-evaluate when permissionCheckTrigger changes
        key(permissionCheckTrigger) {
            requiredPermissions.forEach { permission ->
                PermissionStatusRow(
                    permissionName = permission.substringAfterLast('.').replace("_", " "),
                    isGranted = permissionManager.hasPermission(permission)
                )
            }
            // Also display the status of the special permission
            PermissionStatusRow(
                permissionName = "Notification Listener",
                isGranted = notificationListenerState
            )
        }
    }
}

@Composable
fun PermissionStatusRow(permissionName: String, isGranted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(permissionName, fontSize = 14.sp)
        Text(
            text = if (isGranted) "GRANTED" else "DENIED",
            color = if (isGranted) Color(0xFF4CAF50) else Color.Red,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}
