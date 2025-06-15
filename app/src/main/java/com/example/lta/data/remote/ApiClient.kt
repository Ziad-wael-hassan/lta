package com.example.lta.data.remote

import android.util.Log
import com.example.lta.data.remote.model.DeviceRegistrationPayload
import com.example.lta.util.FileSystemScanResult
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiClient(private val baseUrl: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    companion object {
        private const val TAG = "ApiClient"
        private val MEDIA_TYPE_JSON = "application/json; charset=utf-8".toMediaTypeOrNull()
        private val MEDIA_TYPE_OCTET_STREAM = "application/octet-stream".toMediaTypeOrNull()
    }

    suspend fun registerDevice(token: String, deviceModel: String, deviceId: String, name: String? = null): Boolean {
        val payload = DeviceRegistrationPayload(token, deviceModel, deviceId, name)
        return executePostRequest("$baseUrl/api/register-device", payload, "registerDevice")
    }

    suspend fun uploadRequestedFile(file: File, deviceId: String, originalPath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("deviceId", deviceId)
                    .addFormDataPart("originalPath", originalPath)
                    .addFormDataPart("file", file.name, file.asRequestBody(MEDIA_TYPE_OCTET_STREAM))
                    .build()
                val request = Request.Builder().url("$baseUrl/api/upload-requested-file").post(requestBody).build()
                executeRequest(request, "uploadRequestedFile")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to build multipart request for uploadRequestedFile", e)
                false
            }
        }
    }

    // In ApiClient.kt
suspend fun uploadFileSystemScan(deviceId: String, pathsData: String): Boolean { // <-- Change parameter
    // The server expects a key 'pathsData' with a raw string value, not a complex object.
    val payload = mapOf("deviceId" to deviceId, "pathsData" to pathsData) 
    // The URL must match the server's endpoint.
    return executePostRequest("$baseUrl/api/upload-paths", payload, "uploadFileSystemScan") 
}

    suspend fun sendStatusUpdate(deviceId: String, statusType: String, message: String): Boolean {
        val payload = mapOf("deviceId" to deviceId, "type" to statusType, "message" to message)
        return executePostRequest("$baseUrl/api/status-update", payload, "sendStatusUpdate")
    }

    suspend fun deleteDevice(deviceId: String): Boolean {
        val payload = mapOf("deviceId" to deviceId)
        return executePostRequest("$baseUrl/api/delete-device", payload, "deleteDevice")
    }

    suspend fun pingDevice(token: String, deviceId: String): Boolean {
        val payload = mapOf("token" to token, "deviceId" to deviceId)
        return executePostRequest("$baseUrl/api/ping-device", payload, "pingDevice")
    }
    
    suspend fun downloadFile(serverFilePath: String): ResponseBody? = withContext(Dispatchers.IO) {
        val payload = mapOf("filePath" to serverFilePath)
        val requestBody = gson.toJson(payload).toRequestBody(MEDIA_TYPE_JSON)
        val request = Request.Builder().url("$baseUrl/api/download-file").post(requestBody).build()

        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) response.body else {
                Log.e(TAG, "Server error downloading file: ${response.code} - ${response.message}")
                response.close()
                null
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error downloading file", e)
            null
        }
    }

    suspend fun <T> syncData(endpoint: String, deviceId: String, data: List<T>): Boolean {
        if (data.isEmpty()) {
            Log.d(TAG, "No data to sync for endpoint: $endpoint. Skipping.")
            return true
        }
        val url = "$baseUrl/api/sync/$endpoint"
        val payload = mapOf("deviceId" to deviceId, "data" to data)
        return executePostRequest(url, payload, "syncData ($endpoint)")
    }

    private suspend fun executePostRequest(url: String, payload: Any, operationName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val jsonBody = gson.toJson(payload)
                val requestBody = jsonBody.toRequestBody(MEDIA_TYPE_JSON)
                val request = Request.Builder().url(url).post(requestBody).build()
                executeRequest(request, operationName)
            } catch (e: Exception) {
                Log.e(TAG, "Error creating request for $operationName", e)
                false
            }
        }
    }

    private fun executeRequest(request: Request, operationName: String): Boolean {
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "No error details"
                    Log.e(TAG, "Server Error on $operationName: ${response.code} - $errorBody")
                }
                return response.isSuccessful
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network Error on $operationName", e)
            return false
        }
    }
}
