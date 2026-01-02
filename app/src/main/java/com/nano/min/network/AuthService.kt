package com.nano.min.network

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    fun logout() {
        client.tokenStorage.setToken(null)
    }
}
