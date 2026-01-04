package com.nano.min.network

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class RegisterDeviceTokenRequest(
    val token: String,
    val platform: String = "android"
)

class AuthService(val client: ApiClient) {

    suspend fun register(email: String, password: String, role: String = "USER"): Boolean =
        withContext(Dispatchers.IO) {
            val req = RegisterRequest(email, password, role)
            val response: HttpResponse =
                client.httpClient.post("${client.baseUrl}/api/auth/register") {
                    setBody(req)
                }
            return@withContext response.status == HttpStatusCode.Created
        }

    suspend fun login(email: String, password: String): Boolean = withContext(Dispatchers.IO) {
        val req = LoginRequest(email, password)
        val response: HttpResponse = client.httpClient.post("${client.baseUrl}/api/auth/login") {
            setBody(req)
        }
        if (response.status == HttpStatusCode.OK) {
            val body: LoginResponse = response.body()
            val token = body.token
            if (!token.isNullOrBlank()) {
                client.tokenStorage.setToken(token)
                return@withContext true
            }
        }
        return@withContext false
    }

    suspend fun me(): MeResponse? = withContext(Dispatchers.IO) {
        val response: HttpResponse = client.httpClient.get("${client.baseUrl}/api/me")
        if (response.status == HttpStatusCode.OK) {
            return@withContext response.body()
        }
        return@withContext null
    }

    suspend fun updateProfile(username: String?, displayName: String?, bio: String?, avatarUrl: String?): MeResponse? = withContext(Dispatchers.IO) {
        val req = UpdateProfileRq(username, displayName, bio, avatarUrl)
        val response: HttpResponse = client.httpClient.patch("${client.baseUrl}/api/me") {
            setBody(req)
        }
        if (response.status == HttpStatusCode.OK) {
            return@withContext response.body()
        }
        return@withContext null
    }

    /**
     * Регистрирует FCM токен на сервере для push-уведомлений
     */
    suspend fun registerDeviceToken(fcmToken: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val response: HttpResponse = client.httpClient.post("${client.baseUrl}/api/device-token") {
                setBody(RegisterDeviceTokenRequest(fcmToken, "android"))
            }
            response.status == HttpStatusCode.OK
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Удаляет FCM токен с сервера (при логауте)
     */
    suspend fun removeDeviceToken(fcmToken: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val response: HttpResponse = client.httpClient.delete("${client.baseUrl}/api/device-token") {
                setBody(mapOf("token" to fcmToken))
            }
            response.status == HttpStatusCode.OK
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Запрос на сброс пароля - отправляет токен на email
     */
    suspend fun forgotPassword(email: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val response: HttpResponse = client.httpClient.post("${client.baseUrl}/api/auth/forgot") {
                setBody(mapOf("email" to email))
            }
            response.status == HttpStatusCode.OK
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Сброс пароля с использованием токена
     */
    suspend fun resetPassword(email: String, token: String, newPassword: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val response: HttpResponse = client.httpClient.post("${client.baseUrl}/api/auth/reset") {
                setBody(ResetPasswordRequest(email, token, newPassword))
            }
            response.status == HttpStatusCode.OK
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun logout() {
        client.tokenStorage.setToken(null)
    }
}

@Serializable
data class ResetPasswordRequest(
    val email: String,
    val token: String,
    val newPassword: String
)
