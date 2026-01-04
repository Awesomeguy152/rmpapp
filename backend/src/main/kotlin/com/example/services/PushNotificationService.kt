package com.example.services

import com.google.auth.oauth2.GoogleCredentials
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.io.File
import java.io.FileInputStream
import java.util.UUID

/**
 * Сервис для отправки push-уведомлений через Firebase Cloud Messaging
 */
class PushNotificationService {
    
    private val deviceTokenService = DeviceTokenService()
    private val httpClient = HttpClient.newHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    
    // Путь к файлу Service Account (должен быть в корне проекта)
    private val serviceAccountPath = System.getenv("FIREBASE_SERVICE_ACCOUNT_PATH") 
        ?: "firebase-service-account.json"
    
    private var cachedCredentials: GoogleCredentials? = null
    
    private val projectId: String by lazy {
        try {
            val file = File(serviceAccountPath)
            if (file.exists()) {
                val jsonContent = json.parseToJsonElement(file.readText()).jsonObject
                jsonContent["project_id"]?.jsonPrimitive?.content ?: "rmp-messanger"
            } else {
                System.err.println("⚠️ Firebase Service Account file not found: $serviceAccountPath")
                "rmp-messanger"
            }
        } catch (e: Exception) {
            System.err.println("⚠️ Error reading Firebase config: ${e.message}")
            "rmp-messanger"
        }
    }

    /**
     * Отправляет push-уведомление конкретному пользователю
     */
    fun sendToUser(
        userId: UUID,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ): Boolean {
        val tokens = deviceTokenService.getTokensForUser(userId)
        if (tokens.isEmpty()) {
            println("📱 No device tokens for user $userId")
            return false
        }
        
        return tokens.all { token ->
            sendToToken(token, title, body, data)
        }
    }

    /**
     * Отправляет push-уведомление списку пользователей
     */
    fun sendToUsers(
        userIds: List<UUID>,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ): Int {
        val tokensByUser = deviceTokenService.getTokensForUsers(userIds)
        var successCount = 0
        
        tokensByUser.values.flatten().forEach { token ->
            if (sendToToken(token, title, body, data)) {
                successCount++
            }
        }
        
        return successCount
    }

    /**
     * Отправляет push на конкретный FCM токен
     */
    private fun sendToToken(
        token: String,
        title: String,
        body: String,
        data: Map<String, String>
    ): Boolean {
        return try {
            val accessToken = getAccessToken() ?: run {
                println("⚠️ Failed to get Firebase access token")
                return false
            }
            
            val message = buildJsonObject {
                put("message", buildJsonObject {
                    put("token", token)
                    put("notification", buildJsonObject {
                        put("title", title)
                        put("body", body)
                    })
                    if (data.isNotEmpty()) {
                        put("data", buildJsonObject {
                            data.forEach { (k, v) -> put(k, v) }
                        })
                    }
                    // Android specific config
                    put("android", buildJsonObject {
                        put("priority", "high")
                        put("notification", buildJsonObject {
                            put("channel_id", "chat_messages")
                            put("sound", "default")
                        })
                    })
                })
            }
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create("https://fcm.googleapis.com/v1/projects/$projectId/messages:send"))
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(message.toString()))
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 200) {
                println("✅ Push sent successfully to token: ${token.take(20)}...")
                true
            } else {
                println("❌ Push failed: ${response.statusCode()} - ${response.body()}")
                false
            }
        } catch (e: Exception) {
            println("❌ Push error: ${e.message}")
            false
        }
    }

    /**
     * Получает OAuth2 access token для Firebase через Service Account
     */
    private fun getAccessToken(): String? {
        return try {
            val file = File(serviceAccountPath)
            if (!file.exists()) {
                println("⚠️ Service account file not found: $serviceAccountPath")
                return null
            }
            
            // Создаём или переиспользуем credentials
            if (cachedCredentials == null) {
                cachedCredentials = GoogleCredentials
                    .fromStream(FileInputStream(file))
                    .createScoped(listOf("https://www.googleapis.com/auth/firebase.messaging"))
            }
            
            // Обновляем токен если нужно
            cachedCredentials?.refreshIfExpired()
            
            cachedCredentials?.accessToken?.tokenValue.also {
                if (it != null) {
                    println("✅ Firebase access token obtained")
                }
            }
        } catch (e: Exception) {
            println("❌ Failed to get access token: ${e.message}")
            e.printStackTrace()
            null
        }
    }
}

@Serializable
data class FcmMessage(
    val message: FcmMessageBody
)

@Serializable
data class FcmMessageBody(
    val token: String,
    val notification: FcmNotification,
    val data: Map<String, String>? = null
)

@Serializable
data class FcmNotification(
    val title: String,
    val body: String
)
