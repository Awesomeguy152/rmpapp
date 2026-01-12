package com.nano.min.di

import com.nano.min.viewmodel.ChatsViewModel
import com.nano.min.viewmodel.ForgotPasswordViewModel
import com.nano.min.viewmodel.LoginViewModel
import com.nano.min.viewmodel.MeetingsViewModel
import com.nano.min.viewmodel.ProfileViewModel
import com.nano.min.viewmodel.RegisterViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    viewModelOf(::LoginViewModel)
    viewModelOf(::RegisterViewModel)
    viewModelOf(::ForgotPasswordViewModel)
    viewModelOf(::ChatsViewModel)
    viewModelOf(::ProfileViewModel)
    viewModelOf(::MeetingsViewModel)
}
