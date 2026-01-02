package com.nano.min.di

import com.nano.min.viewmodel.ChatsViewModel
import com.nano.min.viewmodel.LoginViewModel
import com.nano.min.viewmodel.ProfileViewModel
import com.nano.min.viewmodel.RegisterViewModel
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    viewModelOf(::LoginViewModel)
    viewModelOf(::RegisterViewModel)
    viewModelOf(::ChatsViewModel)
    viewModelOf(::ProfileViewModel)
}
