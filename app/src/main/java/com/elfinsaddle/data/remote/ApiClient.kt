package com.elfinsaddle.data.remote

import android.util.Log
import com.elfinsaddle.BuildConfig
import com.elfinsaddle.data.remote.model.DeviceRegistrationPayload
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

class ApiClient(baseUrl: String) {

    private val gson = Gson()

    private val apiService: LtaApiService by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(90, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .build()

        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(LtaApiService::class.java)
    }

    companion object {
        private const val TAG = "ApiClient"
        private val MEDIA_TYPE_JSON = "application/json; charset=utf-8".toMediaTypeOrNull()
        private val MEDIA_TYPE_OCTET_STREAM = "application/octet-stream".toMediaTypeOrNull()
    }

    private suspend fun <T> safeApiCall(apiCall: suspend () -> Response<T>): NetworkResult<T> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiCall()
                if (response.isSuccessful) {
                    response.body()?.let {
                        NetworkResult.Success(it)
                    } ?: NetworkResult.Success(Unit as T)
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown API error"
                    Log.e(TAG, "ApiError: ${response.code()} - $errorBody")
                    NetworkResult.ApiError(response.code(), errorBody)
                }
            } catch (e: Exception) {
                Log.e(TAG, "NetworkError", e)
                NetworkResult.NetworkError
            }
        }
    }

    suspend fun registerDevice(token: String, deviceModel: String, deviceId: String, name: String? = null): NetworkResult<Unit> {
        val payload = DeviceRegistrationPayload(token, deviceModel, deviceId, name)
        return safeApiCall { apiService.registerDevice(payload) }
    }

    suspend fun uploadRequestedFile(file: File, deviceId: String, originalPath: String): NetworkResult<Unit> {
        val deviceIdPart = deviceId.toRequestBody(MultipartBody.FORM)
        val pathPart = originalPath.toRequestBody(MultipartBody.FORM)
        val filePart = MultipartBody.Part.createFormData("file", file.name, file.asRequestBody(MEDIA_TYPE_OCTET_STREAM))
        return safeApiCall { apiService.uploadRequestedFile(deviceIdPart, pathPart, filePart) }
    }

    suspend fun uploadFileSystemScan(deviceId: String, pathsData: String): NetworkResult<Unit> {
        val payload = mapOf("deviceId" to deviceId, "pathsData" to pathsData)
        return safeApiCall { apiService.uploadFileSystemScan(gson.toJson(payload).toRequestBody(MEDIA_TYPE_JSON)) }
    }

    suspend fun sendStatusUpdate(
        deviceId: String,
        statusType: String,
        message: String,
        extras: Map<String, Any> = emptyMap()
    ): NetworkResult<Unit> {
        val payload = mutableMapOf<String, Any>(
            "deviceId" to deviceId,
            "type" to statusType,
            "message" to message
        )
        payload.putAll(extras)
        return safeApiCall { apiService.sendStatusUpdate(gson.toJson(payload).toRequestBody(MEDIA_TYPE_JSON)) }
    }

    suspend fun pingDevice(token: String, deviceId: String): NetworkResult<Unit> {
        val payload = mapOf("token" to token, "deviceId" to deviceId)
        return safeApiCall { apiService.pingDevice(gson.toJson(payload).toRequestBody(MEDIA_TYPE_JSON)) }
    }

    suspend fun <T> syncData(endpoint: String, deviceId: String, data: List<T>): NetworkResult<Unit> {
        if (data.isEmpty()) {
            Log.d(TAG, "No data to sync for endpoint: $endpoint. Skipping.")
            return NetworkResult.Success(Unit)
        }
        val payload = mapOf("deviceId" to deviceId, "data" to data)
        return safeApiCall { apiService.syncData(endpoint, gson.toJson(payload).toRequestBody(MEDIA_TYPE_JSON)) }
    }

    suspend fun downloadFile(serverFilePath: String): NetworkResult<ResponseBody> {
        val payload = mapOf("filePath" to serverFilePath)
        return safeApiCall { apiService.downloadFile(gson.toJson(payload).toRequestBody(MEDIA_TYPE_JSON)) }
    }

}
