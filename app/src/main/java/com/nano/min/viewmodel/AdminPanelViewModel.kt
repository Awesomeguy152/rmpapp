package com.nano.min.viewmodel

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.nano.min.network.AdminUserDto
import com.nano.min.network.AuthService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AdminPanelUiState(
    val users: List<AdminUserDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

class AdminPanelViewModel(
    application: Application,
    private val authService: AuthService
) : ViewModelRes(application) {
    
    private val _uiState = MutableStateFlow(AdminPanelUiState())
    val uiState: StateFlow<AdminPanelUiState> = _uiState.asStateFlow()
    
    init {
        loadUsers()
    }
    
    fun loadUsers() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val users = authService.getAllUsers()
                if (users != null) {
                    _uiState.value = _uiState.value.copy(
                        users = users,
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Не удалось загрузить пользователей"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Неизвестная ошибка"
                )
            }
        }
    }
    
    fun toggleUserBlocked(userId: String, currentlyBlocked: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val success = authService.setUserBlocked(userId, !currentlyBlocked)
                if (success) {
                    _uiState.value = _uiState.value.copy(
                        successMessage = if (currentlyBlocked) "Пользователь разблокирован" else "Пользователь заблокирован"
                    )
                    loadUsers() // Перезагружаем список
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Не удалось изменить статус блокировки"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Неизвестная ошибка"
                )
            }
        }
    }
    
    fun toggleUserRole(userId: String, currentRole: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val newRole = if (currentRole == "ADMIN") "USER" else "ADMIN"
                val success = authService.setUserRole(userId, newRole)
                if (success) {
                    _uiState.value = _uiState.value.copy(
                        successMessage = "Роль изменена на $newRole"
                    )
                    loadUsers()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Не удалось изменить роль"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Неизвестная ошибка"
                )
            }
        }
    }
    
    fun deleteUser(userId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val success = authService.deleteUser(userId)
                if (success) {
                    _uiState.value = _uiState.value.copy(
                        successMessage = "Пользователь удалён"
                    )
                    loadUsers()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Не удалось удалить пользователя"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Неизвестная ошибка"
                )
            }
        }
    }
    
    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null)
    }
}
