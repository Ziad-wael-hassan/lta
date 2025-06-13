// SystemInfoManager.kt
package com.example.lta

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.telephony.*
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

/**
 * Data class to hold battery information in a structured way.
 */
data class BatteryStatus(val percentage: Int, val status: String)

/**
 * Manages system information retrieval including device details, network status,
 * battery information, and cellular tower data.
 */
class SystemInfoManager(private val context: Context) {

    companion object {
        private const val TAG = "SystemInfoManager"
        private const val PUBLIC_IP_SERVICE_URL = "https://api.ipify.org"
        private const val HTTP_TIMEOUT_SECONDS = 10L
        
        // Battery status constants for better readability
        private const val BATTERY_UNAVAILABLE = -1
        private const val BATTERY_SCALE_ZERO_GUARD = 0
    }

    // Improved: Configured OkHttpClient with proper timeouts and optimizations
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * Gets the unique Android device identifier.
     * @return The Android ID for this device
     */
    fun getDeviceId(): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    /**
     * Gets the device model name.
     * @return The device model (e.g., "Pixel 6", "Galaxy S21")
     */
    fun getDeviceModel(): String {
        return Build.MODEL
    }

    /**
     * Determines the current network connection type.
     * @return "WiFi", "Cellular", or "No Network"
     */
    fun getNetworkType(): String {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)

            when {
                networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WiFi"
                networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular"
                networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
                else -> "No Network"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting network type", e)
            "Error"
        }
    }

    /**
     * Fetches the current battery percentage and charging status.
     * @return BatteryStatus containing percentage and status string
     */
    fun getBatteryStatus(): BatteryStatus {
        return try {
            val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
                context.registerReceiver(null, filter)
            }
            
            if (batteryStatus == null) {
                return BatteryStatus(BATTERY_UNAVAILABLE, "Unavailable")
            }

            val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, BATTERY_UNAVAILABLE)
            val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, BATTERY_UNAVAILABLE)
            
            // Improved: Better calculation with proper error handling
            val batteryPct = when {
                level == BATTERY_UNAVAILABLE || scale == BATTERY_UNAVAILABLE || scale == BATTERY_SCALE_ZERO_GUARD -> BATTERY_UNAVAILABLE
                else -> (level * 100 / scale.toFloat()).toInt()
            }

            val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, BATTERY_UNAVAILABLE)
            val statusString = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                BatteryManager.BATTERY_STATUS_FULL -> "Full"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
                else -> "Unknown"
            }
            
            BatteryStatus(batteryPct, statusString)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting battery status", e)
            BatteryStatus(BATTERY_UNAVAILABLE, "Error")
        }
    }

    /**
     * Gets the device's local IP address (typically on WiFi).
     * @return The local IP address or appropriate error message
     */
    fun getLocalIpAddress(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            
            // Improved: Use sequence for better performance and readability
            interfaces.asSequence()
                .flatMap { networkInterface -> 
                    networkInterface.inetAddresses.asSequence() 
                }
                .firstOrNull { address ->
                    !address.isLoopbackAddress && address.isSiteLocalAddress
                }
                ?.hostAddress ?: "Not Found"
                
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local IP address", e)
            "Error"
        }
    }

    /**
     * Gets the device's public IP by querying an external service.
     * This performs a network call and should be called from a coroutine.
     * @return The public IP address or appropriate error message
     */
    suspend fun getPublicIpAddress(): String {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(PUBLIC_IP_SERVICE_URL)
                    .build()
                
                httpClient.newCall(request).execute().use { response ->
                    when {
                        response.isSuccessful -> {
                            response.body?.string()?.trim() ?: "No Response"
                        }
                        else -> {
                            Log.w(TAG, "Public IP service returned error: ${response.code}")
                            "Service Error"
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting public IP address", e)
                "Unavailable"
            }
        }
    }

    /**
     * Gets information about the currently connected cell tower and signal strength.
     * Requires ACCESS_FINE_LOCATION permission.
     * @return Cell tower information string or appropriate error message
     */
    @SuppressLint("MissingPermission")
    fun getCellTowerInfo(): String {
        // Check for required permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return "Permission Missing"
        }

        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val allCellInfo = telephonyManager.allCellInfo

            if (allCellInfo.isNullOrEmpty()) {
                return "Unavailable"
            }

            // Find the first cell that the device is currently registered to
            val registeredCell = allCellInfo.firstOrNull { it.isRegistered }
                ?: return "Not Registered"

            // Improved: More comprehensive cell type handling with better formatting
            when (registeredCell) {
                is CellInfoLte -> {
                    val identity = registeredCell.cellIdentity
                    val strength = registeredCell.cellSignalStrength
                    "LTE | CI: ${identity.ci}, TAC: ${identity.tac}, RSRP: ${strength.rsrp} dBm, RSRQ: ${strength.rsrq} dB"
                }
                is CellInfoGsm -> {
                    val identity = registeredCell.cellIdentity
                    val strength = registeredCell.cellSignalStrength
                    "GSM | LAC: ${identity.lac}, CID: ${identity.cid}, RSSI: ${strength.dbm} dBm"
                }
                is CellInfoWcdma -> {
                    val identity = registeredCell.cellIdentity
                    val strength = registeredCell.cellSignalStrength
                    "WCDMA | LAC: ${identity.lac}, CID: ${identity.cid}, RSCP: ${strength.dbm} dBm"
                }
                is CellInfoNr -> { // 5G
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val identity = registeredCell.cellIdentity as CellIdentityNr
                        val strength = registeredCell.cellSignalStrength as CellSignalStrengthNr
                        "5G NR | NCI: ${identity.nci}, RSRP: ${strength.ssRsrp} dBm, RSRQ: ${strength.ssRsrq} dB"
                    } else {
                        "5G NR | Details unavailable on API < 29"
                    }
                }
                else -> "Unknown Cell Type: ${registeredCell.javaClass.simpleName}"
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting cell tower info", e)
            "Error"
        }
    }

    /**
     * Improved: Clean up resources when no longer needed
     */
    fun cleanup() {
        try {
            httpClient.dispatcher.executorService.shutdown()
            httpClient.connectionPool.evictAll()
        } catch (e: Exception) {
            Log.w(TAG, "Error during cleanup", e)
        }
    }
}
