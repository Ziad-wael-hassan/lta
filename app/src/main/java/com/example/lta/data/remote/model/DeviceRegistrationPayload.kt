package com.example.lta.data.remote.model

data class DeviceRegistrationPayload(
    val token: String,
    val model: String,
    val deviceId: String,
    val name: String? = null
)
