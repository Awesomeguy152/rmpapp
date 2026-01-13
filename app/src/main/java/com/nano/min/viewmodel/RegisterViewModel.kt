package com.nano.min.viewmodel

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.nano.min.R
import com.nano.min.fcm.MyFirebaseMessagingService
import com.nano.min.network.AuthService
import com.nano.min.network.ErrorResponse
import com.nano.min.util.EmailValidationError
import com.nano.min.util.PasswordValidationError
import com.nano.min.util.ValidationUtils
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
    val emailError: EmailValidationError? = null,
    val passwordError: PasswordValidationError? = null,
    val isRegisterSuccessful: Boolean = false
)

class RegisterViewModel(
    private val application: Application,
    private val authService: AuthService
) : ViewModelRes(application) {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

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

    fun register() {
        if (!validateInput()) return
        
        val currentState = _uiState.value

        viewModelScope.launch {
            _uiState.value = currentState.copy(isLoading = true, error = null)
            try {
                val registered = authService.register(currentState.email.trim(), currentState.password)
                
                // Если регистрация не удалась - возможно пользователь уже существует, пробуем залогиниться
                if (!registered) {
                    val loggedIn = authService.login(currentState.email.trim(), currentState.password)
                    if (loggedIn) {
                        MyFirebaseMessagingService.registerToken(application, authService)
                        _uiState.value = currentState.copy(isLoading = false, isRegisterSuccessful = true)
                    } else {
                        _uiState.value = currentState.copy(
                            isLoading = false,
                            error = getString(R.string.register_failed)
                        )
                    }
                    return@launch
                }

                val loggedIn = authService.login(currentState.email.trim(), currentState.password)
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
                // При ошибке регистрации (например, пользователь уже существует) - пробуем залогиниться
                val loggedIn = try {
                    authService.login(currentState.email.trim(), currentState.password)
                } catch (loginEx: Exception) {
                    false
                }
                
                if (loggedIn) {
                    MyFirebaseMessagingService.registerToken(application, authService)
                    _uiState.value = currentState.copy(isLoading = false, isRegisterSuccessful = true)
                } else {
                    val serverError = runCatching {
                        Json.decodeFromString<ErrorResponse>(e.response.bodyAsText())
                    }.getOrNull()?.error

                    _uiState.value = currentState.copy(
                        isLoading = false,
                        error = serverError ?: getString(R.string.register_failed)
                    )
                }
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
