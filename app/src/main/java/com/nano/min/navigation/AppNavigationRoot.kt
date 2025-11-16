package com.nano.min.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.nano.min.screens.LoginScreen
import com.nano.min.screens.RegisterScreen

@Composable
fun AppNavigationRoot(
    modifier: Modifier = Modifier,
) {
    val backStack: SnapshotStateList<Route> = remember { mutableStateListOf(LoginRoute) }

    fun navigateToApp() {
        backStack.clear()
        backStack.add(AppRoute())
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
        }
    )
}