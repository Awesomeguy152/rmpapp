package com.example.routes

import com.example.services.CloudinaryService
import com.example.services.UserService
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class UploadResponse(
    val url: String,
    val fileName: String
)

private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()

fun Route.uploadRoutes() {
    val userService = UserService()
    val cloudinaryService = CloudinaryService()
    
    application.log.info("UploadRoutes initialized - using Cloudinary for uploads")

    authenticate("auth-jwt") {
        // Загрузка аватара отключена
        post("/api/upload/avatar") {
            call.respond(
                HttpStatusCode.Gone,
                mapOf("error" to "profile_avatar_disabled", "message" to "Загрузка фото профиля временно недоступна")
            )
        }

        // Загрузка изображения для сообщения
        post("/api/upload/image") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@post call.respond(HttpStatusCode.Unauthorized)

            principal.subject?.toUuidOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid_subject"))

            val multipart = call.receiveMultipart()
            var imageUrl: String? = null
            var originalFileName: String? = null

            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> {
                        originalFileName = part.originalFileName ?: "image.jpg"
                        val extension = originalFileName!!.substringAfterLast(".", "jpg")
                        
                        // Проверяем расширение
                        if (extension.lowercase() !in listOf("jpg", "jpeg", "png", "gif", "webp")) {
                            part.dispose()
                            return@forEachPart
                        }
                        
                        // Загружаем в Cloudinary
                        try {
                            imageUrl = cloudinaryService.uploadImage(
                                part.streamProvider(),
                                originalFileName!!
                            )
                        } catch (e: Exception) {
                            application.log.error("Failed to upload image to Cloudinary", e)
                        }
                    }
                    else -> {}
                }
                part.dispose()
            }

            if (imageUrl == null) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "upload_failed"))
            }

            call.respond(HttpStatusCode.OK, UploadResponse(
                url = imageUrl!!,
                fileName = originalFileName ?: "image.jpg"
            ))
        }
    }
}
