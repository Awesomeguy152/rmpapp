package com.example.routes

import com.example.schema.Role
import com.example.services.UserService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class SetBlockedRq(val blocked: Boolean)

@Serializable
data class SetRoleRq(val role: String)

fun Route.adminRoutes() {
    val service = UserService()

    authenticate("admin-jwt") {
        route("/api/admin") {
            // Получить всех пользователей
            get("/users") {
                call.respond(service.listForAdmin())
            }
            
            // Заблокировать/разблокировать пользователя
            patch("/users/{id}/block") {
                val userId = call.parameters["id"]?.let { 
                    runCatching { UUID.fromString(it) }.getOrNull() 
                }
                if (userId == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid user ID"))
                    return@patch
                }
                
                val rq = call.receive<SetBlockedRq>()
                val success = service.setBlocked(userId, rq.blocked)
                
                if (success) {
                    call.respond(HttpStatusCode.OK, mapOf("success" to true))
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
                }
            }
            
            // Изменить роль пользователя
            patch("/users/{id}/role") {
                val userId = call.parameters["id"]?.let { 
                    runCatching { UUID.fromString(it) }.getOrNull() 
                }
                if (userId == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid user ID"))
                    return@patch
                }
                
                val rq = call.receive<SetRoleRq>()
                val role = runCatching { Role.valueOf(rq.role.uppercase()) }.getOrNull()
                if (role == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid role"))
                    return@patch
                }
                
                val success = service.setRole(userId, role)
                
                if (success) {
                    call.respond(HttpStatusCode.OK, mapOf("success" to true))
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
                }
            }
            
            // Удалить пользователя
            delete("/users/{id}") {
                val userId = call.parameters["id"]?.let { 
                    runCatching { UUID.fromString(it) }.getOrNull() 
                }
                if (userId == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid user ID"))
                    return@delete
                }
                
                val success = service.deleteUser(userId)
                
                if (success) {
                    call.respond(HttpStatusCode.OK, mapOf("success" to true))
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
                }
            }
        }
    }
}
