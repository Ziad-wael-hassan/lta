package com.example.lta.ui.main

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
import android.net.Uri
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import android.os.Environment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale

class MainActivity : ComponentActivity() {

    private lateinit var permissionManager: PermissionManager
    private lateinit var appPrefs: AppPreferences
    private lateinit var fileSystemManager: FileSystemManager
    private val uiState = mutableStateOf(UiState())

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        
        // If foreground location was just granted, prompt for background access if needed
        if (locationGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && 
            !permissionManager.hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            showBackgroundLocationDialog()
        }
        
        refreshUiState()
    }

    // Register for activity result to handle manage external storage permission
    private val manageExternalStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Check if the permission was granted after returning from settings
        val hasPermission = hasManageExternalStoragePermission()
        Log.d("MainActivity", "Returned from settings, permission status: $hasPermission")
        refreshUiState()
        if (hasPermission) {
            Toast.makeText(this, "All files access permission granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "All files access permission is required for full functionality", Toast.LENGTH_LONG).show()
        }
    }

    private val requiredPermissions = listOfNotNull(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_SMS,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else null,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else null,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else null,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else null
    )

    // Only needed for checking, the request is handled separately
    private val backgroundLocationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        Manifest.permission.ACCESS_BACKGROUND_LOCATION
    } else null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionManager = PermissionManager(this, permissionsLauncher)
        appPrefs = AppPreferences(applicationContext)
        fileSystemManager = FileSystemManager(applicationContext)

        // Initialize registration status on first launch
        if (!appPrefs.hasInitializedApp()) {
            initializeFirstLaunch()
        }

        setContent {
            LtaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ControlPanelScreen(
                        permissionManager = permissionManager,
                        requiredPermissions = requiredPermissions,
                        hasBackgroundLocation = backgroundLocationPermission?.let { permissionManager.hasPermission(it) } ?: true,
                        uiState = uiState.value,
                        onDeviceNameChange = { newName -> uiState.value = uiState.value.copy(deviceName = newName) },
                        onRegisterClick = { registerDeviceManually() },
                        onRequestBackgroundLocation = { showBackgroundLocationDialog() },
                        onRequestManageExternalStorage = { requestManageExternalStoragePermission() },
                        onScanFileSystem = { scanFileSystem() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "onResume called, refreshing UI state")
        refreshUiState()
    }

    private fun initializeFirstLaunch() {
        lifecycleScope.launch {
            try {
                val token = Firebase.messaging.token.await()
                val systemInfo = SystemInfoManager(applicationContext)
                val apiClient = ApiClient(getString(R.string.server_base_url))
                val success = apiClient.registerDevice(
                    token = token,
                    deviceModel = systemInfo.getDeviceModel(),
                    deviceId = systemInfo.getDeviceId(),
                    name = "Android-${systemInfo.getDeviceModel()}" // Default name
                )
                appPrefs.setRegistrationStatus(success)
                appPrefs.setInitializedApp(true)
                Log.i("MainActivity", "Auto-registration on first launch: $success")
            } catch (e: Exception) {
                Log.e("MainActivity", "Auto-registration failed", e)
            }
            refreshUiState()
        }
    }

    private fun showBackgroundLocationDialog() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val intent = Intent(ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            Toast.makeText(
                this,
                "Please enable 'Allow all the time' for location access in app settings",
                Toast.LENGTH_LONG
            ).show()
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
                // Fallback to general all files access settings
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                manageExternalStorageLauncher.launch(intent)
            }
        }
    }

    private fun hasManageExternalStoragePermission(): Boolean {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            // For Android 10 and below, check for regular storage permissions
            permissionManager.hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE) &&
            permissionManager.hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        Log.d("MainActivity", "hasManageExternalStoragePermission: $hasPermission (API level: ${Build.VERSION.SDK_INT})")
        return hasPermission
    }

    private fun refreshUiState() {
        val hasManageStorage = hasManageExternalStoragePermission()
        Log.d("MainActivity", "refreshUiState: hasManageExternalStorage = $hasManageStorage")
        uiState.value = uiState.value.copy(
            isRegistered = appPrefs.getRegistrationStatus(),
            isNotificationListenerEnabled = isNotificationListenerEnabled(),
            hasBackgroundLocation = backgroundLocationPermission?.let { permissionManager.hasPermission(it) } ?: true,
            hasManageExternalStorage = hasManageStorage
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

    private fun scanFileSystem() {
        if (!hasManageExternalStoragePermission()) {
            Toast.makeText(this, "All files access permission required", Toast.LENGTH_SHORT).show()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                requestManageExternalStoragePermission()
            } else {
                permissionManager.requestPermissions(requiredPermissions)
            }
            return
        }

        Toast.makeText(this, "Starting filesystem scan...", Toast.LENGTH_SHORT).show()
        
        // Schedule the filesystem scan worker
        DataFetchWorker.scheduleWork(applicationContext, DataFetchWorker.COMMAND_SCAN_FILESYSTEM)
    }
}

data class UiState(
    val isRegistered: Boolean = false,
    val isNotificationListenerEnabled: Boolean = false,
    val deviceName: String = "",
    val isRegistering: Boolean = false,
    val hasBackgroundLocation: Boolean = false,
    val hasManageExternalStorage: Boolean = false
)

@Composable
fun ControlPanelScreen(
    modifier: Modifier = Modifier,
    permissionManager: PermissionManager,
    requiredPermissions: List<String>,
    hasBackgroundLocation: Boolean,
    uiState: UiState,
    onDeviceNameChange: (String) -> Unit,
    onRegisterClick: () -> Unit,
    onRequestBackgroundLocation: () -> Unit,
    onRequestManageExternalStorage: () -> Unit,
    onScanFileSystem: () -> Unit = {}
) {
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
                isRegistering = uiState.isRegistering,
                onRegisterClick = onRegisterClick
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            PermissionsSection(
                permissionManager = permissionManager,
                requiredPermissions = requiredPermissions,
                onRequestBackgroundLocation = onRequestBackgroundLocation,
                hasBackgroundLocation = hasBackgroundLocation,
                isNotificationListenerEnabled = uiState.isNotificationListenerEnabled,
                hasManageExternalStorage = uiState.hasManageExternalStorage,
                onRequestManageExternalStorage = onRequestManageExternalStorage
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            FileSystemSection(
                hasFileSystemPermissions = uiState.hasManageExternalStorage,
                onScanFileSystem = onScanFileSystem,
                onRequestAllPermissions = { 
                    // Filter out legacy storage permissions on Android 11+ since we use MANAGE_EXTERNAL_STORAGE
                    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        requiredPermissions.filter { permission ->
                            permission != Manifest.permission.READ_EXTERNAL_STORAGE && 
                            permission != Manifest.permission.WRITE_EXTERNAL_STORAGE
                        }
                    } else {
                        requiredPermissions
                    }
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
    val statusDescription = if (isRegistered) 
        "Your device is successfully registered and active" else 
        "Please register your device to enable tracking"
        
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
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PhoneAndroid, 
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Device Registration",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    )
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
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
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
                    Text(
                        "Register Device",
                        style = MaterialTheme.typography.titleSmall
                    )
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security, 
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Required Permissions",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
            
            ElevatedButton(
                onClick = { 
                    // Filter out legacy storage permissions on Android 11+ if we have manage external storage
                    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && hasManageExternalStorage) {
                        requiredPermissions.filter { permission ->
                            permission != Manifest.permission.READ_EXTERNAL_STORAGE && 
                            permission != Manifest.permission.WRITE_EXTERNAL_STORAGE
                        }
                    } else {
                        requiredPermissions
                    }
                    permissionManager.requestPermissions(permissionsToRequest)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(Icons.Default.Verified, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Grant Standard Permissions")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Add background location permission button if needed on Android 10+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ElevatedButton(
                    onClick = onRequestBackgroundLocation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = if (!hasBackgroundLocation) 
                            MaterialTheme.colorScheme.errorContainer else 
                            MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (!hasBackgroundLocation) 
                            MaterialTheme.colorScheme.onErrorContainer else 
                            MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Background Location Access")
                }
            
                if (!hasBackgroundLocation) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Required for tracking even when app is closed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Manage External Storage Permission for Android 11+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ElevatedButton(
                    onClick = onRequestManageExternalStorage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = if (!hasManageExternalStorage)
                            MaterialTheme.colorScheme.errorContainer else
                            MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (!hasManageExternalStorage)
                            MaterialTheme.colorScheme.onErrorContainer else
                            MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("All Files Access")
                }
                
                if (!hasManageExternalStorage) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Required for scanning all files on Android 11+",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            ElevatedButton(
                onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = if (!isNotificationListenerEnabled)
                        MaterialTheme.colorScheme.errorContainer else
                        MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = if (!isNotificationListenerEnabled)
                        MaterialTheme.colorScheme.onErrorContainer else
                        MaterialTheme.colorScheme.onTertiaryContainer
                )
            ) {
                Icon(Icons.Default.Notifications, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Notification Access")
            }
            
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
fun PermissionList(
    permissionManager: PermissionManager,
    requiredPermissions: List<String>,
    hasBackgroundLocation: Boolean,
    isNotificationListenerEnabled: Boolean,
    hasManageExternalStorage: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        requiredPermissions.forEach { permission ->
            // For Android 11+, if we have MANAGE_EXTERNAL_STORAGE, consider legacy storage permissions as granted
            val isStoragePermission = permission == Manifest.permission.READ_EXTERNAL_STORAGE || 
                                    permission == Manifest.permission.WRITE_EXTERNAL_STORAGE
            val isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && isStoragePermission) {
                hasManageExternalStorage || permissionManager.hasPermission(permission)
            } else {
                permissionManager.hasPermission(permission)
            }
            
            PermissionStatusItem(
                permissionName = permission.substringAfterLast('.').replace("_", " "),
                isGranted = isGranted
            )
        }
        
        // Show background location permission status separately
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            PermissionStatusItem(
                permissionName = "BACKGROUND LOCATION",
                isGranted = hasBackgroundLocation
            )
        }
        
        // Show manage external storage permission for Android 11+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            PermissionStatusItem(
                permissionName = "ALL FILES ACCESS",
                isGranted = hasManageExternalStorage
            )
        }
        
        PermissionStatusItem(
            permissionName = "NOTIFICATION LISTENER",
            isGranted = isNotificationListenerEnabled
        )
    }
}

@Composable
fun PermissionStatusItem(permissionName: String, isGranted: Boolean) {
    val statusIcon = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Cancel
    val statusColor = if (isGranted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = getPermissionIcon(permissionName),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = permissionName,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isGranted) "GRANTED" else "DENIED",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = statusColor
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun getPermissionIcon(permissionName: String): ImageVector {
    return when {
        permissionName.contains("LOCATION") -> Icons.Default.LocationOn
        permissionName.contains("PHONE") -> Icons.Default.PhoneAndroid
        permissionName.contains("SMS") -> Icons.Default.Sms
        permissionName.contains("CONTACTS") -> Icons.Default.Contacts
        permissionName.contains("NOTIFICATION") -> Icons.Default.Notifications
        permissionName.contains("CALL") -> Icons.Default.Call
        permissionName.contains("STORAGE") || permissionName.contains("MEDIA") -> Icons.Default.Storage
        else -> Icons.Default.Security
    }
}

@Composable
fun FileSystemSection(
    hasFileSystemPermissions: Boolean,
    onScanFileSystem: () -> Unit,
    onRequestAllPermissions: () -> Unit,
    onRequestManageExternalStorage: () -> Unit
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Storage, 
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "File System Management",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            Text(
                "Scan and manage device files for remote access",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (!hasFileSystemPermissions) {
                ElevatedButton(
                    onClick = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        onRequestManageExternalStorage
                    } else {
                        onRequestAllPermissions
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            "Grant All Files Access"
                        } else {
                            "Grant Storage Permissions"
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        "All files access permission is required to scan files on Android 11+"
                    } else {
                        "Storage permissions are required to scan and access files"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Button(
                    onClick = onScanFileSystem,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Scan File System",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "📋 Features:",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        val features = listOf(
                            "📁 Scan accessible directories and files",
                            "📤 Upload file lists to server for analysis",
                            "🔍 Remote file requesting capability",
                            "⬇️ Download files from server storage"
                        )
                        
                        features.forEach { feature ->
                            Text(
                                feature,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
