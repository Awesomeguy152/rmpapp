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
import com.nano.min.screens.ForgotPasswordScreen
import com.nano.min.screens.AppRootScreen
import com.nano.min.screens.ProfileScreen
import com.nano.min.screens.EditProfileScreen
import org.koin.compose.koinInject

@Composable
fun AppNavigationRoot(
    modifier: Modifier = Modifier,
) {
    val tokenStorage: TokenStorage = koinInject()
    val backStack: SnapshotStateList<Route> = remember { mutableStateListOf(LoginRoute) }

    fun navigateToApp() {
        backStack.clear()
        backStack.add(AppRoute())
    }

    fun navigateToLogin() {
        backStack.clear()
        backStack.add(LoginRoute)
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
                    navigateRegister = { backStack.add(RegisterRoute) },
                    navigateForgotPassword = { backStack.add(ForgotPasswordRoute) },
                    navigateApp = { navigateToApp() }
                )
            }
            entry<RegisterRoute> { route ->
                RegisterScreen(
                    navigateLogin = { backStack.removeAt(backStack.lastIndex); backStack.add(LoginRoute) },
                    navigateApp = { navigateToApp() }
                )
            }
            entry<ForgotPasswordRoute> { route ->
                ForgotPasswordScreen(
                    navigateBack = { backStack.removeLastOrNull() },
                    navigateToLogin = { navigateToLogin() }
                )
            }
            entry<AppRoute> {
                AppRootScreen(
                    onLogout = {
                        backStack.clear()
                        backStack.add(LoginRoute)
                    },
                    onProfileClick = {
                        backStack.add(ProfileScreenRoute())
                    }
                )
            }
            entry<ProfileScreenRoute> {
                ProfileScreen(
                    onLogout = {
                        backStack.clear()
                        backStack.add(LoginRoute)
                    },
                    onEditProfile = {
                        backStack.add(EditProfileScreenRoute)
                    }
                )
            }
            entry<EditProfileScreenRoute> {
                EditProfileScreen(
                    onBack = { backStack.removeLastOrNull() },
                    onSaveSuccess = { backStack.removeLastOrNull() }
                )
            }
        }
    )
}