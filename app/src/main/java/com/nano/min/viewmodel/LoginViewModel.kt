package com.nano.min.viewmodel

import android.app.Application
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nano.min.R
import com.nano.min.fcm.MyFirebaseMessagingService
import com.nano.min.network.AuthService
import com.nano.min.util.EmailValidationError
import com.nano.min.util.PasswordValidationError
import com.nano.min.util.ValidationUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val emailError: EmailValidationError? = null,
    val passwordError: PasswordValidationError? = null,
    val isLoginSuccessful: Boolean = false
)

class LoginViewModel(
    private val application: Application,
    private val authService: AuthService
) : ViewModelRes(application) {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(email: String) {
        _uiState.value = _uiState.value.copy(
            email = email, 
            error = null,
            emailError = null
        )
    }

    fun onPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(
            password = password, 
            error = null,
            passwordError = null
        )
    }
    
    private fun validateInput(): Boolean {
        val currentState = _uiState.value
        val emailError = ValidationUtils.getEmailError(currentState.email)
        val passwordError = ValidationUtils.getPasswordError(currentState.password)
        
        if (emailError != null || passwordError != null) {
            _uiState.value = currentState.copy(
                emailError = emailError,
                passwordError = passwordError
            )
            return false
        }
        return true
    }

    fun login() {
        if (!validateInput()) return
        
        val currentState = _uiState.value

        viewModelScope.launch {
            _uiState.value = currentState.copy(isLoading = true, error = null)
            try {
                val success = authService.login(currentState.email.trim(), currentState.password)
                if (success) {
                    // Регистрируем FCM токен после успешного логина
                    MyFirebaseMessagingService.registerToken(application, authService)
                    
                    _uiState.value = currentState.copy(isLoading = false, isLoginSuccessful = true, error = null)
                } else {
                    _uiState.value = currentState.copy(isLoading = false, error = getString(R.string.login_failed))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = currentState.copy(
                    isLoading = false,
                    error = e.localizedMessage ?: e.message ?: getString(R.string.login_failed)
                )
            }
        }
    }

    fun resetLoginSuccess() {
        _uiState.value = _uiState.value.copy(isLoginSuccessful = false)
    }
}
