package com.nano.min.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val role: String = "USER"
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class LoginResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("token_type") val tokenType: String? = "Bearer",
    val expiresIn: Long? = null
)

@Serializable
data class MeResponse(
    val id: String? = null,
    val email: String? = null,
    val role: String? = null
)

