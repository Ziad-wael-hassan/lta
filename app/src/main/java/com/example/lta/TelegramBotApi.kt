// TelegramBotApi.kt
package com.example.lta

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException

class TelegramBotApi(private val botToken: String, private val chatId: String) {

    private val client = OkHttpClient()

    suspend fun sendMessage(message: String): Boolean {
        return withContext(Dispatchers.IO) {
            val url = "https://api.telegram.org/bot$botToken/sendMessage"
            val json = JSONObject().apply {
                put("chat_id", chatId)
                put("text", message)
            }
            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val request = Request.Builder().url(url).post(body).build()

            try {
                client.newCall(request).execute().use { response -> response.isSuccessful }
            } catch (e: IOException) {
                e.printStackTrace()
                false
            }
        }
    }

    // NEW: Function to send a file as a document
    suspend fun sendDocument(file: File, caption: String = ""): Boolean {
        return withContext(Dispatchers.IO) {
            val url = "https://api.telegram.org/bot$botToken/sendDocument"

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId)
                .addFormDataPart("document", file.name, file.asRequestBody("text/csv".toMediaTypeOrNull()))
                .addFormDataPart("caption", caption)
                .build()

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        // Log error response for debugging
                        println("Telegram API Error: ${response.body?.string()}")
                    }
                    response.isSuccessful
                }
            } catch (e: IOException) {
                e.printStackTrace()
                false
            }
        }
    }
}
