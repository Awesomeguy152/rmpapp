package com.nano.min.viewmodel

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.nano.min.R
import com.nano.min.fcm.MyFirebaseMessagingService
import com.nano.min.network.AuthService
import com.nano.min.network.ErrorResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json

data class RegisterUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isRegisterSuccessful: Boolean = false
)

class RegisterViewModel(
    private val application: Application,
    private val authService: AuthService
) : ViewModelRes(application) {

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
            _uiState.value = currentState.copy(error = getString(R.string.register_empty_fields))
            return
        }

        viewModelScope.launch {
            _uiState.value = currentState.copy(isLoading = true, error = null)
            try {
                val registered = authService.register(currentState.email, currentState.password)
                if (!registered) {
                    _uiState.value = currentState.copy(
                        isLoading = false,
                        error = getString(R.string.register_failed)
                    )
                    return@launch
                }

                val loggedIn = authService.login(currentState.email, currentState.password)
                if (loggedIn) {
                    // Регистрируем FCM токен после успешной регистрации и логина
                    MyFirebaseMessagingService.registerToken(application, authService)
                    
                    _uiState.value = currentState.copy(isLoading = false, isRegisterSuccessful = true)
                } else {
                    _uiState.value = currentState.copy(
                        isLoading = false,
                        error = getString(R.string.register_auto_login_failed)
                    )
                }
            } catch (e: ClientRequestException) {
                val serverError = runCatching {
                    Json.decodeFromString<ErrorResponse>(e.response.bodyAsText())
                }.getOrNull()?.error

                _uiState.value = currentState.copy(
                    isLoading = false,
                    error = serverError ?: getString(R.string.register_failed)
                )
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = currentState.copy(
                    isLoading = false,
                    error = e.localizedMessage ?: e.message ?: getString(R.string.register_failed)
                )
            }
        }
    }

    fun resetRegisterSuccess() {
        _uiState.value = _uiState.value.copy(isRegisterSuccessful = false)
    }
}
