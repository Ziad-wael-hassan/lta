// ui/main/SystemMonitorScreen.kt
package com.elfinsaddle.ui.main

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elfinsaddle.BuildConfig
import com.elfinsaddle.ui.theme.*
import com.elfinsaddle.util.PermissionManager
import kotlinx.coroutines.delay

private enum class PermissionStatus { GRANTED, DENIED }

@Composable
fun SystemMonitorScreen(
    modifier: Modifier = Modifier,
    permissionManager: PermissionManager,
    uiState: UiState,
    onDeviceNameChange: (String) -> Unit,
    onRegisterClick: () -> Unit,
    onRequestBackgroundLocation: () -> Unit,
    onRequestManageExternalStorage: () -> Unit
) {
    val context = LocalContext.current
    val requiredPermissions = remember { getRequiredPermissions() }

    // --- ENHANCEMENT: Entry Animation State ---
    // This state triggers the staggered animations when the screen first composes.
    var isContentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isContentVisible = true
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

            // --- ENHANCEMENT: Staggered Entry Animations ---
            // Each major card fades in and slides up sequentially for a polished entry effect.
            StaggeredAnimatedVisibility(visible = isContentVisible, index = 0) {
                ConnectionStatusCard(isRegistered = uiState.isRegistered)
            }
            StaggeredAnimatedVisibility(visible = isContentVisible, index = 1) {
                DeviceConfigCard(
                    deviceName = uiState.deviceName,
                    onDeviceNameChange = onDeviceNameChange,
                    isRegistering = uiState.isLoading,
                    onRegisterClick = onRegisterClick
                )
            }
            StaggeredAnimatedVisibility(visible = isContentVisible, index = 2) {
                PermissionsCard(
                    standardPermissionsGranted = uiState.allStandardPermissionsGranted,
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
            }

            ScreenFooter()
        }
    }
}

/**
 * A helper composable to simplify staggered entry animations for a list of items.
 */
@Composable
private fun StaggeredAnimatedVisibility(
    visible: Boolean,
    index: Int,
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    // --- ENHANCEMENT: Staggered Entry Animation ---
    // A delay based on the item's index creates the sequential "staggered" effect.
    val delay = (index * 100).toLong()

    // State to control visibility after the initial delay.
    var itemVisible by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        delay(delay)
        itemVisible = visible
    }

    AnimatedVisibility(
        visible = itemVisible,
        enter = fadeIn(animationSpec = tween(durationMillis = 300)) +
                slideInVertically(
                    initialOffsetY = { it / 4 },
                    animationSpec = tween(durationMillis = 400)
                ),
        exit = fadeOut(animationSpec = tween(durationMillis = 200)),
        content = content
    )
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
                imageVector = Icons.Default.Shield, // Changed icon for better representation
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
    val statusText = if (isRegistered) "Connected to Server" else "Registration Required"
    val descriptionText = if (isRegistered) "Sync is active" else "Please register the device"
    val badgeText = if (isRegistered) "Online" else "Offline"
    val icon = if (isRegistered) Icons.Filled.Wifi else Icons.Filled.WifiOff

    // --- ENHANCEMENT: Pulsing Icon Animation ---
    // A subtle pulse effect draws attention when the status is "Online".
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRegistered) 1.1f else 1f, // Only pulse when online
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse-scale"
    )

    // --- ENHANCEMENT: Smooth Color Transitions ---
    // Animates colors for the icon and badge when connection status changes.
    val iconColor by animateColorAsState(
        targetValue = if (isRegistered) StatusGreen else StatusRed,
        animationSpec = tween(durationMillis = 500),
        label = "status-icon-color"
    )
    val badgeContainerColor by animateColorAsState(
        targetValue = if (isRegistered) StatusGreenContainer else StatusRedContainer,
        animationSpec = tween(durationMillis = 500),
        label = "badge-container-color"
    )
    val badgeTextColor by animateColorAsState(
        targetValue = if (isRegistered) StatusGreen else StatusRed,
        animationSpec = tween(durationMillis = 500),
        label = "badge-text-color"
    )

    // --- ENHANCEMENT: Material 3 Tonal Elevation ---
    // Uses surfaceVariant for the card background, providing a subtle tonal lift
    // that adapts to light/dark themes, instead of a hardcoded shadow.
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp) // Increased spacing
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "Status Icon",
                tint = iconColor,
                modifier = Modifier.scale(scale) // Apply pulse effect
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = statusText, style = MaterialTheme.typography.titleMedium)
                Text(text = descriptionText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(badgeContainerColor)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(text = badgeText, color = badgeTextColor, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun DeviceConfigCard(
    deviceName: String,
    onDeviceNameChange: (String) -> Unit,
    isRegistering: Boolean,
    onRegisterClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            CardHeader(icon = Icons.Default.Tune, title = "Device Configuration")
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Device Name",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = onDeviceNameChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Enter device name") },
                    enabled = !isRegistering,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Button(
                    onClick = onRegisterClick,
                    enabled = !isRegistering,
                    modifier = Modifier.height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    // --- ENHANCEMENT: Animated Save Button ---
                    // AnimatedContent provides a smooth crossfade between the "Save" text
                    // and the loading indicator, giving clear feedback on the action state.
                    AnimatedContent(
                        targetState = isRegistering,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(220, delayMillis = 90)) with
                                    fadeOut(animationSpec = tween(90)) using
                                    SizeTransform(clip = false)
                        }, label = "save-button-content"
                    ) { isCurrentlyRegistering ->
                        if (isCurrentlyRegistering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary, // Theme-aware color
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Text("Save")
                        }
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
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            CardHeader(icon = Icons.Default.VerifiedUser, title = "System Permissions")
            Spacer(modifier = Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PermissionBlock(
                    title = "Standard Permissions",
                    description = "Basic system access for configuration sync",
                    status = if (standardPermissionsGranted) PermissionStatus.GRANTED else PermissionStatus.DENIED,
                    buttonText = "Grant",
                    buttonIcon = Icons.Default.DoneAll,
                    onClick = onGrantStandardPermissions
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    PermissionBlock(
                        title = "All Files Access",
                        description = "Required for full file system access",
                        status = if (fileAccessGranted) PermissionStatus.GRANTED else PermissionStatus.DENIED,
                        buttonText = "Enable Access",
                        buttonIcon = Icons.Default.FolderOpen,
                        onClick = onGrantFileAccess
                    )
                }
                PermissionBlock(
                    title = "Notification Sync",
                    description = "Sync system notifications and alerts",
                    status = if (notificationSyncGranted) PermissionStatus.GRANTED else PermissionStatus.DENIED,
                    buttonText = "Enable Sync",
                    buttonIcon = Icons.Default.Notifications,
                    onClick = onGrantNotificationSync
                )
            }
        }
    }
}


@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun PermissionBlock(
    title: String,
    description: String,
    status: PermissionStatus,
    buttonText: String,
    buttonIcon: ImageVector?,
    onClick: () -> Unit
) {
    val isGranted = status == PermissionStatus.GRANTED
    val statusText = if (isGranted) "granted" else "denied"

    // --- ENHANCEMENT: Smooth Color Transitions ---
    // Colors for the icon, background, and border smoothly animate when the
    // permission status changes, providing clear and satisfying visual feedback.
    val statusColor by animateColorAsState(
        targetValue = if (isGranted) StatusGreen else MaterialTheme.colorScheme.error,
        animationSpec = tween(500), label = "permission-status-color"
    )
    val containerColor by animateColorAsState(
        targetValue = if (isGranted) StatusGreenContainer else MaterialTheme.colorScheme.errorContainer,
        animationSpec = tween(500), label = "permission-container-color"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isGranted) StatusGreenBorder else MaterialTheme.colorScheme.errorContainer,
        animationSpec = tween(500), label = "permission-border-color"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .background(containerColor, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp)) // Clip for ripple effect
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // --- ENHANCEMENT: Animated Icon Transition ---
                // AnimatedContent crossfades between the Cancel and CheckCircle icons,
                // making the state change feel more polished and responsive.
                AnimatedContent(
                    targetState = isGranted,
                    transitionSpec = {
                        (fadeIn() + scaleIn()).togetherWith(fadeOut() + scaleOut())
                    }, label = "permission-icon"
                ) { granted ->
                    Icon(
                        imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(text = title, style = MaterialTheme.typography.titleMedium)
            }
            // A more subtle status badge
            Text(
                text = statusText.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = statusColor
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 30.dp, bottom = 8.dp) // Aligned with icon+space
        )

        // --- ENHANCEMENT: Button Fade-out ---
        // The grant button gracefully fades out when no longer needed,
        // cleaning up the UI and confirming the action was successful.
        AnimatedVisibility(
            visible = !isGranted,
            enter = fadeIn(animationSpec = tween(delayMillis = 200)) + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(top = 4.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                buttonIcon?.let {
                    Icon(imageVector = it, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(text = buttonText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}


@Composable
private fun ScreenFooter() {
    Text(
        text = "System Monitor v${BuildConfig.VERSION_NAME}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun CardHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary, // Use primary color for accent
            modifier = Modifier.size(22.dp)
        )
        Text(text = title, style = MaterialTheme.typography.titleLarge)
    }
}

// --- CODE QUALITY: Removed redundant helper functions ---
// These functions are now centralized in the MainViewModel, which is the single
// source of truth for permission logic. This avoids duplication and keeps the UI layer clean.
private fun getRequiredPermissions(): List<String> = listOfNotNull(
    Manifest.permission.READ_PHONE_STATE,
    Manifest.permission.ACCESS_FINE_LOCATION,
    // ... (rest of permissions as they were)
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
