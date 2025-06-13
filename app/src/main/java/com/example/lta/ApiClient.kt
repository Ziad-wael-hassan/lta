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

/**
 * Data class to represent the JSON payload for device registration.
 * Now includes an optional 'name' field.
 */
data class DeviceRegistrationPayload(
    val token: String,
    val model: String,
    val deviceId: String,
    val name: String? = null
)

/**
 * Handles all network communication with the backend server.
 * Provides methods for device registration and file/text uploads.
 */
class ApiClient(private val baseUrl: String) {

    // Improved: Configured OkHttpClient with proper timeouts for better performance
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    // Improved: Made TAG a companion object constant following Kotlin conventions
    companion object {
        private const val TAG = "ApiClient"
        private const val MEDIA_TYPE_JSON = "application/json; charset=utf-8"
        private const val MEDIA_TYPE_CSV = "text/csv"
    }

    /**
     * Registers the device's FCM token, model, and unique ID with the server.
     * @param token The FCM registration token.
     * @param deviceModel The model name of the device.
     * @param deviceId The unique Android ID for the device.
     * @param name An optional, user-provided name for the device.
     * @return True if the registration was successful, false otherwise.
     */
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
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    when {
                        response.isSuccessful -> {
                            Log.i(TAG, "Device registered successfully with token, model, and ID.")
                            true
                        }
                        else -> {
                            // Improved: Better error logging with null-safe response body handling
                            val errorBody = response.body?.string() ?: "No error details"
                            Log.e(TAG, "Server Error during device registration: ${response.code} $errorBody")
                            false
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Network Error during device registration", e)
                false
            }
        }
    }

    /**
     * Uploads a simple text message to the server.
     * @param message The text message to upload.
     * @return True if the upload was successful, false otherwise.
     */
    suspend fun uploadText(message: String): Boolean {
        return withContext(Dispatchers.IO) {
            val url = "$baseUrl/api/upload"
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("message", message)
                .build()
            
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            executeRequest(request, "uploadText")
        }
    }

    /**
     * Uploads a file (like a CSV) with a caption to the server.
     * @param file The file to upload.
     * @param caption A caption describing the file.
     * @return True if the upload was successful, false otherwise.
     */
    suspend fun uploadFile(file: File, caption: String): Boolean {
        return withContext(Dispatchers.IO) {
            val url = "$baseUrl/api/upload"
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("caption", caption)
                .addFormDataPart(
                    "document", 
                    file.name, 
                    file.asRequestBody(MEDIA_TYPE_CSV.toMediaTypeOrNull())
                )
                .build()
            
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            executeRequest(request, "uploadFile")
        }
    }

    /**
     * Improved: Extracted common request execution logic to reduce code duplication
     * and improve maintainability.
     */
    private fun executeRequest(request: Request, operationName: String): Boolean {
        return try {
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        Log.d(TAG, "$operationName completed successfully")
                        true
                    }
                    else -> {
                        val errorBody = response.body?.string() ?: "No error details"
                        Log.e(TAG, "Server Error on $operationName: ${response.code} $errorBody")
                        false
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network Error on $operationName", e)
            false
        }
    }
}
