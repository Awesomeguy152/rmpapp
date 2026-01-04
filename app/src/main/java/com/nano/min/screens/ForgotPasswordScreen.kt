package com.nano.min.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
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
import com.nano.min.viewmodel.ForgotPasswordStep
import com.nano.min.viewmodel.ForgotPasswordViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
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
                        if (uiState.step == ForgotPasswordStep.TOKEN) {
                            viewModel.goBackToEmail()
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

            Text(
                text = if (uiState.step == ForgotPasswordStep.EMAIL) 
                    stringResource(R.string.forgot_password_subtitle)
                else 
                    stringResource(R.string.reset_password_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                AnimatedContent(
                    targetState = uiState.step,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "step"
                ) { step ->
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

                        when (step) {
                            ForgotPasswordStep.EMAIL -> {
                                EmailStep(
                                    email = uiState.email,
                                    onEmailChange = viewModel::onEmailChange,
                                    onSubmit = viewModel::requestReset,
                                    isLoading = uiState.isLoading,
                                    focusManager = focusManager,
                                    colorScheme = colorScheme
                                )
                            }
                            ForgotPasswordStep.TOKEN -> {
                                TokenStep(
                                    email = uiState.email,
                                    token = uiState.token,
                                    newPassword = uiState.newPassword,
                                    confirmPassword = uiState.confirmPassword,
                                    onTokenChange = viewModel::onTokenChange,
                                    onNewPasswordChange = viewModel::onNewPasswordChange,
                                    onConfirmPasswordChange = viewModel::onConfirmPasswordChange,
                                    onSubmit = viewModel::resetPassword,
                                    isLoading = uiState.isLoading,
                                    passwordVisible = passwordVisible,
                                    confirmPasswordVisible = confirmPasswordVisible,
                                    onPasswordVisibilityToggle = { passwordVisible = !passwordVisible },
                                    onConfirmPasswordVisibilityToggle = { confirmPasswordVisible = !confirmPasswordVisible },
                                    focusManager = focusManager,
                                    colorScheme = colorScheme
                                )
                            }
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
private fun EmailStep(
    email: String,
    onEmailChange: (String) -> Unit,
    onSubmit: () -> Unit,
    isLoading: Boolean,
    focusManager: androidx.compose.ui.focus.FocusManager,
    colorScheme: ColorScheme
) {
    Text(
        text = stringResource(R.string.enter_email),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = colorScheme.onSurface
    )

    Spacer(modifier = Modifier.height(16.dp))

    OutlinedTextField(
        value = email,
        onValueChange = onEmailChange,
        label = { Text(stringResource(R.string.email)) },
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
                onSubmit()
            }
        ),
        singleLine = true,
        enabled = !isLoading
    )

    Spacer(modifier = Modifier.height(24.dp))

    Button(
        onClick = onSubmit,
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
                text = stringResource(R.string.send_reset_link),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun TokenStep(
    email: String,
    token: String,
    newPassword: String,
    confirmPassword: String,
    onTokenChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    isLoading: Boolean,
    passwordVisible: Boolean,
    confirmPasswordVisible: Boolean,
    onPasswordVisibilityToggle: () -> Unit,
    onConfirmPasswordVisibilityToggle: () -> Unit,
    focusManager: androidx.compose.ui.focus.FocusManager,
    colorScheme: ColorScheme
) {
    Text(
        text = stringResource(R.string.check_email, email),
        style = MaterialTheme.typography.bodyMedium,
        color = colorScheme.onSurface.copy(alpha = 0.7f),
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(16.dp))

    // Token field
    OutlinedTextField(
        value = token,
        onValueChange = onTokenChange,
        label = { Text(stringResource(R.string.reset_code)) },
        leadingIcon = { 
            Icon(Icons.Default.Key, null, tint = colorScheme.primary) 
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colorScheme.primary,
            unfocusedContainerColor = colorScheme.surfaceVariant.copy(alpha = 0.3f),
            focusedContainerColor = colorScheme.surface
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text, 
            imeAction = ImeAction.Next
        ),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) }
        ),
        singleLine = true,
        enabled = !isLoading
    )

    Spacer(modifier = Modifier.height(12.dp))

    // New password field
    OutlinedTextField(
        value = newPassword,
        onValueChange = onNewPasswordChange,
        label = { Text(stringResource(R.string.new_password)) },
        leadingIcon = { 
            Icon(Icons.Default.Lock, null, tint = colorScheme.primary) 
        },
        trailingIcon = {
            IconButton(onClick = onPasswordVisibilityToggle) {
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

    // Confirm password field
    OutlinedTextField(
        value = confirmPassword,
        onValueChange = onConfirmPasswordChange,
        label = { Text(stringResource(R.string.confirm_password)) },
        leadingIcon = { 
            Icon(Icons.Default.Lock, null, tint = colorScheme.primary) 
        },
        trailingIcon = {
            IconButton(onClick = onConfirmPasswordVisibilityToggle) {
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
                onSubmit()
            }
        ),
        singleLine = true,
        enabled = !isLoading
    )

    Spacer(modifier = Modifier.height(24.dp))

    Button(
        onClick = onSubmit,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        enabled = !isLoading && token.isNotBlank() && newPassword.isNotBlank() && confirmPassword.isNotBlank(),
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
                text = stringResource(R.string.reset_password),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
