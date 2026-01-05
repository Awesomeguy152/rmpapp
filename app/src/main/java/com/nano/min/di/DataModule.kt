package com.nano.min.di

import com.nano.min.data.local.AppDatabase
import com.nano.min.data.repository.ChatRepository
import com.nano.min.data.repository.MeetingRepository
import com.nano.min.network.ApiClient
import com.nano.min.network.AuthService
import com.nano.min.network.ChatService
import com.nano.min.network.MeetingService
import com.nano.min.network.UploadService
import com.nano.min.network.DeviceTokenStorage
import com.nano.min.network.TokenStorage
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val dataModule = module {
    // TokenStorage
    single<TokenStorage> { DeviceTokenStorage(androidContext()) }
    
    // ApiClient
    single { ApiClient(tokenStorage = get()) }
    
    // AuthService (Repository layer)
    single { AuthService(get()) }

    // ChatService
    single { ChatService(get()) }

    // MeetingService
    single { MeetingService(get()) }

    // UploadService
    single { UploadService(get()) }

    // Room Database
    single { AppDatabase.getInstance(androidContext()) }
    single { get<AppDatabase>().messageDao() }
    single { get<AppDatabase>().conversationDao() }
    single { get<AppDatabase>().userDao() }
    single { get<AppDatabase>().pendingMessageDao() }
    single { get<AppDatabase>().avatarCacheDao() }

    // Repositories
    single { ChatRepository(get(), get(), get(), get()) }
    single { MeetingRepository(get()) }
}
