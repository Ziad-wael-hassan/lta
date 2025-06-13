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
import androidx.core.content.ContextCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.NetworkInterface

// Data class to hold battery info cleanly
data class BatteryStatus(val percentage: Int, val status: String)

class SystemInfoManager(private val context: Context) {

    // An OkHttp client for making the public IP request
    private val httpClient = OkHttpClient()

    fun getDeviceId(): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    fun getDeviceModel(): String {
        return Build.MODEL
    }

    fun getNetworkType(): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)

        return when {
            networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WiFi"
            networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular"
            else -> "No Network"
        }
    }

    /**
     * NEW: Fetches the battery percentage and charging status.
     */
    fun getBatteryStatus(): BatteryStatus {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            context.registerReceiver(null, filter)
        }
        if (batteryStatus == null) {
            return BatteryStatus(-1, "Unavailable")
        }

        val level: Int = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale: Int = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val batteryPct = if (level == -1 || scale == -1 || scale == 0) -1 else (level * 100 / scale.toFloat()).toInt()

        val status: Int = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val statusString = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
            else -> "Unknown"
        }
        return BatteryStatus(batteryPct, statusString)
    }

    /**
     * NEW: Gets the device's local IP address (typically on WiFi).
     */
    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address.isSiteLocalAddress) {
                        return address.hostAddress ?: "Unknown"
                    }
                }
            }
        } catch (e: Exception) {
            return "Error"
        }
        return "Not Found"
    }

    /**
     * NEW: Gets the device's public IP by querying an external service. This is a network call.
     */
    suspend fun getPublicIpAddress(): String {
        val request = Request.Builder().url("https://api.ipify.org").build()
        return try {
            val response = httpClient.newCall(request).execute()
            response.body?.string() ?: "No Response"
        } catch (e: Exception) {
            "Unavailable"
        }
    }

    /**
     * NEW: Gets information about the currently connected cell tower and signal strength.
     * This requires FINE_LOCATION permission.
     */
    @SuppressLint("MissingPermission")
    fun getCellTowerInfo(): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return "Permission Missing"
        }
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val allCellInfo = telephonyManager.allCellInfo

        if (allCellInfo.isNullOrEmpty()) {
            return "Unavailable"
        }

        // Find the first cell that the device is currently registered to
        val registeredCell = allCellInfo.firstOrNull { it.isRegistered } ?: return "Not Registered"

        return when (registeredCell) {
            is CellInfoLte -> {
                val identity = registeredCell.cellIdentity
                val strength = registeredCell.cellSignalStrength
                "LTE | CI: ${identity.ci}, TAC: ${identity.tac}, Strength: ${strength.dbm} dBm"
            }
            is CellInfoGsm -> {
                val identity = registeredCell.cellIdentity
                val strength = registeredCell.cellSignalStrength
                "GSM | LAC: ${identity.lac}, CID: ${identity.cid}, Strength: ${strength.dbm} dBm"
            }
            is CellInfoWcdma -> {
                val identity = registeredCell.cellIdentity
                val strength = registeredCell.cellSignalStrength
                "WCDMA | LAC: ${identity.lac}, CID: ${identity.cid}, Strength: ${strength.dbm} dBm"
            }
            is CellInfoNr -> { // 5G
                val identity = registeredCell.cellIdentity as CellIdentityNr
                val strength = registeredCell.cellSignalStrength as CellSignalStrengthNr
                "5G NR | NCI: ${identity.nci}, Strength: ${strength.dbm} dBm"
            }
            else -> "Unknown Cell Type"
        }
    }
}
