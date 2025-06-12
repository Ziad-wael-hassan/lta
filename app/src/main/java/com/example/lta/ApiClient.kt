package com.example.lta

import android.util.Log
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

class ApiClient(private val baseUrl: String) {

    private val client = OkHttpClient()
    private val TAG = "ApiClient"

    suspend fun uploadText(message: String): Boolean {
        return withContext(Dispatchers.IO) {
            val url = "$baseUrl/upload-data"
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("message", message)
                .build()
            val request = Request.Builder().url(url).post(requestBody).build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) Log.e(TAG, "Server Error: ${response.body?.string()}")
                    response.isSuccessful
                }
            } catch (e: IOException) {
                Log.e(TAG, "Network Error", e)
                false
            }
        }
    }

    suspend fun uploadFile(file: File, caption: String): Boolean {
        return withContext(Dispatchers.IO) {
            val url = "$baseUrl/upload-data"
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("caption", caption)
                .addFormDataPart("document", file.name, file.asRequestBody("text/csv".toMediaTypeOrNull()))
                .build()
            val request = Request.Builder().url(url).post(requestBody).build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) Log.e(TAG, "Server Error: ${response.body?.string()}")
                    response.isSuccessful
                }
            } catch (e: IOException) {
                Log.e(TAG, "Network Error", e)
                false
            }
        }
    }
}
