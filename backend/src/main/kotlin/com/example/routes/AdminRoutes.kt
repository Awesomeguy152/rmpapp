package com.example.routes

import com.example.services.UserService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.adminRoutes() {
    val service = UserService()

    authenticate("admin-jwt") {
        get("/api/admin/users") {
            call.respond(service.list())
        }
    }
}
