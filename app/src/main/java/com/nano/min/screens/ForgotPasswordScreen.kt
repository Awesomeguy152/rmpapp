package com.nano.min.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nano.min.R
import com.nano.min.viewmodel.ForgotPasswordViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun ForgotPasswordScreen(
    navigateBack: () -> Unit,
    navigateToLogin: () -> Unit
) {
    val viewModel: ForgotPasswordViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val colorScheme = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isResetSuccessful) {
        if (uiState.isResetSuccessful) {
            navigateToLogin()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        colorScheme.primary.copy(alpha = 0.1f),
                        colorScheme.background,
                        colorScheme.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Back button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(
                    onClick = {
                        if (uiState.currentStep != com.nano.min.viewmodel.ResetStep.EMAIL) {
                            viewModel.goBack()
                        } else {
                            navigateBack()
                        }
                    },
                    enabled = !uiState.isLoading
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = colorScheme.onBackground
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Logo
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .shadow(12.dp, CircleShape)
                    .clip(CircleShape)
                    .background(colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.logo_no_back),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.forgot_password_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onBackground
            )

    // Step indicator
            Text(
                text = when (uiState.currentStep) {
                    com.nano.min.viewmodel.ResetStep.EMAIL -> "Шаг 1: Введите email"
                    com.nano.min.viewmodel.ResetStep.CODE -> "Шаг 2: Введите код из письма"
                    com.nano.min.viewmodel.ResetStep.PASSWORD -> "Шаг 3: Новый пароль"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp),
                textAlign = TextAlign.Center
            )
            
            // Progress indicator
            val stepNumber = when (uiState.currentStep) {
                com.nano.min.viewmodel.ResetStep.EMAIL -> 1
                com.nano.min.viewmodel.ResetStep.CODE -> 2
                com.nano.min.viewmodel.ResetStep.PASSWORD -> 3
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(3) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index + 1 == stepNumber) 12.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (index + 1 <= stepNumber) colorScheme.primary
                                else colorScheme.onBackground.copy(alpha = 0.3f)
                            )
                    )
                    if (index < 2) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Error message
                    AnimatedVisibility(
                        visible = uiState.error != null,
                        enter = fadeIn() + slideInVertically(),
                        exit = fadeOut()
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            colors = CardDefaults.cardColors(containerColor = colorScheme.errorContainer),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = uiState.error ?: "",
                                color = colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    AnimatedContent(
                        targetState = uiState.currentStep,
                        transitionSpec = {
                            if (targetState.ordinal > initialState.ordinal) {
                                slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                            } else {
                                slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                            }
                        },
                        label = "step_animation"
                    ) { step ->
                        when (step) {
                            com.nano.min.viewmodel.ResetStep.EMAIL -> Step1EmailInput(
                                email = uiState.email,
                                onEmailChange = viewModel::onEmailChange,
                                isLoading = uiState.isLoading,
                                onRequestCode = { viewModel.requestCode() },
                                focusManager = focusManager,
                                colorScheme = colorScheme
                            )
                            com.nano.min.viewmodel.ResetStep.CODE -> Step2CodeInput(
                                code = uiState.code,
                                onCodeChange = viewModel::onCodeChange,
                                isLoading = uiState.isLoading,
                                onVerifyCode = { viewModel.verifyCode() },
                                focusManager = focusManager,
                                colorScheme = colorScheme
                            )
                            com.nano.min.viewmodel.ResetStep.PASSWORD -> Step3NewPassword(
                                newPassword = uiState.newPassword,
                                confirmPassword = uiState.confirmPassword,
                                onNewPasswordChange = viewModel::onNewPasswordChange,
                                onConfirmPasswordChange = viewModel::onConfirmPasswordChange,
                                passwordVisible = passwordVisible,
                                confirmPasswordVisible = confirmPasswordVisible,
                                onPasswordVisibleChange = { passwordVisible = it },
                                onConfirmPasswordVisibleChange = { confirmPasswordVisible = it },
                                isLoading = uiState.isLoading,
                                onSetNewPassword = { viewModel.resetPassword() },
                                focusManager = focusManager,
                                colorScheme = colorScheme
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(
                onClick = navigateToLogin,
                enabled = !uiState.isLoading
            ) {
                Text(
                    text = stringResource(R.string.back_to_login),
                    color = colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun Step1EmailInput(
    email: String,
    onEmailChange: (String) -> Unit,
    isLoading: Boolean,
    onRequestCode: () -> Unit,
    focusManager: androidx.compose.ui.focus.FocusManager,
    colorScheme: ColorScheme
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Введите email, на который зарегистрирован аккаунт. Мы отправим код подтверждения.",
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            label = { Text("Email") },
            leadingIcon = { 
                Icon(Icons.Default.Email, null, tint = colorScheme.primary) 
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colorScheme.primary,
                unfocusedContainerColor = colorScheme.surfaceVariant.copy(alpha = 0.3f),
                focusedContainerColor = colorScheme.surface
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email, 
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    onRequestCode()
                }
            ),
            singleLine = true,
            enabled = !isLoading
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onRequestCode,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = !isLoading && email.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = "Получить код",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun Step2CodeInput(
    code: String,
    onCodeChange: (String) -> Unit,
    isLoading: Boolean,
    onVerifyCode: () -> Unit,
    focusManager: androidx.compose.ui.focus.FocusManager,
    colorScheme: ColorScheme
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Мы отправили 6-значный код на вашу почту. Код действителен 15 минут.",
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        OutlinedTextField(
            value = code,
            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) onCodeChange(it) },
            label = { Text("Код подтверждения") },
            leadingIcon = { 
                Icon(Icons.Default.Pin, null, tint = colorScheme.primary) 
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colorScheme.primary,
                unfocusedContainerColor = colorScheme.surfaceVariant.copy(alpha = 0.3f),
                focusedContainerColor = colorScheme.surface
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number, 
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    onVerifyCode()
                }
            ),
            singleLine = true,
            enabled = !isLoading,
            placeholder = { Text("123456") }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onVerifyCode,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = !isLoading && code.length == 6,
            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = "Подтвердить",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun Step3NewPassword(
    newPassword: String,
    confirmPassword: String,
    onNewPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    confirmPasswordVisible: Boolean,
    onPasswordVisibleChange: (Boolean) -> Unit,
    onConfirmPasswordVisibleChange: (Boolean) -> Unit,
    isLoading: Boolean,
    onSetNewPassword: () -> Unit,
    focusManager: androidx.compose.ui.focus.FocusManager,
    colorScheme: ColorScheme
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Код подтвержден! Теперь введите новый пароль (минимум 8 символов).",
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        OutlinedTextField(
            value = newPassword,
            onValueChange = onNewPasswordChange,
            label = { Text("Новый пароль") },
            leadingIcon = { 
                Icon(Icons.Default.Lock, null, tint = colorScheme.primary) 
            },
            trailingIcon = {
                IconButton(onClick = { onPasswordVisibleChange(!passwordVisible) }) {
                    Icon(
                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariant
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colorScheme.primary,
                unfocusedContainerColor = colorScheme.surfaceVariant.copy(alpha = 0.3f),
                focusedContainerColor = colorScheme.surface
            ),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password, 
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            ),
            singleLine = true,
            enabled = !isLoading
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = onConfirmPasswordChange,
            label = { Text("Подтвердите пароль") },
            leadingIcon = { 
                Icon(Icons.Default.Lock, null, tint = colorScheme.primary) 
            },
            trailingIcon = {
                IconButton(onClick = { onConfirmPasswordVisibleChange(!confirmPasswordVisible) }) {
                    Icon(
                        if (confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariant
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colorScheme.primary,
                unfocusedContainerColor = colorScheme.surfaceVariant.copy(alpha = 0.3f),
                focusedContainerColor = colorScheme.surface
            ),
            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password, 
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    onSetNewPassword()
                }
            ),
            singleLine = true,
            enabled = !isLoading
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onSetNewPassword,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = !isLoading && newPassword.isNotBlank() && confirmPassword.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = "Сменить пароль",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
