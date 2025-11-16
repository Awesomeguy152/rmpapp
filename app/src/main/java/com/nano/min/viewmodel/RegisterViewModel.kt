package com.nano.min.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nano.min.network.AuthService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RegisterUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isRegisterSuccessful: Boolean = false
)

class RegisterViewModel(
    private val authService: AuthService
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onEmailChange(email: String) {
        _uiState.value = _uiState.value.copy(email = email, error = null)
    }

    fun onPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(password = password, error = null)
    }

    fun register() {
        val currentState = _uiState.value
        if (currentState.email.isEmpty() || currentState.password.isEmpty()) {
            _uiState.value = currentState.copy(error = "Email and password are required")
            return
        }

        viewModelScope.launch {
            _uiState.value = currentState.copy(isLoading = true, error = null)
            try {
                val success = authService.register(currentState.email, currentState.password)
                _uiState.value = if (success) {
                    currentState.copy(isLoading = false, isRegisterSuccessful = true)
                } else {
                    currentState.copy(isLoading = false, error = "Registration failed")
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

    fun resetRegisterSuccess() {
        _uiState.value = _uiState.value.copy(isRegisterSuccessful = false)
    }
}
