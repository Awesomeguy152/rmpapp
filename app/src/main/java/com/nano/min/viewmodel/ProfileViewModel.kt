package com.nano.min.viewmodel

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.nano.min.network.AuthService
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val username: String = "",
    val email: String = "",
    val displayName: String = "",
    val bio: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isEditing: Boolean = false
)

sealed interface ProfileEvent {
    data object SaveSuccess : ProfileEvent
}

class ProfileViewModel(
    application: Application,
    private val authService: AuthService,
) : ViewModelRes(application) {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _events = Channel<ProfileEvent>()
    val events = _events.receiveAsFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val me = authService.me()
                if (me != null) {
                    _uiState.value = _uiState.value.copy(
                        username = me.username ?: "",
                        email = me.email ?: "",
                        displayName = me.displayName ?: "",
                        bio = me.bio ?: "",
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Failed to load profile")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Unknown error")
            }
        }
    }

    fun onUsernameChange(value: String) {
        _uiState.value = _uiState.value.copy(username = value)
    }

    fun onDisplayNameChange(value: String) {
        _uiState.value = _uiState.value.copy(displayName = value)
    }

    fun onBioChange(value: String) {
        _uiState.value = _uiState.value.copy(bio = value)
    }
    
    fun toggleEditMode() {
        _uiState.value = _uiState.value.copy(isEditing = !_uiState.value.isEditing)
    }

    fun saveProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val state = _uiState.value
                val updated = authService.updateProfile(
                    username = state.username,
                    displayName = state.displayName,
                    bio = state.bio
                )
                if (updated != null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isEditing = false,
                        username = updated.username ?: "",
                        displayName = updated.displayName ?: "",
                        bio = updated.bio ?: ""
                    )
                    _events.send(ProfileEvent.SaveSuccess)
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Failed to update profile")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Unknown error")
            }
        }
    }
    
    fun logout() {
        authService.logout()
    }
}
