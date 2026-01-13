package com.nano.min.navigation

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.key
import com.nano.min.network.TokenStorage
import com.nano.min.screens.LoginScreen
import com.nano.min.screens.RegisterScreen
import com.nano.min.screens.ForgotPasswordScreen
import com.nano.min.screens.AppRootScreen
import com.nano.min.screens.ProfileScreen
import com.nano.min.screens.EditProfileScreen
import com.nano.min.screens.MeetingsScreen
import com.nano.min.viewmodel.MeetingsViewModel
import org.koin.compose.koinInject

@Composable
fun AppNavigationRoot(
    modifier: Modifier = Modifier,
) {
    val tokenStorage: TokenStorage = koinInject()
    val meetingsViewModel: MeetingsViewModel = koinInject()
    var currentRoute by remember { mutableStateOf<Route>(LoginRoute) }
    var routeStack by remember { mutableStateOf(listOf<Route>(LoginRoute)) }
    var initialCheckDone by remember { mutableStateOf(false) }
    var loginScreenKey by remember { mutableStateOf(0) }

    fun navigateToApp() {
        Log.d("AppNavigationRoot", "navigateToApp")
        currentRoute = AppRoute()
        routeStack = listOf(AppRoute())
    }

    fun navigateToLogin() {
        Log.d("AppNavigationRoot", "navigateToLogin - clearing token")
        tokenStorage.setToken(null)
        meetingsViewModel.clearState() // Очищаем встречи при выходе
        loginScreenKey++ // Увеличиваем ключ для пересоздания LoginScreen
        currentRoute = LoginRoute
        routeStack = listOf(LoginRoute)
    }
    
    fun pushRoute(route: Route) {
        routeStack = routeStack + route
        currentRoute = route
    }
    
    fun popRoute() {
        if (routeStack.size > 1) {
            routeStack = routeStack.dropLast(1)
            currentRoute = routeStack.last()
        }
    }

    // Проверяем токен ТОЛЬКО при первом запуске
    LaunchedEffect(Unit) {
        if (!initialCheckDone) {
            initialCheckDone = true
            val token = runCatching { tokenStorage.getToken() }.getOrNull()
            val hasToken = !token.isNullOrBlank()
            Log.d("AppNavigationRoot", "Initial token check: hasToken=$hasToken")
            if (hasToken) {
                navigateToApp()
            }
        }
    }
    
    // Обработка кнопки "назад"
    BackHandler(enabled = routeStack.size > 1) {
        popRoute()
    }

    // Простая навигация
    when (currentRoute) {
        is LoginRoute -> {
            key(loginScreenKey) {
                LoginScreen(
                    navigateRegister = { pushRoute(RegisterRoute) },
                    navigateForgotPassword = { pushRoute(ForgotPasswordRoute) },
                    navigateApp = { navigateToApp() }
                )
            }
        }
        is RegisterRoute -> {
            RegisterScreen(
                navigateLogin = { navigateToLogin() },
                navigateApp = { navigateToApp() }
            )
        }
        is ForgotPasswordRoute -> {
            ForgotPasswordScreen(
                navigateBack = { popRoute() },
                navigateToLogin = { navigateToLogin() }
            )
        }
        is AppRoute -> {
            AppRootScreen(
                onLogout = { navigateToLogin() },
                onProfileClick = { pushRoute(ProfileScreenRoute()) },
                onMeetingsClick = { pushRoute(MeetingsScreenRoute) }
            )
        }
        is ProfileScreenRoute -> {
            ProfileScreen(
                onLogout = { navigateToLogin() },
                onEditProfile = { pushRoute(EditProfileScreenRoute) }
            )
        }
        is EditProfileScreenRoute -> {
            EditProfileScreen(
                onBack = { popRoute() },
                onSaveSuccess = { popRoute() }
            )
        }
        is MeetingsScreenRoute -> {
            MeetingsScreen(
                onNavigateBack = { popRoute() }
            )
        }
        else -> {
            // Для неизвестных маршрутов показываем Login
            LoginScreen(
                navigateRegister = { pushRoute(RegisterRoute) },
                navigateForgotPassword = { pushRoute(ForgotPasswordRoute) },
                navigateApp = { navigateToApp() }
            )
        }
    }
}
