package com.nano.min.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.nano.min.network.TokenStorage
import com.nano.min.screens.LoginScreen
import com.nano.min.screens.RegisterScreen
import com.nano.min.screens.AppRootScreen
import org.koin.compose.koinInject

@Composable
fun AppNavigationRoot(
    modifier: Modifier = Modifier,
    darkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit
) {
    val tokenStorage: TokenStorage = koinInject()
    val backStack: SnapshotStateList<Route> = remember { mutableStateListOf(LoginRoute) }

    fun navigateToApp() {
        backStack.clear()
        backStack.add(AppRoute())
    }

    LaunchedEffect(Unit) {
        val token = runCatching { tokenStorage.getToken() }.getOrNull()
        if (!token.isNullOrBlank()) {
            navigateToApp()
        }
    }

    NavDisplay(
        modifier = modifier,
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<LoginRoute> { route ->
                LoginScreen(
                    route,
                    navigateRegister = { backStack.add(RegisterRoute) },
                    navigateForgotPassword = {},
                    onLoginSuccess = { navigateToApp() }
                )
            }
            entry<RegisterRoute> { route ->
                RegisterScreen(route,
                    navigateLogin = { backStack.add(LoginRoute) },
                    onRegisterSuccess = { navigateToApp() }
                )
            }
            entry<AppRoute> {
                AppRootScreen(
                    onLogout = {
                        backStack.clear()
                        backStack.add(LoginRoute)
                    },
                    darkTheme = darkTheme,
                    onToggleTheme = onToggleTheme
                )
            }
        }
    )
}