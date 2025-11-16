package com.nano.min.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nano.min.R
import com.nano.min.navigation.RegisterRoute
import com.nano.min.network.ApiClient
import com.nano.min.network.AuthService
import com.nano.min.network.DeviceTokenStorage
import com.nano.min.ui.theme.AppButton
import com.nano.min.ui.theme.KeyboardPassword
import com.nano.min.ui.theme.LargeTitle
import com.nano.min.ui.theme.MinTheme
import com.nano.min.ui.theme.Typography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun RegisterScreen(
    screenContext: RegisterRoute,
    api: ApiClient,
    navigateLogin: () -> Unit = {},
    onRegisterSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope { Dispatchers.IO }
    val authService = AuthService(api)

    var loginString by remember { mutableStateOf("") }
    var passwordString by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.padding(top = 80.dp))
        Image(
            painterResource(R.drawable.logo_no_back),
            "app logo",
            modifier = Modifier
                .size(140.dp)
                .align(Alignment.CenterHorizontally)
        )
        LargeTitle(
            stringResource(R.string.screen_register),
            modifier = Modifier.padding(top = 24.dp, bottom = 64.dp)
        )
        OutlinedCard(shape = ShapeDefaults.Medium, modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.email),
                    style = Typography.bodyLarge,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                )
                OutlinedTextField(
                    value = loginString,
                    onValueChange = {
                        loginString = it
                    },
                    singleLine = true
                )
                Text(
                    text = stringResource(R.string.password),
                    style = Typography.bodyLarge,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                )
                OutlinedTextField(
                    value = passwordString,
                    onValueChange = {
                        passwordString = it
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardPassword
                )
                Spacer(Modifier.padding(vertical = 16.dp))
                AppButton(
                    text = stringResource(R.string.register),
                    onClick = {
                        scope.launch {
                            isLoading = true
                            val success = try {
                                authService.login(loginString, passwordString)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                error = e.localizedMessage?: e.message?: "Unknown error"
                                false
                            }
                            isLoading = false
                            if (success) {
                                onRegisterSuccess()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    enabled = (loginString.isNotEmpty() && passwordString.isNotEmpty()) && !isLoading
                )
                if (error != null) {
                    Text(
                        text = error!!,
                        style = Typography.bodyMedium,
                        color = Color.Red,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
            }
        }
    }
}

@Preview(showSystemUi = false)
@Composable
private fun Preview() {
    val api = ApiClient(tokenStorage = DeviceTokenStorage(LocalContext.current))
    MinTheme {
        RegisterScreen(RegisterRoute, api) {}
    }
}