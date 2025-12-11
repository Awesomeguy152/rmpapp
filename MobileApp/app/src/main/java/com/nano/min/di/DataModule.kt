package com.nano.min.di

import com.nano.min.network.ApiClient
import com.nano.min.network.AuthService
import com.nano.min.network.ChatService
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
}
