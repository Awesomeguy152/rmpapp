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
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isResetSuccessful: Boolean = false
)

class ForgotPasswordViewModel(
    private val authService: AuthService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForgotPasswordState())
    val uiState = _uiState.asStateFlow()

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, error = null) }
    }

    fun onNewPasswordChange(password: String) {
        _uiState.update { it.copy(newPassword = password, error = null) }
    }

    fun onConfirmPasswordChange(password: String) {
        _uiState.update { it.copy(confirmPassword = password, error = null) }
    }

    fun resetPassword() {
        val state = _uiState.value
        
        val email = state.email.trim()
        if (email.isBlank()) {
            _uiState.update { it.copy(error = "Введите email") }
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.update { it.copy(error = "Некорректный email") }
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
            val success = authService.directResetPassword(
                email = email,
                newPassword = state.newPassword
            )
            _uiState.update { 
                if (success) {
                    it.copy(isLoading = false, isResetSuccessful = true)
                } else {
                    it.copy(isLoading = false, error = "Пользователь не найден")
                }
            }
        }
    }

    fun reset() {
        _uiState.value = ForgotPasswordState()
    }
}

