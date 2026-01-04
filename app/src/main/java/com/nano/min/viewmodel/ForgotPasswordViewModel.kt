package com.nano.min.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nano.min.network.AuthService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ForgotPasswordState(
    val email: String = "",
    val token: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val step: ForgotPasswordStep = ForgotPasswordStep.EMAIL,
    val isResetSuccessful: Boolean = false
)

enum class ForgotPasswordStep {
    EMAIL,      // Ввод email для запроса токена
    TOKEN       // Ввод токена и нового пароля
}

class ForgotPasswordViewModel(
    private val authService: AuthService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForgotPasswordState())
    val uiState = _uiState.asStateFlow()

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, error = null) }
    }

    fun onTokenChange(token: String) {
        _uiState.update { it.copy(token = token, error = null) }
    }

    fun onNewPasswordChange(password: String) {
        _uiState.update { it.copy(newPassword = password, error = null) }
    }

    fun onConfirmPasswordChange(password: String) {
        _uiState.update { it.copy(confirmPassword = password, error = null) }
    }

    fun requestReset() {
        val email = _uiState.value.email.trim()
        if (email.isBlank()) {
            _uiState.update { it.copy(error = "Введите email") }
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.update { it.copy(error = "Некорректный email") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val success = authService.forgotPassword(email)
            _uiState.update { 
                if (success) {
                    it.copy(isLoading = false, step = ForgotPasswordStep.TOKEN)
                } else {
                    it.copy(isLoading = false, step = ForgotPasswordStep.TOKEN) // Всегда переходим, чтобы не раскрывать наличие email
                }
            }
        }
    }

    fun resetPassword() {
        val state = _uiState.value
        
        if (state.token.isBlank()) {
            _uiState.update { it.copy(error = "Введите код из письма") }
            return
        }
        if (state.newPassword.length < 6) {
            _uiState.update { it.copy(error = "Пароль должен быть не менее 6 символов") }
            return
        }
        if (state.newPassword != state.confirmPassword) {
            _uiState.update { it.copy(error = "Пароли не совпадают") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val success = authService.resetPassword(
                email = state.email.trim(),
                token = state.token.trim(),
                newPassword = state.newPassword
            )
            _uiState.update { 
                if (success) {
                    it.copy(isLoading = false, isResetSuccessful = true)
                } else {
                    it.copy(isLoading = false, error = "Неверный или истёкший код")
                }
            }
        }
    }

    fun goBackToEmail() {
        _uiState.update { it.copy(step = ForgotPasswordStep.EMAIL, token = "", newPassword = "", confirmPassword = "", error = null) }
    }

    fun reset() {
        _uiState.value = ForgotPasswordState()
    }
}
