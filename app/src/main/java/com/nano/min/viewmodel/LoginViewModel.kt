package com.nano.min.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nano.min.network.AuthService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLoginSuccessful: Boolean = false
)

class LoginViewModel(
    private val authService: AuthService
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(email: String) {
        _uiState.value = _uiState.value.copy(email = email, error = null)
    }

    fun onPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(password = password, error = null)
    }

    fun login() {
        val currentState = _uiState.value
        if (currentState.email.isEmpty() || currentState.password.isEmpty()) {
            _uiState.value = currentState.copy(error = "Email and password are required")
            return
        }

        viewModelScope.launch {
            _uiState.value = currentState.copy(isLoading = true, error = null)
            try {
                val success = authService.login(currentState.email, currentState.password)
                _uiState.value = if (success) {
                    currentState.copy(isLoading = false, isLoginSuccessful = true)
                } else {
                    currentState.copy(isLoading = false, error = "Login failed")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = currentState.copy(
                    isLoading = false,
                    error = e.localizedMessage ?: e.message ?: "Unknown error"
                )
            }
        }
    }

    fun resetLoginSuccess() {
        _uiState.value = _uiState.value.copy(isLoginSuccessful = false)
    }
}
