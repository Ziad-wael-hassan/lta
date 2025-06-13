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

    // A state object that will be updated by the launcher's callback to trigger recomposition.
    private val permissionCheckTrigger = mutableStateOf(0)

    // ✅ 1. Define the launcher as a property of the Activity.
    // This ensures it is registered before the Activity is STARTED.
    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // This callback block executes when the user responds to the permission dialog.
        if (!permissions.containsValue(false)) {
            Toast.makeText(this, "All permissions granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Some permissions were denied.", Toast.LENGTH_LONG).show()
        }
        // Increment the trigger to force the UI to recompose and update the status list.
        permissionCheckTrigger.value++
    }

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

        // ✅ 2. Instantiate PermissionManager, passing the pre-registered launcher to it.
        permissionManager = PermissionManager(this, permissionsLauncher)

        setContent {
            LtaTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // ✅ 3. Pass the trigger's value to the composable.
                    ControlPanelScreen(
                        modifier = Modifier.padding(innerPadding),
                        permissionManager = permissionManager,
                        requiredPermissions = requiredPermissions,
                        permissionCheckTrigger = permissionCheckTrigger.value
                    )
                }
            }
        }
    }
}

// The ControlPanelScreen and PermissionStatusRow composables below do not need any changes.
@Composable
fun ControlPanelScreen(
    modifier: Modifier = Modifier,
    permissionManager: PermissionManager,
    requiredPermissions: List<String>,
    permissionCheckTrigger: Int // This Int will change, forcing recomposition
) {
    val context = LocalContext.current

    val isNotificationListenerEnabled: () -> Boolean = {
        val enabledListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        enabledListeners?.contains(context.packageName) == true
    }

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

        key(permissionCheckTrigger) {
            requiredPermissions.forEach { permission ->
                PermissionStatusRow(
                    permissionName = permission.substringAfterLast('.').replace("_", " "),
                    isGranted = permissionManager.hasPermission(permission)
                )
            }
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
