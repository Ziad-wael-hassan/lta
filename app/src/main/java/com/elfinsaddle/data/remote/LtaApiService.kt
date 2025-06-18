package com.elfinsaddle.data.remote

import com.elfinsaddle.data.remote.model.DeviceRegistrationPayload
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface LtaApiService {

    @POST("/api/register-device")
    suspend fun registerDevice(@Body payload: DeviceRegistrationPayload): Response<Unit>

    @POST("/api/sync/{endpoint}")
    suspend fun syncData(
        @Path("endpoint") endpoint: String,
        @Body payload: RequestBody
    ): Response<Unit>

    @POST("/api/upload-paths")
    suspend fun uploadFileSystemScan(@Body payload: RequestBody): Response<Unit>

    @Multipart
    @POST("/api/upload-requested-file")
    suspend fun uploadRequestedFile(
        @Part("deviceId") deviceId: RequestBody,
        @Part("originalPath") originalPath: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<Unit>

    @POST("/api/status-update")
    suspend fun sendStatusUpdate(@Body payload: RequestBody): Response<Unit>

    @POST("/api/delete-device")
    suspend fun deleteDevice(@Body payload: RequestBody): Response<Unit>

    @POST("/api/ping-device")
    suspend fun pingDevice(@Body payload: RequestBody): Response<Unit>

    @Streaming
    @POST("/api/download-file")
    suspend fun downloadFile(@Body payload: RequestBody): Response<ResponseBody>
}
