package com.nano.min.network

import android.content.Context
import android.net.Uri
import com.nano.min.util.ImageUtils
import io.ktor.client.call.body
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.serialization.Serializable

@Serializable
data class UploadResponse(
    val url: String,
    val filename: String
)

class UploadService(private val apiClient: ApiClient) {
    private val httpClient get() = apiClient.httpClient
    private val baseUrl get() = apiClient.baseUrl

    /**
     * Загружает аватар пользователя
     * @param context Context для доступа к ContentResolver
     * @param imageUri Uri выбранного изображения
     * @return URL загруженного аватара
     */
    suspend fun uploadAvatar(context: Context, imageUri: Uri): Result<String> {
        return try {
            val compressedBytes = ImageUtils.compressForAvatar(context, imageUri)
                ?: return Result.failure(Exception("Не удалось сжать изображение"))

            val response: UploadResponse = httpClient.submitFormWithBinaryData(
                url = "$baseUrl/api/upload/avatar",
                formData = formData {
                    append("file", compressedBytes, Headers.build {
                        append(HttpHeaders.ContentType, "image/jpeg")
                        append(HttpHeaders.ContentDisposition, "filename=\"avatar.jpg\"")
                    })
                }
            ).body()

            Result.success(response.url)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Загружает изображение для сообщения
     * @param context Context для доступа к ContentResolver
     * @param imageUri Uri выбранного изображения
     * @return URL загруженного изображения
     */
    suspend fun uploadImage(context: Context, imageUri: Uri): Result<String> {
        return try {
            val compressedBytes = ImageUtils.compressForMessage(context, imageUri)
                ?: return Result.failure(Exception("Не удалось сжать изображение"))

            val response: UploadResponse = httpClient.submitFormWithBinaryData(
                url = "$baseUrl/api/upload/image",
                formData = formData {
                    append("file", compressedBytes, Headers.build {
                        append(HttpHeaders.ContentType, "image/jpeg")
                        append(HttpHeaders.ContentDisposition, "filename=\"image.jpg\"")
                    })
                }
            ).body()

            Result.success(response.url)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Загружает изображение для сообщения и возвращает полную информацию
     * @param context Context для доступа к ContentResolver
     * @param imageUri Uri выбранного изображения
     * @return UploadResponse с URL и filename
     */
    suspend fun uploadImageWithDetails(context: Context, imageUri: Uri): Result<UploadResponse> {
        return try {
            val compressedBytes = ImageUtils.compressForMessage(context, imageUri)
                ?: return Result.failure(Exception("Не удалось сжать изображение"))

            val response: UploadResponse = httpClient.submitFormWithBinaryData(
                url = "$baseUrl/api/upload/image",
                formData = formData {
                    append("file", compressedBytes, Headers.build {
                        append(HttpHeaders.ContentType, "image/jpeg")
                        append(HttpHeaders.ContentDisposition, "filename=\"image.jpg\"")
                    })
                }
            ).body()

            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
