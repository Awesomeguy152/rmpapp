package database

import org.jetbrains.exposed.sql.transactions.transaction

fun main() {

    DatabaseConfig.init()
    TestData.insertTestData()
    demonstrateEntity()

}

fun demonstrateEntity() {
    transaction {
        println("\n Все пользователи:")
        User.all().forEach { user ->
            println("  - ${user.displayName ?: "NoName"} (${user.email}) role=${user.role}")
        }

        println("\nВсе диалоги:")
        Conversation.all().forEach { conversation ->
            println("  - ${conversation.topic ?: conversation.id.value} (${conversation.type})")
            println("    Создатель: ${conversation.createdBy.displayName}")
            println("    Участников: ${conversation.members.count()}")
            println("    Сообщений: ${conversation.messages.count()}")
        }

        val rmpConversation = Conversation.find { Conversations.topic eq "RMP" }.first()
        println("\n Сообщения в чате '${rmpConversation.topic}':")
        rmpConversation.messages.limit(3).forEach { message ->
            println("  - ${message.sender.displayName}: ${message.body}")
        }

        val anna = User.find { Users.displayName eq "Anna" }.first()
        println("\n Диалоги пользователя ${anna.displayName}:")
        anna.conversations.forEach { conversation ->
            println("  - ${conversation.topic}")
        }
    }
}