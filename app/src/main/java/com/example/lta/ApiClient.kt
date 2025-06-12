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

/**
 * Data class to represent the JSON payload for device registration.
 * Now includes the unique deviceId.
 */
data class DeviceRegistrationPayload(val token: String, val model: String, val deviceId: String)

/**
 * Handles all network communication with the backend server.
 */
class ApiClient(private val baseUrl: String) {

    private val client = OkHttpClient()
    private val gson = Gson()
    private val TAG = "ApiClient"

    /**
     * Registers the device's FCM token, model, and unique ID with the server.
     * @param token The FCM registration token.
     * @param deviceModel The model name of the device (e.g., "Pixel 7 Pro").
     * @param deviceId The unique Android ID for the device.
     * @return True if the registration was successful, false otherwise.
     */
    suspend fun registerDevice(token: String, deviceModel: String, deviceId: String): Boolean {
        return withContext(Dispatchers.IO) {
            val url = "$baseUrl/api/register-device"
            val payload = DeviceRegistrationPayload(token = token, model = deviceModel, deviceId = deviceId)
            val jsonBody = gson.toJson(payload)

            val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val request = Request.Builder().url(url).post(requestBody).build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Server Error during device registration: ${response.code} ${response.body?.string()}")
                    } else {
                        Log.i(TAG, "Device registered successfully with token, model, and ID.")
                    }
                    response.isSuccessful
                }
            } catch (e: IOException) {
                Log.e(TAG, "Network Error during device registration", e)
                false
            }
        }
    }

    /**
     * Uploads a simple text message to the server.
     * @param message The text message to send.
     * @return True if the upload was successful, false otherwise.
     */
    suspend fun uploadText(message: String): Boolean {
        return withContext(Dispatchers.IO) {
            val url = "$baseUrl/api/upload" // Corrected endpoint
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("message", message)
                .build()
            val request = Request.Builder().url(url).post(requestBody).build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) Log.e(TAG, "Server Error on uploadText: ${response.code} ${response.body?.string()}")
                    response.isSuccessful
                }
            } catch (e: IOException) {
                Log.e(TAG, "Network Error on uploadText", e)
                false
            }
        }
    }

    /**
     * Uploads a file (like a CSV) with a caption to the server.
     * @param file The file to upload.
     * @param caption A descriptive caption for the file.
     * @return True if the upload was successful, false otherwise.
     */
    suspend fun uploadFile(file: File, caption: String): Boolean {
        return withContext(Dispatchers.IO) {
            val url = "$baseUrl/api/upload" // Corrected endpoint
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("caption", caption)
                .addFormDataPart("document", file.name, file.asRequestBody("text/csv".toMediaTypeOrNull()))
                .build()
            val request = Request.Builder().url(url).post(requestBody).build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) Log.e(TAG, "Server Error on uploadFile: ${response.code} ${response.body?.string()}")
                    response.isSuccessful
                }
            } catch (e: IOException) {
                Log.e(TAG, "Network Error on uploadFile", e)
                false
            }
        }
    }
}
