package com.nano.min.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.sharp.Chat
import androidx.compose.material.icons.automirrored.sharp.Login
import androidx.compose.material.icons.sharp.AutoFixHigh
import androidx.compose.material.icons.sharp.CalendarMonth
import androidx.compose.material.icons.sharp.Chat
import androidx.compose.material.icons.sharp.Password
import androidx.compose.ui.graphics.vector.ImageVector
import com.nano.min.R

sealed interface Route {
    val title: Int
    val icon: ImageVector
}
data object LoginRoute : Route {
    override val title = R.string.screen_login
    override val icon = Icons.AutoMirrored.Sharp.Login
}

data object RegisterRoute : Route {
    override val title = R.string.screen_register
    override val icon = Icons.Sharp.AutoFixHigh
}

data object ForgotPasswordRoute : Route {
    override val title = R.string.screen_forgotpass
    override val icon = Icons.Sharp.Password
}

data class AppRoute(val screen: Route = ChatsScreenRoute) : Route {
    override val title = screen.title
    override val icon = screen.icon
}

data object ChatsScreenRoute : Route {
    override val title = R.string.screen_chats
    override val icon = Icons.AutoMirrored.Sharp.Chat
}

data object SettingsScreenRoute : Route {
    override val title = R.string.screen_settings
    override val icon = Icons.Sharp.Chat
}

data class ProfileScreenRoute(val userId: String? = null) : Route {
    override val title = R.string.screen_profile
    override val icon = Icons.AutoMirrored.Sharp.Chat
}

data object EditProfileScreenRoute : Route {
    override val title = R.string.screen_profile
    override val icon = Icons.AutoMirrored.Sharp.Chat
}

data object MeetingsScreenRoute : Route {
    override val title = R.string.screen_meetings
    override val icon = Icons.Sharp.CalendarMonth
}