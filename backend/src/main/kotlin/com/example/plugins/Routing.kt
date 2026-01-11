package com.example.plugins

import com.example.routes.adminRoutes
import com.example.routes.authRoutes
import com.example.routes.meRoutes
import com.example.routes.chatRoutes
import com.example.routes.aiRoutes
import com.example.routes.docsRoutes
import com.example.routes.userRoutes
import com.example.routes.deviceTokenRoutes
import com.example.routes.uploadRoutes
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        // Health check для Railway/Render/Fly.io
        get("/api/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }
        
        authRoutes()
        meRoutes()
        userRoutes()
        adminRoutes()
        chatRoutes()
        aiRoutes()
        docsRoutes()
        deviceTokenRoutes()
        uploadRoutes()
    }
}
