package com.example.lta

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
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
import androidx.lifecycle.lifecycleScope
import com.example.lta.ui.theme.LtaTheme
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {

    private lateinit var permissionManager: PermissionManager
    private lateinit var appPrefs: AppPreferences
    private val uiState = mutableStateOf(UiState())

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshUiState()
    }

    private val requiredPermissions = listOfNotNull(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_SMS,
        Manifest.permission.READ_CONTACTS,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else null
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionManager = PermissionManager(this, permissionsLauncher)
        appPrefs = AppPreferences(applicationContext)

        setContent {
            LtaTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ControlPanelScreen(
                        modifier = Modifier.padding(innerPadding),
                        permissionManager = permissionManager,
                        requiredPermissions = requiredPermissions,
                        uiState = uiState.value,
                        onDeviceNameChange = { newName -> uiState.value = uiState.value.copy(deviceName = newName) },
                        onRegisterClick = { registerDeviceManually() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUiState()
    }

    private fun refreshUiState() {
        uiState.value = uiState.value.copy(
            isRegistered = appPrefs.getRegistrationStatus(),
            isNotificationListenerEnabled = isNotificationListenerEnabled()
        )
    }

    private fun registerDeviceManually() {
        val deviceName = uiState.value.deviceName
        if (deviceName.isBlank()) {
            Toast.makeText(this, "Device name cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        uiState.value = uiState.value.copy(isRegistering = true)
        lifecycleScope.launch {
            try {
                val apiClient = ApiClient(getString(R.string.server_base_url))
                val systemInfo = SystemInfoManager(applicationContext)
                val token = Firebase.messaging.token.await()
                val success = apiClient.registerDevice(
                    token = token,
                    deviceModel = systemInfo.getDeviceModel(),
                    deviceId = systemInfo.getDeviceId(),
                    name = deviceName
                )
                appPrefs.setRegistrationStatus(success)
                val message = if (success) "Device registered successfully!" else "Registration failed."
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e("MainActivity", "Manual registration failed", e)
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                appPrefs.setRegistrationStatus(false)
            } finally {
                refreshUiState()
                uiState.value = uiState.value.copy(isRegistering = false)
            }
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        return Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?.contains(packageName) == true
    }
}

data class UiState(
    val isRegistered: Boolean = false,
    val isNotificationListenerEnabled: Boolean = false,
    val deviceName: String = "",
    val isRegistering: Boolean = false
)

@Composable
fun ControlPanelScreen(
    modifier: Modifier = Modifier,
    permissionManager: PermissionManager,
    requiredPermissions: List<String>,
    uiState: UiState,
    onDeviceNameChange: (String) -> Unit,
    onRegisterClick: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Data Tracker", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))

        SectionHeader("Registration Status")
        RegistrationStatusCard(isRegistered = uiState.isRegistered)
        Spacer(modifier = Modifier.height(16.dp))

        SectionHeader("Manual Device Registration")
        OutlinedTextField(
            value = uiState.deviceName,
            onValueChange = onDeviceNameChange,
            label = { Text("Enter Device Nickname") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !uiState.isRegistering
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onRegisterClick,
            enabled = !uiState.isRegistering,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.isRegistering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Register / Update Name")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "This name will be used to identify your device in the database.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        SectionHeader("Permissions")
        Button(
            onClick = { permissionManager.requestPermissions(requiredPermissions) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Grant Standard Permissions")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Enable Notification Service")
        }
        Spacer(modifier = Modifier.height(16.dp))

        requiredPermissions.forEach { permission ->
            PermissionStatusRow(
                permissionName = permission.substringAfterLast('.').replace("_", " "),
                isGranted = permissionManager.hasPermission(permission)
            )
        }
        PermissionStatusRow(
            permissionName = "Notification Listener",
            isGranted = uiState.isNotificationListenerEnabled
        )
    }
}

@Composable
fun RegistrationStatusCard(isRegistered: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isRegistered) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val statusText = if (isRegistered) "Device is Registered" else "Device Not Registered"
            val statusColor = if (isRegistered) Color(0xFF4CAF50) else Color.Red
            Text(
                text = statusText,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = statusColor
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Column(Modifier.fillMaxWidth()) {
        Divider(modifier = Modifier.padding(vertical = 8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
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
