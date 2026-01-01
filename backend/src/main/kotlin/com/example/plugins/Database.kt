package com.example.plugins

import com.example.database.SampleDataSeeder
import com.example.schema.UserTable
import com.example.schema.Conversations
import com.example.schema.ConversationMembers
import com.example.schema.Messages
import com.example.schema.MessageAttachments
import com.example.schema.ConversationReadMarkers
import com.example.schema.ConversationPins
import com.example.schema.MessageReactions
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import com.example.schema.PasswordResetTokens

fun Application.configureDatabase() {
    val urlFromEnv = System.getenv("DB_URL")
    val host = System.getenv("DB_HOST") ?: "localhost"
    val port = (System.getenv("DB_PORT") ?: "5433").toInt()
    val db   = System.getenv("DB_NAME") ?: "appdb"
    val user = System.getenv("DB_USER") ?: "app"
    val pass = System.getenv("DB_PASSWORD") ?: "app"

    val jdbcUrl = urlFromEnv ?: "jdbc:postgresql://$host:$port/$db"

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
            MessageReactions
        )
    }

    SampleDataSeeder.seedIfEnabled(environment)
}
