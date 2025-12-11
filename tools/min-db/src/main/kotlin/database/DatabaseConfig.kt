package database

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseConfig {
    fun init() {
        Database.connect(
            url = "jdbc:postgresql://backend-postgres-1:5432/appdb",
            driver = "org.postgresql.Driver",
            user = "app",
            password = "app"
        )

        transaction {
            exec("""
                DO $$ BEGIN
                    CREATE TYPE user_role AS ENUM ('creator', 'moderator', 'user');
                EXCEPTION
                    WHEN duplicate_object THEN null;
                END $$;
            """.trimIndent())

            SchemaUtils.create(
                Users,
                Conversations,
                ConversationMembers,
                Messages,
                MessageAttachments,
                ConversationReadMarkers
            )
        }

    }
}