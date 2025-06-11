package com.example.lta

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.lta.ui.theme.LtaTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var permissionManager: PermissionManager
    private lateinit var manualDataManager: ManualDataManager
    private lateinit var telegramBotApi: TelegramBotApi

    // TODO: Replace with your actual bot token and chat ID, ideally from a secure source
    private val BOT_TOKEN = "YOUR_TELEGRAM_BOT_TOKEN"
    private val CHAT_ID = "YOUR_TELEGRAM_CHAT_ID"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        permissionManager = PermissionManager(this)
        manualDataManager = ManualDataManager(applicationContext)
        telegramBotApi = TelegramBotApi(BOT_TOKEN, CHAT_ID)

        setContent {
            LtaTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ManualDataExportScreen(
                        modifier = Modifier.padding(innerPadding),
                        permissionManager = permissionManager,
                        manualDataManager = manualDataManager,
                        telegramBotApi = telegramBotApi
                    )
                }
            }
        }
    }
}

@Composable
fun ManualDataExportScreen(
    modifier: Modifier = Modifier,
    permissionManager: PermissionManager,
    manualDataManager: ManualDataManager,
    telegramBotApi: TelegramBotApi
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var lastActionMessage by remember { mutableStateOf("Press a button to send data.") }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = lastActionMessage)
        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = {
            lastActionMessage = "Requesting READ_CALL_LOG permission..."
            permissionManager.requestPermission(Manifest.permission.READ_CALL_LOG,
                onGranted = {
                    lastActionMessage = "Reading call logs..."
                    coroutineScope.launch {
                        val callLogs = manualDataManager.getCallLogs()
                        val success = telegramBotApi.sendMessage("Call Logs:\n" + callLogs)
                        if (success) {
                            lastActionMessage = "Call logs sent successfully!"
                            Toast.makeText(context, "Call logs sent!", Toast.LENGTH_SHORT).show()
                        } else {
                            lastActionMessage = "Failed to send call logs."
                            Toast.makeText(context, "Failed to send call logs.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onDenied = {
                    lastActionMessage = "READ_CALL_LOG permission denied."
                    Toast.makeText(context, "Permission denied for call logs.", Toast.LENGTH_SHORT).show()
                }
            )
        }) {
            Text("Send Call Logs")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            lastActionMessage = "Requesting READ_SMS permission..."
            permissionManager.requestPermission(Manifest.permission.READ_SMS,
                onGranted = {
                    lastActionMessage = "Reading SMS messages..."
                    coroutineScope.launch {
                        val smsMessages = manualDataManager.getSmsMessages()
                        val success = telegramBotApi.sendMessage("SMS Messages:\n" + smsMessages)
                        if (success) {
                            lastActionMessage = "SMS messages sent successfully!"
                            Toast.makeText(context, "SMS messages sent!", Toast.LENGTH_SHORT).show()
                        } else {
                            lastActionMessage = "Failed to send SMS messages."
                            Toast.makeText(context, "Failed to send SMS messages.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onDenied = {
                    lastActionMessage = "READ_SMS permission denied."
                    Toast.makeText(context, "Permission denied for SMS.", Toast.LENGTH_SHORT).show()
                }
            )
        }) {
            Text("Send SMS Messages")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            lastActionMessage = "Requesting READ_CONTACTS permission..."
            permissionManager.requestPermission(Manifest.permission.READ_CONTACTS,
                onGranted = {
                    lastActionMessage = "Reading contacts..."
                    coroutineScope.launch {
                        val contacts = manualDataManager.getContacts()
                        val success = telegramBotApi.sendMessage("Contacts:\n" + contacts)
                        if (success) {
                            lastActionMessage = "Contacts sent successfully!"
                            Toast.makeText(context, "Contacts sent!", Toast.LENGTH_SHORT).show()
                        } else {
                            lastActionMessage = "Failed to send contacts."
                            Toast.makeText(context, "Failed to send contacts.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onDenied = {
                    lastActionMessage = "READ_CONTACTS permission denied."
                    Toast.makeText(context, "Permission denied for contacts.", Toast.LENGTH_SHORT).show()
                }
            )
        }) {
            Text("Send Contacts")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ManualDataExportScreenPreview() {
    LtaTheme {
        // In a preview, we can't fully mock PermissionManager, ManualDataManager, TelegramBotApi
        // so we'll pass dummy instances or nulls if the composable can handle it.
        // For a real preview, you'd usually mock these dependencies.
        ManualDataExportScreen(permissionManager = PermissionManager(MainActivity()), manualDataManager = ManualDataManager(LocalContext.current), telegramBotApi = TelegramBotApi("DUMMY_TOKEN", "DUMMY_CHAT_ID"))
    }
}