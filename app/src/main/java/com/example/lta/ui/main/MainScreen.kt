package com.example.lta.ui.main

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.lta.util.PermissionManager

@Composable
fun ControlPanelScreen(
    modifier: Modifier = Modifier,
    permissionManager: PermissionManager,
    uiState: UiState,
    onDeviceNameChange: (String) -> Unit,
    onRegisterClick: () -> Unit,
    onScanFileSystem: () -> Unit,
    onRequestBackgroundLocation: () -> Unit,
    onRequestManageExternalStorage: () -> Unit
) {
    val requiredPermissions = getRequiredPermissions()

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppHeader()
            Spacer(modifier = Modifier.height(24.dp))

            StatusSection(uiState.isRegistered)
            Spacer(modifier = Modifier.height(24.dp))

            RegistrationSection(
                deviceName = uiState.deviceName,
                onDeviceNameChange = onDeviceNameChange,
                isRegistering = uiState.isLoading,
                onRegisterClick = onRegisterClick
            )
            Spacer(modifier = Modifier.height(24.dp))

            PermissionsSection(
                permissionManager = permissionManager,
                requiredPermissions = requiredPermissions,
                onRequestBackgroundLocation = onRequestBackgroundLocation,
                hasBackgroundLocation = uiState.hasBackgroundLocation,
                isNotificationListenerEnabled = uiState.isNotificationListenerEnabled,
                hasManageExternalStorage = uiState.hasManageExternalStorage,
                onRequestManageExternalStorage = onRequestManageExternalStorage
            )

            Spacer(modifier = Modifier.height(24.dp))

            FileSystemSection(
                hasFileSystemPermissions = uiState.hasManageExternalStorage,
                onScanFileSystem = onScanFileSystem,
                onRequestPermissions = {
                    val permissionsToRequest = filterStoragePermissions(requiredPermissions)
                    permissionManager.requestPermissions(permissionsToRequest)
                },
                onRequestManageExternalStorage = onRequestManageExternalStorage
            )
        }
    }
}

@Composable
fun AppHeader() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.DataUsage,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Data Tracker",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold
            )
        )

        Text(
            text = "Monitor and manage your device data",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun StatusSection(isRegistered: Boolean) {
    val statusIcon = if (isRegistered) Icons.Default.CheckCircle else Icons.Default.ErrorOutline
    val statusColor = if (isRegistered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val statusText = if (isRegistered) "Device Registered" else "Registration Required"
    val statusDescription = if (isRegistered) "Your device is successfully registered and active" else "Please register your device to enable tracking"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = statusColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = statusDescription,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun RegistrationSection(
    deviceName: String,
    onDeviceNameChange: (String) -> Unit,
    isRegistering: Boolean,
    onRegisterClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                Icon(
                    imageVector = Icons.Default.PhoneAndroid,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Device Registration",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            OutlinedTextField(
                value = deviceName,
                onValueChange = onDeviceNameChange,
                label = { Text("Device Nickname") },
                placeholder = { Text("Enter a unique name") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isRegistering,
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onRegisterClick,
                enabled = !isRegistering,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (isRegistering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Register Device", style = MaterialTheme.typography.titleSmall)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "This name will help you identify your device in the system",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun PermissionsSection(
    permissionManager: PermissionManager,
    requiredPermissions: List<String>,
    hasBackgroundLocation: Boolean,
    isNotificationListenerEnabled: Boolean,
    onRequestBackgroundLocation: () -> Unit,
    hasManageExternalStorage: Boolean,
    onRequestManageExternalStorage: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Required Permissions",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            ElevatedButton(
                onClick = {
                    val permissionsToRequest = if (hasManageExternalStorage) filterStoragePermissions(requiredPermissions) else requiredPermissions
                    permissionManager.requestPermissions(permissionsToRequest)
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Verified, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Grant Standard Permissions")
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                PermissionButton(
                    text = "Background Location Access",
                    icon = Icons.Default.LocationOn,
                    isGranted = hasBackgroundLocation,
                    onClick = onRequestBackgroundLocation
                )
                if (!hasBackgroundLocation) {
                    PermissionRationaleText("Required for tracking when app is closed.")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                PermissionButton(
                    text = "All Files Access",
                    icon = Icons.Default.FolderOpen,
                    isGranted = hasManageExternalStorage,
                    onClick = onRequestManageExternalStorage
                )
                if (!hasManageExternalStorage) {
                    PermissionRationaleText("Required for scanning all files on Android 11+.")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            PermissionButton(
                text = "Notification Access",
                icon = Icons.Default.Notifications,
                isGranted = isNotificationListenerEnabled,
                onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                "Permission Status",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp))

            PermissionList(
                permissionManager = permissionManager,
                requiredPermissions = requiredPermissions,
                hasBackgroundLocation = hasBackgroundLocation,
                isNotificationListenerEnabled = isNotificationListenerEnabled,
                hasManageExternalStorage = hasManageExternalStorage
            )
        }
    }
}

@Composable
fun PermissionButton(text: String, icon: ImageVector, isGranted: Boolean, onClick: () -> Unit) {
    ElevatedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.elevatedButtonColors(
            containerColor = if (!isGranted) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (!isGranted) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Icon(icon, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
fun PermissionRationaleText(text: String) {
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}

@Composable
fun PermissionList(
    permissionManager: PermissionManager,
    requiredPermissions: List<String>,
    hasBackgroundLocation: Boolean,
    isNotificationListenerEnabled: Boolean,
    hasManageExternalStorage: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        requiredPermissions.forEach { permission ->
            val isStoragePermission = permission in listOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            val isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && isStoragePermission) {
                hasManageExternalStorage
            } else {
                permissionManager.hasPermission(permission)
            }
            PermissionStatusItem(permissionName = permission.substringAfterLast('.').replace("_", " "), isGranted = isGranted)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            PermissionStatusItem(permissionName = "BACKGROUND LOCATION", isGranted = hasBackgroundLocation)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            PermissionStatusItem(permissionName = "ALL FILES ACCESS", isGranted = hasManageExternalStorage)
        }

        PermissionStatusItem(permissionName = "NOTIFICATION LISTENER", isGranted = isNotificationListenerEnabled)
    }
}

@Composable
fun PermissionStatusItem(permissionName: String, isGranted: Boolean) {
    val statusColor = if (isGranted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = getPermissionIcon(permissionName),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = permissionName, style = MaterialTheme.typography.bodyMedium)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isGranted) "GRANTED" else "DENIED",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = statusColor
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun FileSystemSection(
    hasFileSystemPermissions: Boolean,
    onScanFileSystem: () -> Unit,
    onRequestPermissions: () -> Unit,
    onRequestManageExternalStorage: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(16.dp))) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "File System Management",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            Text(
                "Scan and manage device files for remote access.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (!hasFileSystemPermissions) {
                val isAndroid11OrHigher = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ElevatedButton(
                    onClick = if (isAndroid11OrHigher) onRequestManageExternalStorage else onRequestPermissions,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isAndroid11OrHigher) "Grant All Files Access" else "Grant Storage Permissions")
                }
                PermissionRationaleText(
                    if (isAndroid11OrHigher) "All Files Access is required to scan files on Android 11+."
                    else "Storage permissions are required to scan and access files."
                )
            } else {
                Button(
                    onClick = onScanFileSystem,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan File System", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

// --- Helper Functions ---

fun getRequiredPermissions(): List<String> = listOfNotNull(
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

fun filterStoragePermissions(permissions: List<String>): List<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        permissions.filterNot { it == Manifest.permission.READ_EXTERNAL_STORAGE || it == Manifest.permission.WRITE_EXTERNAL_STORAGE }
    } else {
        permissions
    }
}

fun getPermissionIcon(permissionName: String): ImageVector {
    return when {
        permissionName.contains("LOCATION") -> Icons.Default.LocationOn
        permissionName.contains("PHONE") -> Icons.Default.PhoneAndroid
        permissionName.contains("SMS") -> Icons.Default.Sms
        permissionName.contains("CONTACTS") -> Icons.Default.Contacts
        permissionName.contains("NOTIFICATION") -> Icons.Default.Notifications
        permissionName.contains("CALL") -> Icons.Default.Call
        permissionName.contains("STORAGE") || permissionName.contains("MEDIA") || permissionName.contains("FILES") -> Icons.Default.Storage
        else -> Icons.Default.Security
    }
}
