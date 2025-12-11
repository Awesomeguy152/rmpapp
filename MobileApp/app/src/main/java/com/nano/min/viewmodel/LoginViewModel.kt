package com.nano.min.viewmodel

import android.app.Application
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nano.min.R
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
    application: Application,
    private val authService: AuthService
) : ViewModelRes(application) {

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
            _uiState.value = currentState.copy(error = getString(R.string.login_empty_fields))
            return
        }

        viewModelScope.launch {
            _uiState.value = currentState.copy(isLoading = true, error = null)
            try {
                val success = authService.login(currentState.email, currentState.password)
                _uiState.value = if (success) {
                    currentState.copy(isLoading = false, isLoginSuccessful = true, error = null)
                } else {
                    currentState.copy(isLoading = false, error = getString(R.string.login_failed))
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
