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
        try {
            val response: HttpResponse = client.httpClient.get("${client.baseUrl}/api/me")
            android.util.Log.d("AuthService", "me() response status: ${response.status}")
            if (response.status == HttpStatusCode.OK) {
                return@withContext response.body()
            }
            android.util.Log.e("AuthService", "me() failed with status: ${response.status}")
            return@withContext null
        } catch (e: Exception) {
            android.util.Log.e("AuthService", "me() exception: ${e.message}", e)
            return@withContext null
        }
    }

    suspend fun updateProfile(username: String?, displayName: String?, bio: String?): MeResponse? = withContext(Dispatchers.IO) {
        val req = UpdateProfileRq(username, displayName, bio)
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

    /**
     * Прямой сброс пароля (без токена из письма)
     */
    suspend fun directResetPassword(email: String, newPassword: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val response: HttpResponse = client.httpClient.post("${client.baseUrl}/api/auth/direct-reset") {
                setBody(DirectResetRequest(email, newPassword))
            }
            response.status == HttpStatusCode.OK
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Запрос кода сброса пароля на email
     */
    suspend fun requestResetCode(email: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val response: HttpResponse = client.httpClient.post("${client.baseUrl}/api/auth/request-code") {
                setBody(RequestCodeRequest(email))
            }
            response.status == HttpStatusCode.OK
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Проверка кода сброса
     */
    suspend fun verifyResetCode(email: String, code: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val response: VerifyCodeResponse = client.httpClient.post("${client.baseUrl}/api/auth/verify-code") {
                setBody(VerifyCodeRequest(email, code))
            }.body()
            response.valid
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Сброс пароля с кодом
     */
    suspend fun resetPasswordWithCode(email: String, code: String, newPassword: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val response: ResetWithCodeResponse = client.httpClient.post("${client.baseUrl}/api/auth/reset-with-code") {
                setBody(ResetWithCodeRequest(email, code, newPassword))
            }.body()
            response.success
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

@Serializable
data class DirectResetRequest(
    val email: String,
    val newPassword: String
)

@Serializable
data class RequestCodeRequest(val email: String)

@Serializable
data class VerifyCodeRequest(val email: String, val code: String)

@Serializable
data class VerifyCodeResponse(val valid: Boolean)

@Serializable
data class ResetWithCodeRequest(val email: String, val code: String, val newPassword: String)

@Serializable
data class ResetWithCodeResponse(val success: Boolean, val message: String? = null)
