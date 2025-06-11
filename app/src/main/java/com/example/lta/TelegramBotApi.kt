package com.example.lta

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class TelegramBotApi(private val botToken: String, private val chatId: String) {

    private val client = OkHttpClient()

    fun sendMessage(message: String): Boolean {
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

        return try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }
} 