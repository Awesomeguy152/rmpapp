package com.nano.min.viewmodel

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.nano.min.R
import com.nano.min.data.repository.MeetingRepository
import com.nano.min.network.ExtractedMeetingDto
import com.nano.min.network.MeetingDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class MeetingsUiState(
    val meetings: List<MeetingDto> = emptyList(),
    val extractedMeetings: List<ExtractedMeetingDto> = emptyList(),
    val isLoading: Boolean = false,
    val isExtracting: Boolean = false,
    val errorMessageResId: Int? = null,
    val errorMessage: String? = null,
    val selectedMeeting: MeetingDto? = null,
    val showExtractedDialog: Boolean = false
)

class MeetingsViewModel(
    application: Application,
    private val meetingRepository: MeetingRepository
) : ViewModelRes(application) {

    private val _uiState = MutableStateFlow(MeetingsUiState())
    val uiState: StateFlow<MeetingsUiState> = _uiState.asStateFlow()

    private val dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
        .withZone(ZoneId.systemDefault())

    init {
        loadMeetings()
    }

    /**
     * Загрузить все встречи пользователя
     */
    fun loadMeetings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessageResId = null) }
            
            meetingRepository.getMeetings()
                .onSuccess { meetings ->
                    _uiState.update { 
                        it.copy(
                            meetings = meetings.sortedBy { m -> m.scheduledAt },
                            isLoading = false
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Unknown error"
                        )
                    }
                }
        }
    }

    /**
     * Извлечь предложения встреч из чата с помощью AI
     */
    fun extractMeetings(conversationId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExtracting = true, extractedMeetings = emptyList()) }
            
            meetingRepository.extractMeetings(conversationId)
                .onSuccess { extracted ->
                    _uiState.update { 
                        it.copy(
                            extractedMeetings = extracted,
                            isExtracting = false,
                            showExtractedDialog = extracted.isNotEmpty()
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { 
                        it.copy(
                            isExtracting = false,
                            errorMessage = error.message
                        )
                    }
                }
        }
    }

    /**
     * Создать встречу из предложения AI
     */
    fun createMeetingFromExtracted(
        conversationId: String,
        extracted: ExtractedMeetingDto
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            val scheduledAt = extracted.dateTime ?: Instant.now().plusSeconds(3600).toString()
            
            meetingRepository.createMeeting(
                conversationId = conversationId,
                title = extracted.title,
                description = extracted.description,
                scheduledAt = scheduledAt,
                location = extracted.location,
                aiGenerated = true,
                sourceMessageId = extracted.sourceMessageId
            )
                .onSuccess { meeting ->
                    _uiState.update { state ->
                        state.copy(
                            meetings = (state.meetings + meeting).sortedBy { it.scheduledAt },
                            isLoading = false,
                            extractedMeetings = state.extractedMeetings - extracted,
                            showExtractedDialog = (state.extractedMeetings - extracted).isNotEmpty()
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message
                        )
                    }
                }
        }
    }

    /**
     * Создать встречу вручную (без привязки к чату)
     */
    fun createMeetingManually(
        title: String,
        description: String?,
        scheduledAt: String,
        location: String?
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            // Для ручных встреч используем специальный ID "personal"
            // или можно создать персональный чат для встреч
            meetingRepository.createMeetingWithoutConversation(
                title = title,
                description = description,
                scheduledAt = scheduledAt,
                location = location
            )
                .onSuccess { meeting ->
                    _uiState.update { state ->
                        state.copy(
                            meetings = (state.meetings + meeting).sortedBy { it.scheduledAt },
                            isLoading = false
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message
                        )
                    }
                }
        }
    }

    /**
     * Принять приглашение на встречу
     */
    fun acceptMeeting(meetingId: String) {
        respondToMeeting(meetingId, true)
    }

    /**
     * Отклонить приглашение на встречу
     */
    fun declineMeeting(meetingId: String) {
        respondToMeeting(meetingId, false)
    }

    private fun respondToMeeting(meetingId: String, accept: Boolean) {
        viewModelScope.launch {
            meetingRepository.respondToMeeting(meetingId, accept)
                .onSuccess {
                    loadMeetings() // Перезагрузить список
                }
                .onFailure { error ->
                    _uiState.update { 
                        it.copy(errorMessage = error.message)
                    }
                }
        }
    }

    /**
     * Удалить встречу
     */
    fun deleteMeeting(meetingId: String) {
        viewModelScope.launch {
            meetingRepository.deleteMeeting(meetingId)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            meetings = state.meetings.filter { it.id != meetingId }
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { 
                        it.copy(errorMessage = error.message)
                    }
                }
        }
    }
    
    /**
     * Обновить встречу
     */
    fun updateMeeting(
        meetingId: String,
        title: String,
        description: String?,
        scheduledAt: String,
        location: String?
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            meetingRepository.updateMeeting(
                meetingId = meetingId,
                title = title,
                description = description,
                scheduledAt = scheduledAt,
                location = location
            )
                .onSuccess { updatedMeeting ->
                    _uiState.update { state ->
                        state.copy(
                            meetings = state.meetings.map { 
                                if (it.id == meetingId) updatedMeeting else it 
                            }.sortedBy { it.scheduledAt },
                            isLoading = false
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message
                        )
                    }
                }
        }
    }

    /**
     * Выбрать встречу для просмотра деталей
     */
    fun selectMeeting(meeting: MeetingDto?) {
        _uiState.update { it.copy(selectedMeeting = meeting) }
    }

    /**
     * Закрыть диалог с предложениями AI
     */
    fun dismissExtractedDialog() {
        _uiState.update { it.copy(showExtractedDialog = false, extractedMeetings = emptyList()) }
    }

    /**
     * Очистить ошибку
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null, errorMessageResId = null) }
    }
    
    /**
     * Очистить состояние (при logout)
     */
    fun clearState() {
        _uiState.value = MeetingsUiState()
    }

    /**
     * Форматировать дату для отображения
     */
    fun formatDate(isoDate: String): String {
        return try {
            val instant = Instant.parse(isoDate)
            dateFormatter.format(instant)
        } catch (e: Exception) {
            isoDate
        }
    }
}
