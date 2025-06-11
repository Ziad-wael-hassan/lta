// TelegramBotApi.kt
package com.example.lta

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class TelegramBotApi(private val botToken: String, private val chatId: String) {

    private val client = OkHttpClient()

    // FIXED: Made this a suspend function to allow it to be called from coroutines safely.
    suspend fun sendMessage(message: String): Boolean {
        // FIXED: Switched to the IO dispatcher for the network call.
        return withContext(Dispatchers.IO) {
            val url = "https://api.telegram.org/bot${botToken}/sendMessage"
            val json = JSONObject().apply {
                put("chat_id", chatId)
                put("text", message)
            }
            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            try {
                // The .execute() call is now safely on a background thread.
                client.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            } catch (e: IOException) {
                e.printStackTrace()
                false
            }
        }
    }
}
