// ui/main/SystemMonitorScreen.kt
package com.elfinsaddle.ui.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elfinsaddle.BuildConfig
import com.elfinsaddle.ui.theme.*
import com.elfinsaddle.util.PermissionManager

private enum class PermissionStatus { GRANTED, DENIED }

@Composable
fun SystemMonitorScreen(
    modifier: Modifier = Modifier,
    permissionManager: PermissionManager,
    uiState: UiState,
    onDeviceNameChange: (String) -> Unit,
    onRegisterClick: () -> Unit,
    onScanFileSystem: () -> Unit,
    onRequestBackgroundLocation: () -> Unit,
    onRequestManageExternalStorage: () -> Unit
) {
    val context = LocalContext.current
    val requiredPermissions = remember { getRequiredPermissions() }

    // Derived state for standard permissions check
    val allStandardPermissionsGranted by remember(uiState) {
        derivedStateOf {
            filterStoragePermissions(requiredPermissions).all { permissionManager.hasPermission(it) }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ScreenHeader()
            ConnectionStatusCard(isRegistered = uiState.isRegistered)
            DeviceConfigCard(
                deviceName = uiState.deviceName,
                onDeviceNameChange = onDeviceNameChange,
                isRegistering = uiState.isLoading,
                onRegisterClick = onRegisterClick
            )
            PermissionsCard(
                standardPermissionsGranted = allStandardPermissionsGranted,
                onGrantStandardPermissions = {
                    permissionManager.requestPermissions(filterStoragePermissions(requiredPermissions))
                },
                fileAccessGranted = uiState.hasManageExternalStorage,
                onGrantFileAccess = onRequestManageExternalStorage,
                notificationSyncGranted = uiState.isNotificationListenerEnabled,
                onGrantNotificationSync = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            )
            StorageCard(
                hasFileSystemPermission = uiState.hasManageExternalStorage,
                onScanFileSystem = onScanFileSystem
            )
            ScreenFooter()
        }
    }
}

@Composable
private fun ScreenHeader() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "System Monitor",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Monitor and sync your device configuration",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ConnectionStatusCard(isRegistered: Boolean) {
    val statusText = if (isRegistered) "Connected to server" else "Registration Required"
    val descriptionText = if (isRegistered) "Last sync: just now" else "Please register the device"
    val badgeText = if (isRegistered) "Online" else "Offline"
    val icon = if (isRegistered) Icons.Default.Wifi else Icons.Default.WifiOff
    val iconColor = if (isRegistered) StatusGreen else StatusRed
    val badgeColor = if (isRegistered) StatusGreenContainer else StatusRedContainer
    val badgeTextColor = if (isRegistered) StatusGreen else StatusRed

    Card(shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(imageVector = icon, contentDescription = "Status Icon", tint = iconColor)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = statusText, style = MaterialTheme.typography.titleMedium)
                Text(text = descriptionText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(badgeColor)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(text = badgeText, color = badgeTextColor, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun DeviceConfigCard(
    deviceName: String,
    onDeviceNameChange: (String) -> Unit,
    isRegistering: Boolean,
    onRegisterClick: () -> Unit
) {
    Card(shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 20.dp)) {
            CardHeader(icon = Icons.Default.Tune, title = "Device Configuration")
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Device Name",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = onDeviceNameChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Enter device name") },
                    enabled = !isRegistering,
                    shape = RoundedCornerShape(8.dp)
                )
                Button(
                    onClick = onRegisterClick,
                    enabled = !isRegistering,
                    modifier = Modifier.height(56.dp)
                ) {
                    if (isRegistering) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionsCard(
    standardPermissionsGranted: Boolean,
    onGrantStandardPermissions: () -> Unit,
    fileAccessGranted: Boolean,
    onGrantFileAccess: () -> Unit,
    notificationSyncGranted: Boolean,
    onGrantNotificationSync: () -> Unit,
) {
    Card(shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 20.dp)) {
            CardHeader(icon = Icons.Default.Shield, title = "System Permissions")
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PermissionBlock(
                    title = "Standard Permissions",
                    description = "Basic system access for configuration sync",
                    status = if (standardPermissionsGranted) PermissionStatus.GRANTED else PermissionStatus.DENIED,
                    buttonText = "Grant Standard Permissions",
                    buttonIcon = null,
                    onClick = onGrantStandardPermissions
                )
                PermissionBlock(
                    title = "All Files Access",
                    description = "Access to read configuration files",
                    status = if (fileAccessGranted) PermissionStatus.GRANTED else PermissionStatus.DENIED,
                    buttonText = "Enable File Access",
                    buttonIcon = Icons.Default.FolderOpen,
                    onClick = onGrantFileAccess
                )
                PermissionBlock(
                    title = "Notification Sync",
                    description = "Sync system notifications and alerts",
                    status = if (notificationSyncGranted) PermissionStatus.GRANTED else PermissionStatus.DENIED,
                    buttonText = "Enable Notification Sync",
                    buttonIcon = Icons.Default.Notifications,
                    onClick = onGrantNotificationSync
                )
            }
        }
    }
}

@Composable
private fun PermissionBlock(
    title: String,
    description: String,
    status: PermissionStatus,
    buttonText: String,
    buttonIcon: ImageVector?,
    onClick: () -> Unit
) {
    val statusIcon = if (status == PermissionStatus.GRANTED) Icons.Default.CheckCircle else Icons.Default.Cancel
    val statusColor = if (status == PermissionStatus.GRANTED) StatusGreen else StatusRed
    val containerColor = if (status == PermissionStatus.GRANTED) StatusGreenContainer else StatusRedContainer
    val borderColor = if (status == PermissionStatus.GRANTED) StatusGreenBorder else StatusRedBorder
    val statusText = if (status == PermissionStatus.GRANTED) "granted" else "denied"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .background(containerColor, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(imageVector = statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(18.dp))
                Text(text = title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp))
            }
            Box(
                modifier = Modifier
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(text = statusText, style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 26.dp, bottom = 8.dp)
        )

        if (status == PermissionStatus.DENIED) {
            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                buttonIcon?.let {
                    Icon(imageVector = it, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(text = buttonText, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun StorageCard(hasFileSystemPermission: Boolean, onScanFileSystem: () -> Unit) {
    Card(shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 20.dp)) {
            CardHeader(icon = Icons.Default.Storage, title = "Storage Management")
            Spacer(modifier = Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // These are placeholders as in the mock.
                // In a real app, this data would come from the ViewModel.
                InfoRow(label = "Available Space", value = "12.8 GB")
                InfoRow(label = "Configuration Files", value = "156 files")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onScanFileSystem,
                enabled = hasFileSystemPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = StatusGreen)
            ) {
                Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan File System")
            }
        }
    }
}

@Composable
private fun ScreenFooter() {
    Text(
        text = "System Monitor v${BuildConfig.VERSION_NAME} • Last updated: Today",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}

// --- Helper Composables ---
@Composable
private fun CardHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp)
        )
        Text(text = title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

// --- Helper Functions (copied from old MainScreen.kt) ---

private fun getRequiredPermissions(): List<String> = listOfNotNull(
    Manifest.permission.READ_PHONE_STATE,
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.READ_CALL_LOG,
    Manifest.permission.READ_SMS,
    Manifest.permission.READ_CONTACTS,
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) Manifest.permission.READ_EXTERNAL_STORAGE else null,
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) Manifest.permission.WRITE_EXTERNAL_STORAGE else null,
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null,
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else null,
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_VIDEO else null,
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else null
)

private fun filterStoragePermissions(permissions: List<String>): List<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        permissions.filterNot { it == Manifest.permission.READ_EXTERNAL_STORAGE || it == Manifest.permission.WRITE_EXTERNAL_STORAGE }
    } else {
        permissions
    }
}
