package com.example.plugins

import com.example.database.SampleDataSeeder
import com.example.schema.UserTable
import com.example.schema.Conversations
import com.example.schema.ConversationMembers
import com.example.schema.ConversationArchives
import com.example.schema.ConversationMutes
import com.example.schema.PinnedMessages
import com.example.schema.Messages
import com.example.schema.MessageAttachments
import com.example.schema.ConversationReadMarkers
import com.example.schema.ConversationPins
import com.example.schema.MessageReactions
import com.example.schema.DeviceTokens
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import com.example.schema.PasswordResetTokens
import java.net.URI

fun Application.configureDatabase() {
    // Поддержка Railway/Render/Fly.io DATABASE_URL
    val databaseUrl = System.getenv("DATABASE_URL")
    
    val (jdbcUrl, user, pass) = if (databaseUrl != null) {
        // Парсинг DATABASE_URL: postgresql://user:password@host:port/database
        val uri = URI(databaseUrl)
        val userInfo = uri.userInfo?.split(":") ?: listOf("app", "app")
        val parsedUser = userInfo.getOrElse(0) { "app" }
        val parsedPass = userInfo.getOrElse(1) { "app" }
        val parsedJdbcUrl = "jdbc:postgresql://${uri.host}:${uri.port}${uri.path}"
        Triple(parsedJdbcUrl, parsedUser, parsedPass)
    } else {
        // Локальная разработка / Docker
        val urlFromEnv = System.getenv("DB_URL")
        val host = System.getenv("DB_HOST") ?: "localhost"
        val port = (System.getenv("DB_PORT") ?: "5433").toInt()
        val db   = System.getenv("DB_NAME") ?: "appdb"
        val localUser = System.getenv("DB_USER") ?: "app"
        val localPass = System.getenv("DB_PASSWORD") ?: "app"
        val localJdbcUrl = urlFromEnv ?: "jdbc:postgresql://$host:$port/$db"
        Triple(localJdbcUrl, localUser, localPass)
    }

    val ds = HikariDataSource(HikariConfig().apply {
        driverClassName = "org.postgresql.Driver"
        this.jdbcUrl = jdbcUrl
        username = user
        password = pass

        initializationFailTimeout = -1
        connectionTimeout = 15000
        maximumPoolSize = 10
        minimumIdle = 0
    })

    Database.connect(ds)

    transaction {
        SchemaUtils.createMissingTablesAndColumns(
            UserTable,
            PasswordResetTokens,
            Conversations,
            ConversationMembers,
            Messages,
            MessageAttachments,
            ConversationReadMarkers,
            ConversationPins,
            ConversationArchives,
            ConversationMutes,
            PinnedMessages,
            MessageReactions,
            DeviceTokens
        )
    }

    SampleDataSeeder.seedIfEnabled(environment)
}
