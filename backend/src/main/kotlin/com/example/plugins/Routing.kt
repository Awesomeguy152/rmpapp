package com.example.plugins

import com.example.routes.adminRoutes
import com.example.routes.authRoutes
import com.example.routes.meRoutes
import com.example.routes.chatRoutes
import com.example.routes.aiRoutes
import com.example.routes.docsRoutes
import com.example.routes.userRoutes
import io.ktor.server.application.*
import io.ktor.server.routing.*
fun Application.configureRouting() {
    routing {
        authRoutes()
        meRoutes()
        userRoutes()
        adminRoutes()
        chatRoutes()
        aiRoutes()
        docsRoutes()
    }
}
