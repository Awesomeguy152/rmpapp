package com.example.routes

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
import java.io.File
import java.util.UUID

@Serializable
data class UploadResponse(
    val url: String,
    val fileName: String
)

fun Route.uploadRoutes() {
    val service = UserService()
    
    // Папка для хранения загруженных файлов
    val uploadDir = File(System.getenv("UPLOAD_DIR") ?: "uploads").apply {
        if (!exists()) mkdirs()
    }
    
    // Публичный URL для доступа к файлам
    val publicUrl = System.getenv("PUBLIC_URL") ?: "http://localhost:8080"

    authenticate("auth-jwt") {
        // Загрузка аватара
        post("/api/upload/avatar") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@post call.respond(HttpStatusCode.Unauthorized)

            val userId = principal.subject?.toUuidOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid_subject"))

            val multipart = call.receiveMultipart()
            var uploadedFile: File? = null
            var originalFileName: String? = null

            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> {
                        originalFileName = part.originalFileName ?: "avatar.jpg"
                        val extension = originalFileName!!.substringAfterLast(".", "jpg")
                        
                        // Проверяем расширение
                        if (extension.lowercase() !in listOf("jpg", "jpeg", "png", "gif", "webp")) {
                            part.dispose()
                            return@forEachPart
                        }
                        
                        // Генерируем уникальное имя файла
                        val fileName = "avatar_${userId}_${System.currentTimeMillis()}.$extension"
                        uploadedFile = File(uploadDir, fileName)
                        
                        // Сохраняем файл
                        part.streamProvider().use { input ->
                            uploadedFile!!.outputStream().buffered().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    else -> {}
                }
                part.dispose()
            }

            if (uploadedFile == null) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "no_file_uploaded"))
            }

            val avatarUrl = "$publicUrl/uploads/${uploadedFile!!.name}"
            
            // Обновляем аватар пользователя в БД
            service.updateProfile(userId, null, null, null, avatarUrl)

            call.respond(HttpStatusCode.OK, UploadResponse(
                url = avatarUrl,
                fileName = uploadedFile!!.name
            ))
        }

        // Загрузка изображения для сообщения
        post("/api/upload/image") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@post call.respond(HttpStatusCode.Unauthorized)

            val userId = principal.subject?.toUuidOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid_subject"))

            val multipart = call.receiveMultipart()
            var uploadedFile: File? = null

            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> {
                        val originalFileName = part.originalFileName ?: "image.jpg"
                        val extension = originalFileName.substringAfterLast(".", "jpg")
                        
                        // Проверяем расширение
                        if (extension.lowercase() !in listOf("jpg", "jpeg", "png", "gif", "webp")) {
                            part.dispose()
                            return@forEachPart
                        }
                        
                        // Генерируем уникальное имя файла
                        val fileName = "img_${userId}_${System.currentTimeMillis()}.$extension"
                        uploadedFile = File(uploadDir, fileName)
                        
                        // Сохраняем файл
                        part.streamProvider().use { input ->
                            uploadedFile!!.outputStream().buffered().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    else -> {}
                }
                part.dispose()
            }

            if (uploadedFile == null) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "no_file_uploaded"))
            }

            val imageUrl = "$publicUrl/uploads/${uploadedFile!!.name}"

            call.respond(HttpStatusCode.OK, UploadResponse(
                url = imageUrl,
                fileName = uploadedFile!!.name
            ))
        }
    }

    // Статические файлы (публичный доступ к загруженным файлам)
    get("/uploads/{fileName}") {
        val fileName = call.parameters["fileName"]
            ?: return@get call.respond(HttpStatusCode.BadRequest)

        val file = File(uploadDir, fileName)
        if (!file.exists()) {
            return@get call.respond(HttpStatusCode.NotFound)
        }

        // Определяем content type
        val contentType = when (file.extension.lowercase()) {
            "jpg", "jpeg" -> ContentType.Image.JPEG
            "png" -> ContentType.Image.PNG
            "gif" -> ContentType.Image.GIF
            "webp" -> ContentType("image", "webp")
            else -> ContentType.Application.OctetStream
        }

        call.respondFile(file)
    }
}

private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()
