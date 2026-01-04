package com.nano.min.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MessageEntity::class,
        ConversationEntity::class,
        UserEntity::class,
        PendingMessageEntity::class,
        AvatarCacheEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao
    abstract fun userDao(): UserDao
    abstract fun pendingMessageDao(): PendingMessageDao
    abstract fun avatarCacheDao(): AvatarCacheDao

    companion object {
        private const val DATABASE_NAME = "min_messenger_db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .fallbackToDestructiveMigration() // Для простоты - удаляем данные при миграции
                .build()
        }
    }
}
