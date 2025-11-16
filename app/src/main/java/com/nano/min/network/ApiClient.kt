package com.nano.min.network

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.Json

/**
 * Simple Ktor HttpClient provider configured for JSON and logging.
 * Token is injected via [TokenStorage] when requests are made.
 */
class ApiClient(val baseUrl: String = "https://minimum.illegalfiles.icu", val tokenStorage: TokenStorage) {
    val httpClient: HttpClient by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                })
            }
            install(Logging) {
                logger = Logger.ANDROID
                level = LogLevel.BODY
            }
            defaultRequest {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                val token = runCatching { tokenStorage.getToken() }.getOrNull()
                if (!token.isNullOrBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            }
        }
    }
}
