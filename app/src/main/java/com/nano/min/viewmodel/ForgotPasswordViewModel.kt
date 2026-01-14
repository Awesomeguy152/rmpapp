package com.nano.min.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nano.min.network.AuthService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ResetStep {
    EMAIL,      // Ввод email
    CODE,       // Ввод кода из письма
    PASSWORD    // Ввод нового пароля
}

data class ForgotPasswordState(
    val email: String = "",
    val code: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val currentStep: ResetStep = ResetStep.EMAIL,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isResetSuccessful: Boolean = false,
    val codeSent: Boolean = false
)

class ForgotPasswordViewModel(
    private val authService: AuthService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForgotPasswordState())
    val uiState = _uiState.asStateFlow()

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, error = null) }
    }
    
    fun onCodeChange(code: String) {
        _uiState.update { it.copy(code = code, error = null) }
    }

    fun onNewPasswordChange(password: String) {
        _uiState.update { it.copy(newPassword = password, error = null) }
    }

    fun onConfirmPasswordChange(password: String) {
        _uiState.update { it.copy(confirmPassword = password, error = null) }
    }
    
    /**
     * Шаг 1: Отправить код на email
     */
    fun requestCode() {
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
            val success = authService.requestResetCode(email)
            _uiState.update {
                if (success) {
                    it.copy(
                        isLoading = false,
                        codeSent = true,
                        currentStep = ResetStep.CODE
                    )
                } else {
                    // Всегда показываем успех для безопасности
                    it.copy(
                        isLoading = false,
                        codeSent = true,
                        currentStep = ResetStep.CODE
                    )
                }
            }
        }
    }
    
    /**
     * Шаг 2: Проверить код
     */
    fun verifyCode() {
        val state = _uiState.value
        val code = state.code.trim()
        
        if (code.length != 6) {
            _uiState.update { it.copy(error = "Введите 6-значный код") }
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val valid = authService.verifyResetCode(state.email, code)
            _uiState.update {
                if (valid) {
                    it.copy(
                        isLoading = false,
                        currentStep = ResetStep.PASSWORD
                    )
                } else {
                    it.copy(
                        isLoading = false,
                        error = "Неверный или просроченный код"
                    )
                }
            }
        }
    }
    
    /**
     * Шаг 3: Установить новый пароль
     */
    fun resetPassword() {
        val state = _uiState.value
        
        if (state.newPassword.length < 8) {
            _uiState.update { it.copy(error = "Пароль должен быть не менее 8 символов") }
            return
        }
        if (state.newPassword != state.confirmPassword) {
            _uiState.update { it.copy(error = "Пароли не совпадают") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val success = authService.resetPasswordWithCode(
                email = state.email,
                code = state.code,
                newPassword = state.newPassword
            )
            _uiState.update { 
                if (success) {
                    it.copy(isLoading = false, isResetSuccessful = true)
                } else {
                    it.copy(isLoading = false, error = "Не удалось сбросить пароль. Попробуйте снова.")
                }
            }
        }
    }
    
    fun goBack() {
        val currentStep = _uiState.value.currentStep
        when (currentStep) {
            ResetStep.CODE -> _uiState.update { it.copy(currentStep = ResetStep.EMAIL, error = null) }
            ResetStep.PASSWORD -> _uiState.update { it.copy(currentStep = ResetStep.CODE, error = null) }
            else -> {}
        }
    }

    fun reset() {
        _uiState.value = ForgotPasswordState()
    }
}

