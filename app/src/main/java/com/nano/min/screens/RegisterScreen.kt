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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nano.min.R
import com.nano.min.navigation.RegisterRoute
import com.nano.min.ui.theme.AppButton
import com.nano.min.ui.theme.KeyboardPassword
import com.nano.min.ui.theme.LargeTitle
import com.nano.min.ui.theme.MinTheme
import com.nano.min.ui.theme.Typography
import com.nano.min.viewmodel.RegisterViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun RegisterScreen(
    screenContext: RegisterRoute,
    navigateLogin: () -> Unit = {},
    onRegisterSuccess: () -> Unit,
    viewModel: RegisterViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isRegisterSuccessful) {
        if (uiState.isRegisterSuccessful) {
            onRegisterSuccess()
            viewModel.resetRegisterSuccess()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.padding(top = 80.dp))

        Image(
            painterResource(R.drawable.logo_no_back),
            contentDescription = "app logo",
            modifier = Modifier.size(140.dp)
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
                    value = uiState.email,
                    onValueChange = viewModel::onEmailChange,
                    singleLine = true
                )

                Text(
                    text = stringResource(R.string.password),
                    style = Typography.bodyLarge,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                )
                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = viewModel::onPasswordChange,
                    singleLine = true,
                    keyboardOptions = KeyboardPassword
                )

                Spacer(Modifier.padding(vertical = 16.dp))

                AppButton(
                    text = stringResource(R.string.register),
                    onClick = viewModel::register,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    enabled = uiState.email.isNotEmpty() && uiState.password.isNotEmpty() && !uiState.isLoading
                )

                if (uiState.error != null) {
                    Text(
                        text = uiState.error!!,
                        style = Typography.bodyMedium,
                        color = Color.Red,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun RegisterScreenPreview() {
    MinTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.padding(top = 80.dp))

            Image(
                painterResource(R.drawable.logo_no_back),
                contentDescription = "logo",
                modifier = Modifier.size(140.dp)
            )

            LargeTitle(
                text = "Register",
                modifier = Modifier.padding(top = 24.dp, bottom = 64.dp)
            )

            OutlinedCard(
                shape = ShapeDefaults.Medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 32.dp)
                        .fillMaxWidth()
                ) {

                    Text(
                        text = "Email",
                        style = Typography.bodyLarge,
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                    )

                    OutlinedTextField(
                        value = "example@email.com",
                        onValueChange = {},
                        singleLine = true
                    )

                    Text(
                        text = "Password",
                        style = Typography.bodyLarge,
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                    )

                    OutlinedTextField(
                        value = "••••••••",
                        onValueChange = {},
                        singleLine = true,
                        keyboardOptions = KeyboardPassword
                    )

                    Spacer(Modifier.padding(vertical = 16.dp))

                    AppButton(
                        text = "Register",
                        onClick = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        enabled = true
                    )

                    Text(
                        text = "Email or password is invalid",
                        style = Typography.bodyMedium,
                        color = Color.Red,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
            }
        }
    }
}
