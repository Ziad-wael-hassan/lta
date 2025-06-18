package com.elfinsaddle.util

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
import com.elfinsaddle.data.local.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

data class BatteryStatus(val percentage: Int, val status: String)

class SystemInfoManager(private val context: Context) {

    companion object {
        private const val TAG = "SystemInfoManager"
        private const val PUBLIC_IP_SERVICE_URL = "https://api.ipify.org"
    }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    fun getDeviceId(): String {
        val appPrefs = AppPreferences(context)
        val savedId = appPrefs.getDeviceId()
        if (!savedId.isNullOrBlank()) {
            return savedId
        }

        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        if (androidId != "9774d56d682e549c" && !androidId.isNullOrBlank()) {
            appPrefs.saveDeviceId(androidId)
            return androidId
        }
        
        val deviceProps = buildString {
            append(Build.BOARD)
            append(Build.BRAND)
            append(Build.DEVICE)
            append(Build.HARDWARE)
            append(Build.MANUFACTURER)
            append(Build.MODEL)
            append(Build.PRODUCT)
            append(Build.DISPLAY)
        }
        
        val hashedId = deviceProps.hashCode().toString()
        appPrefs.saveDeviceId(hashedId)
        return hashedId
    }

    fun getDeviceModel(): String = Build.MODEL

    fun getNetworkType(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return "Error"
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        return when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WiFi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            else -> "No Network"
        }
    }

    fun getBatteryStatus(): BatteryStatus {
        val iFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = context.registerReceiver(null, iFilter)
        
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        
        val batteryPct = if (level == -1 || scale == -1 || scale == 0) -1 else (level * 100 / scale.toFloat()).toInt()

        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val statusString = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
            else -> "Unknown"
        }
        return BatteryStatus(batteryPct, statusString)
    }

    suspend fun getPublicIpAddress(): String = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(PUBLIC_IP_SERVICE_URL).build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string()?.trim() ?: "No Response" else "Service Error"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting public IP address", e)
            "Unavailable"
        }
    }
}
