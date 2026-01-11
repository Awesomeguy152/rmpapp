package com.example

import com.example.plugins.configureMonitoring
import com.example.plugins.configureSerialization
import com.example.plugins.configureSecurity
import com.example.plugins.configureStatusPages
import com.example.plugins.configureRouting
import com.example.plugins.configureDatabase
import com.example.plugins.configureWebSockets
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

// Version: 2.1 - Cloudinary integration with logging
fun main() {
    embeddedServer(Netty, port = System.getenv("PORT")?.toIntOrNull() ?: 8080,
        module = Application::module).start(wait = true)
}

fun Application.module() {
    configureStatusPages()
    configureMonitoring()
    configureSerialization()
    configureSecurity()
    configureDatabase()
    configureWebSockets()
    configureRouting()
}
