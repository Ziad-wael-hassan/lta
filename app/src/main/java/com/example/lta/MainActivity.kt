package com.example.lta

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.lta.ui.theme.LtaTheme
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var permissionManager: PermissionManager
    private lateinit var manualDataManager: ManualDataManager
    private lateinit var apiClient: ApiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionManager = PermissionManager(this)
        manualDataManager = ManualDataManager(applicationContext)
        apiClient = ApiClient(getString(R.string.server_base_url))

        setContent {
            LtaTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ControlPanelScreen(
                        modifier = Modifier.padding(innerPadding),
                        permissionManager = permissionManager,
                        manualDataManager = manualDataManager,
                        apiClient = apiClient
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
    manualDataManager: ManualDataManager,
    apiClient: ApiClient
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var statusMessage by remember { mutableStateOf("Ready.") }
    var fcmToken by remember { mutableStateOf("Loading FCM Token...") }

    // Get FCM Token on composition
    LaunchedEffect(Unit) {
        try {
            val token = Firebase.messaging.token.await()
            fcmToken = token
            Log.d("MainActivity", "FCM Token: $token")
        } catch (e: Exception) {
            fcmToken = "Error getting FCM token."
            Log.e("MainActivity", "FCM token retrieval failed", e)
        }
    }

    // Reusable helper for manual CSV exports to the server
    val exportDataAsCsv: (dataType: String, mainPermission: String, secondPermission: String?, csvProvider: () -> String) -> Unit =
        { dataType, mainPermission, secondPermission, csvProvider ->
            val proceedWithExport = {
                coroutineScope.launch {
                    try {
                        statusMessage = "Generating $dataType CSV..."
                        val csvData = withContext(Dispatchers.IO) { csvProvider() }

                        if (csvData.lines().size <= 1) { // Check if only header is present
                            statusMessage = "$dataType: No data found to export."
                            Toast.makeText(context, statusMessage, Toast.LENGTH_SHORT).show()
                            return@launch
                        }

                        val file = File.createTempFile(dataType.lowercase().replace(" ", "_"), ".csv", context.cacheDir)
                        file.writeText(csvData)

                        statusMessage = "Uploading $dataType to server..."
                        val success = apiClient.uploadFile(file, "Manual Export: $dataType")
                        file.delete()

                        statusMessage = if (success) "$dataType upload successful." else "Failed to upload $dataType."
                        Toast.makeText(context, statusMessage, Toast.LENGTH_LONG).show()

                    } catch (e: Exception) {
                        statusMessage = "Error during export: ${e.message}"
                        Toast.makeText(context, statusMessage, Toast.LENGTH_LONG).show()
                        e.printStackTrace()
                    }
                }
            }

            // Start the permission request chain
            permissionManager.requestPermission(mainPermission,
                onGranted = {
                    if (secondPermission != null) {
                        permissionManager.requestPermission(secondPermission,
                            onGranted = { proceedWithExport() }
                        )
                    } else {
                        proceedWithExport()
                    }
                }
            )
        }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Status: $statusMessage", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        // --- FCM Token Display Card ---
        Card(elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Your Device FCM Token", style = MaterialTheme.typography.titleMedium)
                Text("(Needed for the server)", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                SelectionContainer {
                    Text(fcmToken, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("FCM Token", fcmToken)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Token Copied!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Copy Token")
                }
            }
        }

        SectionHeader(title = "Manual Actions & CSV Export")

        Button(onClick = {
            statusMessage = "Requesting one-time location..."
            permissionManager.requestPermission(Manifest.permission.ACCESS_FINE_LOCATION) {
                // Trigger the DataFetchWorker directly for this manual action
                val workRequest = OneTimeWorkRequestBuilder<DataFetchWorker>()
                    .setInputData(Data.Builder().putString(DataFetchWorker.KEY_COMMAND, "get_location").build())
                    .build()
                WorkManager.getInstance(context).enqueue(workRequest)
                statusMessage = "One-time location request enqueued."
                Toast.makeText(context, statusMessage, Toast.LENGTH_SHORT).show()
            }
        }) {
            Text("Send Location Now")
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            exportDataAsCsv(
                "Call Logs",
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_CONTACTS,
                manualDataManager::getCallLogsAsCsv
            )
        }) {
            Text("Export Call Logs as CSV")
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            exportDataAsCsv(
                "SMS",
                Manifest.permission.READ_SMS,
                Manifest.permission.READ_CONTACTS,
                manualDataManager::getSmsAsCsv
            )
        }) {
            Text("Export SMS as CSV")
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            exportDataAsCsv(
                "Contacts",
                Manifest.permission.READ_CONTACTS,
                null, // No second permission needed
                manualDataManager::getContactsAsCsv
            )
        }) {
            Text("Export Contacts as CSV")
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Spacer(modifier = Modifier.height(24.dp))
    Divider()
    Spacer(modifier = Modifier.height(16.dp))
    Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(16.dp))
}

/**
 * Convenience extension function for [PermissionManager] to request a single permission
 * and handle the denied case with a Toast message.
 */
fun PermissionManager.requestPermission(permission: String, onGranted: () -> Unit) {
    this.requestPermission(
        permission = permission,
        onGranted = onGranted,
        onDenied = {
            val context = (this as? ComponentActivity)?.baseContext ?: return@requestPermission
            val permissionName = permission.substringAfterLast('.').replace("_", " ")
            Toast.makeText(
                context,
                "Permission for $permissionName was denied. The feature cannot proceed.",
                Toast.LENGTH_LONG
            ).show()
        }
    )
}
