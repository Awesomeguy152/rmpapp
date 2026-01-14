package com.nano.min.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.nano.min.R
import com.nano.min.network.AuthService
import com.nano.min.network.ChatService
import com.nano.min.network.ConversationSummaryDto
import com.nano.min.network.ErrorResponse
import com.nano.min.network.MessageDto
import com.nano.min.network.MeResponse
import com.nano.min.network.ChatEventDto
import com.nano.min.network.UserProfileDto
import com.nano.min.network.MessageAttachmentPayload
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

import com.nano.min.network.MessageStatus
import com.nano.min.network.PinnedMessageDto
import com.nano.min.network.ConversationMemberDto
import com.nano.min.network.ConversationType
import com.nano.min.data.repository.ChatRepository
import com.nano.min.data.repository.MeetingRepository
import com.nano.min.data.local.ConversationEntity
import com.nano.min.data.local.MessageEntity

data class ConversationsUiState(
    val profileId: String? = null,
    val profileEmail: String? = null,
    val profileRole: String? = null,
    val profileCreatedAt: String? = null,
    val profileDisplayName: String? = null,
    val profileUsername: String? = null,
    val conversations: List<ConversationItem> = emptyList(),
    val archivedConversations: List<ConversationItem> = emptyList(),
    val showArchivedChats: Boolean = false,
    val isLoading: Boolean = false,
    val isSearchingContacts: Boolean = false,
    val contactSuggestions: List<ContactSuggestion> = emptyList(),
    val error: String? = null
)

data class ConversationDetailUiState(
    val conversation: ConversationItem? = null,
    val messages: List<MessageItem> = emptyList(),
    val pinnedMessages: List<PinnedMessageDto> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMoreMessages: Boolean = true,
    val isSending: Boolean = false,
    val messageInput: String = "",
    val pendingAttachments: List<PendingAttachment> = emptyList(),
    val error: String? = null,
    val typingUsers: Set<String> = emptySet(),
    val replyingToMessage: MessageItem? = null,
    val isSearchMode: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<MessageItem> = emptyList(),
    val isSearching: Boolean = false,
    val isRecordingVoice: Boolean = false,
    val voiceRecordingStartTime: Long = 0L,
    val showPinnedMessages: Boolean = false,
    // AI Meeting extraction
    val isExtractingMeetings: Boolean = false,
    val extractedMeetings: List<ExtractedMeetingInfo> = emptyList(),
    val showExtractedMeetingsDialog: Boolean = false
)

data class ExtractedMeetingInfo(
    val title: String,
    val description: String?,
    val dateTime: String?,
    val location: String?,
    val confidence: Double,
    val sourceMessageId: String?
)

data class PendingAttachment(
    val uri: Uri,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long
)

data class ConversationItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val unreadCount: Long,
    val lastMessageTime: String?,
    val members: List<MemberInfo>,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isMuted: Boolean = false,
    val raw: ConversationSummaryDto
)

data class ReplyInfo(
    val id: String,
    val senderName: String,
    val body: String
)

data class MessageItem(
    val id: String,
    val text: String,
    val timestamp: String,
    val dateHeader: String,
    val isMine: Boolean,
    val senderName: String? = null,
    val reactions: List<ReactionItem> = emptyList(),
    val myReaction: String? = null,
    val status: MessageStatus = MessageStatus.SENT,
    val replyTo: ReplyInfo? = null,
    val attachments: List<AttachmentItem> = emptyList()
)

data class AttachmentItem(
    val id: String,
    val fileName: String,
    val contentType: String,
    val dataBase64: String
)

data class ReactionItem(
    val emoji: String,
    val count: Long,
    val reactedByMe: Boolean
)

data class ContactSuggestion(
    val id: String,
    val email: String,
    val role: String,
    val joinedAt: String,
    val name: String? = null
)

data class MemberInfo(
    val id: String,
    val joinedAt: String,
    val name: String? = null,
    val avatarUrl: String? = null,
    val isOnline: Boolean = false // TODO: Get from WebSocket presence updates
)

sealed interface ChatsEvent {
    data class ShowMessage(val message: String) : ChatsEvent
    object SessionExpired : ChatsEvent
}

class ChatsViewModel(
    application: Application,
    private val authService: AuthService,
    private val chatService: ChatService,
    private val chatRepository: ChatRepository,
    private val meetingRepository: MeetingRepository
) : ViewModelRes(application) {

    private val _conversationState = MutableStateFlow(ConversationsUiState(isLoading = true))
    val conversationState: StateFlow<ConversationsUiState> = _conversationState.asStateFlow()

    private val _detailState = MutableStateFlow(ConversationDetailUiState())
    val detailState: StateFlow<ConversationDetailUiState> = _detailState.asStateFlow()
    
    // Состояние сети
    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    private val _events = MutableSharedFlow<ChatsEvent>()
    val events: SharedFlow<ChatsEvent> = _events.asSharedFlow()

    private var me: MeResponse? = null
    private var selectedConversationId: String? = null
    private var contactSearchJob: Job? = null
    private var updatesJob: Job? = null

    private val json = Json { ignoreUnknownKeys = true }
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM HH:mm")
    private val dateOnlyFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    val availableReactions = listOf("👍", "❤️", "😂", "😮", "😢", "👎")

    init {
        viewModelScope.launch {
            loadProfile()
            refreshConversationsInternal(selectConversation = null)
        }
        startRealtimeUpdates()
    }

    fun refreshConversations(selectConversation: String? = selectedConversationId) {
        viewModelScope.launch {
            refreshConversationsInternal(selectConversation)
        }
    }

    fun selectConversation(conversationId: String) {
        val conversation = _conversationState.value.conversations.find { it.id == conversationId }
        if (conversation == null) {
            selectedConversationId = null
            _detailState.value = ConversationDetailUiState()
            return
        }
        selectedConversationId = conversationId
        _detailState.value = ConversationDetailUiState(conversation = conversation, isLoading = true)
        viewModelScope.launch {
            loadMessagesInternal(conversationId, conversation)
            // Загружаем закреплённые сообщения
            loadPinnedMessages(conversationId)
        }
    }

    fun clearConversationSelection() {
        selectedConversationId = null
        _detailState.value = ConversationDetailUiState()
    }

    private var typingJob: Job? = null

    fun updateMessageInput(value: String) {
        _detailState.update { it.copy(messageInput = value) }
        
        val conversationId = selectedConversationId ?: return
        
        typingJob?.cancel()
        typingJob = viewModelScope.launch {
            try {
                chatService.sendTyping(conversationId, true)
                delay(3000)
                chatService.sendTyping(conversationId, false)
            } catch (e: Exception) {
                // ignore typing errors
            }
        }
    }

    fun sendMessage() {
        val conversationId = selectedConversationId ?: return
        val currentState = _detailState.value
        val conversation = currentState.conversation ?: return
        val message = currentState.messageInput.trim()
        val pendingAttachments = currentState.pendingAttachments
        
        if (message.isEmpty() && pendingAttachments.isEmpty()) {
            _detailState.update { it.copy(error = getString(R.string.error_empty_message)) }
            return
        }
        
        val replyToId = currentState.replyingToMessage?.id

        viewModelScope.launch {
            _detailState.update { it.copy(isSending = true, error = null) }
            try {
                // Convert pending attachments to base64
                val attachmentPayloads = pendingAttachments.mapNotNull { attachment ->
                    try {
                        val inputStream = getApplication<Application>().contentResolver.openInputStream(attachment.uri)
                        val bytes = inputStream?.readBytes() ?: return@mapNotNull null
                        inputStream.close()
                        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        MessageAttachmentPayload(
                            fileName = attachment.fileName,
                            contentType = attachment.contentType,
                            dataBase64 = base64
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                
                val dto = chatService.sendMessage(
                    conversationId = conversationId, 
                    body = message,
                    attachments = attachmentPayloads,
                    replyToMessageId = replyToId
                )
                val meId = me?.id
                val messageItem = dto.toMessageItem(meId)
                _detailState.update {
                    it.copy(
                        isSending = false,
                        messageInput = "",
                        pendingAttachments = emptyList(),
                        messages = it.messages + messageItem,
                        replyingToMessage = null
                    )
                }
                refreshConversations()
            } catch (t: Throwable) {
                _detailState.update { it.copy(isSending = false) }
                handleNetworkError(t, R.string.error_send_message)
            }
        }
    }
    
    fun addAttachment(uri: Uri, fileName: String, contentType: String, sizeBytes: Long) {
        val attachment = PendingAttachment(uri, fileName, contentType, sizeBytes)
        _detailState.update { it.copy(pendingAttachments = it.pendingAttachments + attachment) }
    }
    
    fun removeAttachment(uri: Uri) {
        _detailState.update { state ->
            state.copy(pendingAttachments = state.pendingAttachments.filter { it.uri != uri })
        }
    }
    
    fun clearAttachments() {
        _detailState.update { it.copy(pendingAttachments = emptyList()) }
    }
    
    fun sendVoiceMessage(audioData: ByteArray, durationMs: Long) {
        val conversationId = selectedConversationId ?: return
        
        viewModelScope.launch {
            _detailState.update { it.copy(isSending = true, error = null) }
            try {
                val base64 = android.util.Base64.encodeToString(audioData, android.util.Base64.NO_WRAP)
                val fileName = "voice_${System.currentTimeMillis()}.m4a"
                
                val dto = chatService.sendMessage(
                    conversationId = conversationId,
                    body = getString(R.string.voice_message),
                    attachments = listOf(
                        MessageAttachmentPayload(
                            fileName = fileName,
                            contentType = "audio/mp4",
                            dataBase64 = base64
                        )
                    )
                )
                val meId = me?.id
                val messageItem = dto.toMessageItem(meId)
                _detailState.update {
                    it.copy(
                        isSending = false,
                        messages = it.messages + messageItem
                    )
                }
                refreshConversations()
            } catch (t: Throwable) {
                _detailState.update { it.copy(isSending = false) }
                handleNetworkError(t, R.string.error_send_message)
            }
        }
    }
    
    fun startVoiceRecording() {
        _detailState.update { 
            it.copy(
                isRecordingVoice = true,
                voiceRecordingStartTime = System.currentTimeMillis()
            ) 
        }
    }
    
    fun stopVoiceRecording() {
        _detailState.update { it.copy(isRecordingVoice = false) }
    }
    
    fun cancelVoiceRecording() {
        _detailState.update { 
            it.copy(
                isRecordingVoice = false,
                voiceRecordingStartTime = 0L
            ) 
        }
    }
    
    fun startReplyTo(message: MessageItem) {
        _detailState.update { it.copy(replyingToMessage = message) }
    }
    
    fun cancelReply() {
        _detailState.update { it.copy(replyingToMessage = null) }
    }

    fun toggleSearchMode() {
        _detailState.update { 
            it.copy(
                isSearchMode = !it.isSearchMode, 
                searchQuery = "",
                searchResults = emptyList()
            ) 
        }
    }

    fun updateSearchQuery(query: String) {
        _detailState.update { it.copy(searchQuery = query) }
        if (query.length >= 2) {
            searchInChat(query)
        } else {
            _detailState.update { it.copy(searchResults = emptyList()) }
        }
    }

    private var searchJob: Job? = null

    private fun searchInChat(query: String) {
        val conversationId = selectedConversationId ?: return
        val conversation = _detailState.value.conversation ?: return

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _detailState.update { it.copy(isSearching = true) }
            delay(300) // Debounce
            try {
                val meId = me?.id
                val results = chatService.getMessages(conversationId, limit = 50, offset = 0, query = query)
                _detailState.update { state ->
                    state.copy(
                        searchResults = results.map { it.toMessageItem(meId, conversation.members) },
                        isSearching = false
                    )
                }
            } catch (t: Throwable) {
                _detailState.update { it.copy(isSearching = false) }
            }
        }
    }

    fun clearSearch() {
        _detailState.update { 
            it.copy(
                isSearchMode = false,
                searchQuery = "",
                searchResults = emptyList()
            ) 
        }
    }

    fun editMessage(messageId: String, newText: String) {
        val conversationId = selectedConversationId ?: return
        val trimmed = newText.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            try {
                val updated = chatService.editMessage(messageId, trimmed)
                val meId = me?.id
                val updatedItem = updated.toMessageItem(meId)
                applyUpdatedMessage(updatedItem)
            } catch (t: Throwable) {
                handleNetworkError(t, R.string.error_send_message)
            }
        }
    }

    fun deleteMessage(messageId: String) {
        val conversationId = selectedConversationId ?: return

        viewModelScope.launch {
            try {
                val updated = chatService.deleteMessage(messageId)
                val meId = me?.id
                val updatedItem = updated.toMessageItem(meId)
                applyUpdatedMessage(updatedItem)
            } catch (t: Throwable) {
                handleNetworkError(t, R.string.error_send_message)
            }
        }
    }

    // === Conversation Actions ===

    fun pinConversation(conversationId: String) {
        viewModelScope.launch {
            try {
                val updated = chatService.pinConversation(conversationId)
                updateConversationInList(updated)
            } catch (t: Throwable) {
                handleNetworkError(t, R.string.error_generic)
            }
        }
    }

    fun unpinConversation(conversationId: String) {
        viewModelScope.launch {
            try {
                val updated = chatService.unpinConversation(conversationId)
                updateConversationInList(updated)
            } catch (t: Throwable) {
                handleNetworkError(t, R.string.error_generic)
            }
        }
    }

    fun archiveConversation(conversationId: String) {
        viewModelScope.launch {
            try {
                val updated = chatService.archiveConversation(conversationId)
                // Удаляем из списка (архивированные не показываются по умолчанию)
                _conversationState.update { state ->
                    state.copy(conversations = state.conversations.filter { it.id != conversationId })
                }
                _events.emit(ChatsEvent.ShowMessage(getString(R.string.conversation_archived)))
                // Обновляем список архивированных, если открыт
                if (_conversationState.value.showArchivedChats) {
                    loadArchivedConversations()
                }
            } catch (t: Throwable) {
                handleNetworkError(t, R.string.error_generic)
            }
        }
    }

    fun unarchiveConversation(conversationId: String) {
        viewModelScope.launch {
            try {
                val updated = chatService.unarchiveConversation(conversationId)
                // Убираем из архивных
                _conversationState.update { state ->
                    state.copy(archivedConversations = state.archivedConversations.filter { it.id != conversationId })
                }
                _events.emit(ChatsEvent.ShowMessage(getString(R.string.conversation_unarchived)))
                // Обновляем основной список
                refreshConversations()
            } catch (t: Throwable) {
                handleNetworkError(t, R.string.error_generic)
            }
        }
    }
    
    fun toggleArchivedChats() {
        val newShowArchived = !_conversationState.value.showArchivedChats
        _conversationState.update { it.copy(showArchivedChats = newShowArchived) }
        if (newShowArchived) {
            loadArchivedConversations()
        }
    }
    
    fun loadArchivedConversations() {
        viewModelScope.launch {
            _conversationState.update { it.copy(isLoading = true) }
            try {
                val summaries = chatService.listConversations(includeArchived = true)
                val meId = me?.id
                val archivedItems = summaries
                    .filter { it.archivedAt != null }
                    .map { it.toConversationItem(meId) }
                    .sortedByDescending { it.lastMessageTime }
                _conversationState.update { it.copy(isLoading = false, archivedConversations = archivedItems) }
            } catch (t: Throwable) {
                _conversationState.update { it.copy(isLoading = false) }
                handleNetworkError(t, R.string.error_load_conversations)
            }
        }
    }

    fun muteConversation(conversationId: String, until: String? = null) {
        viewModelScope.launch {
            try {
                val updated = chatService.muteConversation(conversationId, until)
                updateConversationInList(updated)
                _events.emit(ChatsEvent.ShowMessage(getString(R.string.conversation_muted)))
            } catch (t: Throwable) {
                handleNetworkError(t, R.string.error_generic)
            }
        }
    }

    fun unmuteConversation(conversationId: String) {
        viewModelScope.launch {
            try {
                val updated = chatService.unmuteConversation(conversationId)
                updateConversationInList(updated)
                _events.emit(ChatsEvent.ShowMessage(getString(R.string.conversation_unmuted)))
            } catch (t: Throwable) {
                handleNetworkError(t, R.string.error_generic)
            }
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            try {
                chatService.deleteConversation(conversationId)
                // Удаляем из списка
                _conversationState.update { state ->
                    state.copy(conversations = state.conversations.filter { it.id != conversationId })
                }
                // Если это выбранный чат, сбрасываем
                if (selectedConversationId == conversationId) {
                    selectedConversationId = null
                    _detailState.value = ConversationDetailUiState()
                }
                _events.emit(ChatsEvent.ShowMessage(getString(R.string.conversation_deleted)))
            } catch (t: Throwable) {
                handleNetworkError(t, R.string.error_generic)
            }
        }
    }

    // ====== Закреплённые сообщения ======
    
    fun loadPinnedMessages(conversationId: String) {
        viewModelScope.launch {
            try {
                val pinned = chatService.getPinnedMessages(conversationId)
                _detailState.update { it.copy(pinnedMessages = pinned) }
            } catch (t: Throwable) {
                // Ошибки загрузки закреплённых можно игнорировать
            }
        }
    }
    
    fun pinMessage(messageId: String) {
        val conversationId = selectedConversationId ?: return
        viewModelScope.launch {
            try {
                val pinned = chatService.pinMessage(conversationId, messageId)
                _detailState.update { state ->
                    state.copy(pinnedMessages = state.pinnedMessages + pinned)
                }
                _events.emit(ChatsEvent.ShowMessage(getString(R.string.message_pinned)))
            } catch (t: Throwable) {
                handleNetworkError(t, R.string.error_generic)
            }
        }
    }
    
    fun unpinMessage(messageId: String) {
        val conversationId = selectedConversationId ?: return
        viewModelScope.launch {
            try {
                chatService.unpinMessage(conversationId, messageId)
                _detailState.update { state ->
                    state.copy(pinnedMessages = state.pinnedMessages.filter { it.messageId != messageId })
                }
                _events.emit(ChatsEvent.ShowMessage(getString(R.string.message_unpinned)))
            } catch (t: Throwable) {
                handleNetworkError(t, R.string.error_generic)
            }
        }
    }
    
    fun togglePinnedMessagesPanel() {
        _detailState.update { it.copy(showPinnedMessages = !it.showPinnedMessages) }
    }

    private fun updateConversationInList(updated: ConversationSummaryDto) {
        val meId = me?.id
        val item = updated.toConversationItem(meId)
        _conversationState.update { state ->
            val newList = state.conversations.map { conv ->
                if (conv.id == updated.id) item else conv
            }
            state.copy(conversations = newList)
        }
    }

    fun toggleReaction(messageId: String, emoji: String) {
        val current = _detailState.value.messages.firstOrNull { it.id == messageId } ?: return
        viewModelScope.launch {
            try {
                val updated = if (current.myReaction == emoji) {
                    chatService.removeReaction(messageId)
                } else {
                    chatService.reactToMessage(messageId, emoji)
                }
                val meId = me?.id
                val updatedItem = updated.toMessageItem(meId)
                applyUpdatedMessage(updatedItem)
            } catch (t: Throwable) {
                handleNetworkError(t, R.string.error_send_message)
            }
        }
    }

    fun createConversation(memberInput: String, topic: String?) {
        val members = memberInput.split(',', ';', ' ', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        if (members.isEmpty()) {
            viewModelScope.launch {
                _events.emit(ChatsEvent.ShowMessage(getString(R.string.error_member_required)))
            }
            return
        }

        viewModelScope.launch {
            try {
                val normalizedTopic = topic?.takeIf { it.isNotBlank() }
                val resolvedMembers = resolveMemberIds(members) ?: return@launch

                if (resolvedMembers.isEmpty()) {
                    _events.emit(ChatsEvent.ShowMessage(getString(R.string.error_member_required)))
                    return@launch
                }

                val summary = if (resolvedMembers.size == 1) {
                    chatService.createDirectConversation(resolvedMembers.first(), normalizedTopic)
                } else {
                    chatService.createGroupConversation(resolvedMembers, normalizedTopic)
                }
                clearContactSuggestions()
                _events.emit(ChatsEvent.ShowMessage(getString(R.string.message_conversation_created)))
                refreshConversationsInternal(selectConversation = summary.id)
            } catch (t: Throwable) {
                handleNetworkError(t, R.string.error_create_conversation)
            }
        }
    }

    fun updateCurrentConversationTopic(newTopic: String) {
        val conversationId = selectedConversationId ?: return

        viewModelScope.launch {
            try {
                val normalizedTopic = newTopic.trim().ifBlank { null }
                chatService.updateConversationTopic(conversationId, normalizedTopic)
                _events.emit(ChatsEvent.ShowMessage(getString(R.string.message_topic_updated)))
                refreshConversationsInternal(selectConversation = conversationId)
            } catch (t: Throwable) {
                handleNetworkError(t, R.string.error_update_topic)
            }
        }
    }

    fun addMembersToCurrentConversation(memberInput: String) {
        val conversationId = selectedConversationId ?: return
        val members = memberInput.parseMembers()

        if (members.isEmpty()) {
            viewModelScope.launch {
                _events.emit(ChatsEvent.ShowMessage(getString(R.string.error_member_required)))
            }
            return
        }

        viewModelScope.launch {
            try {
                chatService.addConversationMembers(conversationId, members)
                _events.emit(ChatsEvent.ShowMessage(getString(R.string.message_members_updated)))
                refreshConversationsInternal(selectConversation = conversationId)
            } catch (t: Throwable) {
                handleNetworkError(t, R.string.error_manage_members)
            }
        }
    }

    fun removeMemberFromCurrentConversation(memberId: String) {
        val conversationId = selectedConversationId ?: return

        viewModelScope.launch {
            try {
                chatService.removeConversationMember(conversationId, memberId)
                _events.emit(ChatsEvent.ShowMessage(getString(R.string.message_members_updated)))
                refreshConversationsInternal(selectConversation = conversationId)
            } catch (t: Throwable) {
                handleNetworkError(t, R.string.error_manage_members)
            }
        }
    }

    fun leaveCurrentConversation() {
        val conversationId = selectedConversationId ?: return
        val myId = me?.id ?: return

        viewModelScope.launch {
            try {
                chatService.removeConversationMember(conversationId, myId)
                _events.emit(ChatsEvent.ShowMessage(getString(R.string.message_left_conversation)))
                clearConversationSelection()
                refreshConversationsInternal(selectConversation = null)
            } catch (t: Throwable) {
                handleNetworkError(t, R.string.error_leave_conversation)
            }
        }
    }

    fun searchContacts(query: String) {
        contactSearchJob?.cancel()

        val trimmedQuery = query.trim()

        if (trimmedQuery.isBlank()) {
            _conversationState.update {
                it.copy(contactSuggestions = emptyList(), isSearchingContacts = false)
            }
            return
        }

        if (trimmedQuery.length < 2) {
            _conversationState.update {
                it.copy(contactSuggestions = emptyList(), isSearchingContacts = false)
            }
            return
        }

        contactSearchJob = viewModelScope.launch {
            _conversationState.update { it.copy(isSearchingContacts = true) }
            delay(250)
            try {
                val suggestions = chatService.searchUsers(trimmedQuery)
                    .map { it.toSuggestion() }
                _conversationState.update {
                    it.copy(
                        contactSuggestions = suggestions,
                        isSearchingContacts = false
                    )
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _conversationState.update { it.copy(isSearchingContacts = false) }
                handleNetworkError(t, R.string.error_search_contacts)
            }
        }
    }

    fun clearContactSuggestions() {
        contactSearchJob?.cancel()
        _conversationState.update {
            it.copy(contactSuggestions = emptyList(), isSearchingContacts = false)
        }
    }

    fun logout() {
        authService.logout()
        me = null
        selectedConversationId = null
        contactSearchJob?.cancel()
        updatesJob?.cancel()
        _conversationState.value = ConversationsUiState()
        _detailState.value = ConversationDetailUiState()
    }
    
    // Перезагрузка данных после входа нового пользователя
    fun reloadAfterLogin() {
        viewModelScope.launch {
            loadProfile()
            refreshConversationsInternal(selectConversation = null)
        }
        startRealtimeUpdates()
    }

    private fun startRealtimeUpdates() {
        updatesJob?.cancel()
        updatesJob = viewModelScope.launch {
            chatService.observeChatEvents().collect { event ->
                handleRealtimeEvent(event)
            }
        }
    }

    private suspend fun handleRealtimeEvent(event: ChatEventDto) {
        when (event.type) {
            "message_created" -> handleMessageCreatedEvent(event)
            "reaction_updated" -> handleReactionUpdatedEvent(event)
            "user_typing" -> handleTypingEvent(event)
            "conversation_read" -> handleReadEvent(event)
        }
    }

    private fun handleTypingEvent(event: ChatEventDto) {
        val conversationId = event.conversationId ?: return
        if (selectedConversationId != conversationId) return
        val userId = event.readerId ?: return

        _detailState.update { state ->
            val newTyping = if (event.status == "TYPING") {
                state.typingUsers + userId
            } else {
                state.typingUsers - userId
            }
            state.copy(typingUsers = newTyping)
        }
    }

    private fun handleReadEvent(event: ChatEventDto) {
        val conversationId = event.conversationId ?: return
        if (selectedConversationId != conversationId) return
        
        if (event.status == "READ") {
             _detailState.update { state ->
                 state.copy(messages = state.messages.map { 
                     if (it.isMine && it.status != MessageStatus.READ) {
                         it.copy(status = MessageStatus.READ)
                     } else {
                         it
                     }
                 })
             }
        }
    }

    private suspend fun handleMessageCreatedEvent(event: ChatEventDto) {
        val conversationId = event.conversationId ?: return
        val currentUserId = me?.id
        if (event.recipients.isNotEmpty() && currentUserId != null && event.recipients.none { it == currentUserId }) {
            return
        }

        val currentSelection = selectedConversationId
        refreshConversationsInternal(selectConversation = currentSelection)

        if (currentSelection == conversationId && event.message != null) {
            val meId = currentUserId
            val members = _detailState.value.conversation?.members ?: emptyList()
            val incoming = event.message.toMessageItem(meId, members)
            _detailState.update { state ->
                if (state.messages.any { it.id == incoming.id }) state else state.copy(messages = state.messages + incoming)
            }
        }
    }

    private suspend fun handleReactionUpdatedEvent(event: ChatEventDto) {
        val conversationId = event.conversationId ?: return
        val currentUserId = me?.id
        if (event.recipients.isNotEmpty() && currentUserId != null && event.recipients.none { it == currentUserId }) {
            return
        }

        if (selectedConversationId != conversationId) return

        val messageId = event.message?.id ?: event.messageId ?: return
        val meId = currentUserId

        val updatedItem = when {
            event.message != null -> event.message.toMessageItem(meId)
            event.reactions != null -> {
                val base = _detailState.value.messages.firstOrNull { it.id == messageId } ?: return
                base.copy(
                    reactions = event.reactions.map { ReactionItem(it.emoji, it.count, it.reactedByMe) },
                    myReaction = event.reactions.firstOrNull { it.reactedByMe }?.emoji
                )
            }
            else -> return
        }

        applyUpdatedMessage(updatedItem)
    }

    private suspend fun loadProfile() {
        runCatching { authService.me() }
            .onSuccess { response ->
                me = response
                val createdAt = response?.createdAt?.let(::formatDate)
                val role = formatRole(response?.role)
                _conversationState.update {
                    it.copy(
                        profileId = response?.id,
                        profileEmail = response?.email,
                        profileRole = role,
                        profileCreatedAt = createdAt,
                        profileDisplayName = response?.displayName,
                        profileUsername = response?.username
                    )
                }
            }
    }

    private suspend fun refreshConversationsInternal(selectConversation: String?) {
        if (me == null) {
            loadProfile()
        }
        _conversationState.update { it.copy(isLoading = true, error = null) }
        
        val meId = me?.id
        
        // Сначала попробуем загрузить с сервера
        val result = chatRepository.refreshConversations()
        
        if (result.isSuccess) {
            _isOffline.value = false
            val summaries = result.getOrThrow()
            val items = summaries.map { it.toConversationItem(meId) }
                .sortedWith(conversationComparator())
            _conversationState.value = _conversationState.value.copy(
                isLoading = false,
                conversations = items,
                error = null
            )
            
            // Синхронизируем ожидающие сообщения
            viewModelScope.launch {
                try {
                    val synced = chatRepository.syncPendingMessages()
                    if (synced > 0) {
                        _events.emit(ChatsEvent.ShowMessage("Synced $synced messages"))
                    }
                } catch (_: Exception) {}
            }
        } else {
            // Офлайн-режим: загружаем из кеша
            _isOffline.value = true
            try {
                val cachedConversations = chatRepository.getConversations().first()
                if (cachedConversations.isNotEmpty()) {
                    val items = cachedConversations.map { it.toConversationItem(meId) }
                        .sortedWith(conversationComparator())
                    _conversationState.value = _conversationState.value.copy(
                        isLoading = false,
                        conversations = items,
                        error = getString(R.string.offline_mode)
                    )
                } else {
                    val errorMessage = result.exceptionOrNull()?.localizedMessage 
                        ?: getString(R.string.error_load_conversations)
                    _conversationState.update { it.copy(isLoading = false, error = errorMessage) }
                }
            } catch (t: Throwable) {
                val errorMessage = result.exceptionOrNull()?.localizedMessage 
                    ?: getString(R.string.error_load_conversations)
                _conversationState.update { it.copy(isLoading = false, error = errorMessage) }
            }
        }

        val targetId = selectConversation
        selectedConversationId = targetId
        if (targetId != null) {
            val targetConversation = _conversationState.value.conversations.find { it.id == targetId }
            if (targetConversation != null) {
                loadMessagesInternal(targetId, targetConversation)
            } else {
                _detailState.value = ConversationDetailUiState()
                selectedConversationId = null
            }
        } else {
            _detailState.value = _detailState.value.copy(conversation = null)
        }
    }

    private val pageSize = 30

    private suspend fun loadMessagesInternal(conversationId: String, conversation: ConversationItem) {
        if (me == null) {
            loadProfile()
        }
        _detailState.update { it.copy(conversation = conversation, isLoading = true, error = null, hasMoreMessages = true) }
        try {
            val meId = me?.id
            val messages = chatService.getMessages(conversationId, limit = pageSize, offset = 0)
            _detailState.value = ConversationDetailUiState(
                conversation = conversation,
                messages = messages.map { it.toMessageItem(meId, conversation.members) },
                isLoading = false,
                hasMoreMessages = messages.size >= pageSize,
                messageInput = ""
            )
            runCatching {
                val lastId = messages.lastOrNull()?.id
                chatService.markConversationRead(conversationId, lastId)
            }
        } catch (t: Throwable) {
            val errorMessage = t.localizedMessage ?: t.message ?: getString(R.string.error_load_messages)
            _detailState.update {
                it.copy(
                    isLoading = false,
                    error = errorMessage
                )
            }
            handleNetworkError(t, R.string.error_load_messages)
        }
    }

    fun loadMoreMessages() {
        val conversationId = selectedConversationId ?: return
        val currentState = _detailState.value
        val conversation = currentState.conversation ?: return
        
        if (currentState.isLoadingMore || !currentState.hasMoreMessages) return

        viewModelScope.launch {
            _detailState.update { it.copy(isLoadingMore = true) }
            try {
                val meId = me?.id
                val offset = currentState.messages.size
                val olderMessages = chatService.getMessages(conversationId, limit = pageSize, offset = offset)
                
                val newItems = olderMessages.map { it.toMessageItem(meId, conversation.members) }
                _detailState.update { state ->
                    state.copy(
                        messages = newItems + state.messages,
                        isLoadingMore = false,
                        hasMoreMessages = olderMessages.size >= pageSize
                    )
                }
            } catch (t: Throwable) {
                _detailState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    private fun ConversationSummaryDto.toConversationItem(meId: String?): ConversationItem {
        val normalizedTopic = topic?.takeIf { it.isNotBlank() }

        val formattedMembers = members.map { member ->
            MemberInfo(
                id = member.userId,
                joinedAt = formatDate(member.joinedAt),
            name = member.displayName?.takeIf { it.isNotBlank() }
                    ?: member.username?.takeIf { it.isNotBlank() }
                    ?: member.email?.substringBefore("@")
                    ?: "ID ${member.userId.takeLast(6)}",
            avatarUrl = member.avatarUrl
        )
    }

    val otherMember = formattedMembers.firstOrNull { it.id != meId }

    val title = when {
        type.name == "GROUP" && !normalizedTopic.isNullOrEmpty() -> normalizedTopic
        type.name == "GROUP" -> getString(R.string.title_group_chat)
        else -> otherMember?.name ?: normalizedTopic ?: getString(R.string.title_direct_chat_fallback)
    }

    val lastMessagePreview = lastMessage?.let { message ->
            val senderName = if (message.senderId != meId) {
                formattedMembers.find { it.id == message.senderId }?.name
            } else null
            
            val content = when {
                message.deletedAt != null -> getString(R.string.message_deleted)
                message.body.isNotBlank() -> message.body
                message.attachments.isNotEmpty() -> getString(R.string.message_with_attachment)
                else -> getString(R.string.message_empty_placeholder)
            }
            
            if (senderName != null && type.name == "GROUP") {
                "$senderName: $content"
            } else {
                content
            }
        } ?: getString(R.string.message_no_history)

        val lastTime = lastMessage?.createdAt?.let(::formatTimestamp)

        return ConversationItem(
            id = id,
            title = title,
            subtitle = lastMessagePreview,
            unreadCount = unreadCount,
            lastMessageTime = lastTime,
            members = formattedMembers,
            isPinned = pinnedAt != null,
            isArchived = archivedAt != null,
            isMuted = isMuted,
            raw = this
        )
    }

    private fun MessageDto.toMessageItem(meId: String?, members: List<MemberInfo> = emptyList()): MessageItem = MessageItem(
        id = id,
        text = when {
            deletedAt != null -> getString(R.string.message_deleted)
            body.isNotBlank() -> body
            attachments.isNotEmpty() -> getString(R.string.message_with_attachment)
            else -> getString(R.string.message_empty_placeholder)
        },
        timestamp = formatTimestamp(createdAt),
        dateHeader = formatDateHeader(createdAt),
        isMine = senderId == meId,
        senderName = if (senderId != meId) members.find { it.id == senderId }?.name else null,
        reactions = reactions.map { ReactionItem(it.emoji, it.count, it.reactedByMe) },
        myReaction = reactions.firstOrNull { it.reactedByMe }?.emoji,
        status = status,
        replyTo = replyTo?.let { 
            ReplyInfo(
                id = it.id,
                senderName = it.senderName,
                body = it.body
            )
        },
        attachments = attachments.map { 
            AttachmentItem(
                id = it.id,
                fileName = it.fileName,
                contentType = it.contentType,
                dataBase64 = it.dataBase64
            )
        }
    )

    private fun UserProfileDto.toSuggestion(): ContactSuggestion = ContactSuggestion(
        id = id,
        email = email,
        role = formatRole(role) ?: role,
        joinedAt = formatDate(createdAt),
        name = displayName?.takeIf { it.isNotBlank() } ?: username?.takeIf { it.isNotBlank() }
    )

    private fun applyUpdatedMessage(updatedItem: MessageItem) {
        _detailState.update { state ->
            val idx = state.messages.indexOfFirst { it.id == updatedItem.id }
            if (idx == -1) state else state.copy(
                messages = state.messages.toMutableList().also { it[idx] = updatedItem }
            )
        }
    }

    private fun formatTimestamp(iso: String): String = runCatching {
        val instant = Instant.parse(iso)
        val zoned = instant.atZone(ZoneId.systemDefault())
        val now = Instant.now().atZone(ZoneId.systemDefault())
        if (zoned.toLocalDate() == now.toLocalDate()) {
            zoned.format(timeFormatter)
        } else {
            zoned.format(dateFormatter)
        }
    }.getOrElse { iso }

    private fun formatDate(iso: String): String = runCatching {
        val instant = Instant.parse(iso)
        val zoned = instant.atZone(ZoneId.systemDefault())
        zoned.format(dateOnlyFormatter)
    }.getOrElse { iso }

    private fun formatDateHeader(iso: String): String = runCatching {
        val instant = Instant.parse(iso)
        val zoned = instant.atZone(ZoneId.systemDefault())
        val now = Instant.now().atZone(ZoneId.systemDefault())
        
        when {
            zoned.toLocalDate() == now.toLocalDate() -> getString(R.string.date_today)
            zoned.toLocalDate() == now.minusDays(1).toLocalDate() -> getString(R.string.date_yesterday)
            else -> zoned.format(dateOnlyFormatter)
        }
    }.getOrElse { iso }

    private fun formatRole(role: String?): String? = role?.lowercase()?.replaceFirstChar { it.titlecase() }

    private fun String.parseMembers(): List<String> = split(',', ';', ' ', '\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

    private suspend fun resolveMemberIds(rawMembers: List<String>): List<String>? {
        val suggestionsSnapshot = conversationState.value.contactSuggestions
        val lookupCache = mutableMapOf<String, String>()
        val currentUserId = me?.id
        val resolved = mutableListOf<String>()

        for (raw in rawMembers) {
            val candidate = raw.trim()
            if (candidate.isEmpty()) continue

            val cacheKey = candidate.lowercase()

            val suggestionMatch = suggestionsSnapshot.firstOrNull {
                it.id == candidate || it.email.equals(candidate, ignoreCase = true)
            }

            val resolvedId = when {
                candidate.isUuid() -> candidate
                suggestionMatch != null -> suggestionMatch.id
                lookupCache.containsKey(cacheKey) -> lookupCache.getValue(cacheKey)
                else -> {
                    val results = chatService.searchUsers(candidate)
                    val match = results.firstOrNull { it.email.equals(candidate, ignoreCase = true) }
                        ?: results.firstOrNull { it.id.equals(candidate, ignoreCase = true) }
                    match?.id?.also { lookupCache[cacheKey] = it }
                }
            }

            val finalId = resolvedId ?: run {
                _events.emit(ChatsEvent.ShowMessage(getString(R.string.error_member_not_found, candidate)))
                return null
            }

            if (finalId == currentUserId) {
                _events.emit(ChatsEvent.ShowMessage(getString(R.string.error_member_self)))
                return null
            }

            resolved += finalId
        }

        return resolved.distinct()
    }

    private fun String.isUuid(): Boolean = runCatching { UUID.fromString(this); true }.getOrElse { false }

    private suspend fun handleNetworkError(throwable: Throwable, fallbackMessageRes: Int) {
        if (throwable is ClientRequestException && throwable.response.status == HttpStatusCode.Unauthorized) {
            authService.logout()
            me = null
            selectedConversationId = null
            _conversationState.value = ConversationsUiState()
            _detailState.value = ConversationDetailUiState()
            _events.emit(ChatsEvent.SessionExpired)
            return
        }

        val message = when (throwable) {
            is ClientRequestException -> runCatching {
                json.decodeFromString<ErrorResponse>(throwable.response.bodyAsText()).error
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: getString(fallbackMessageRes)
            else -> throwable.localizedMessage ?: throwable.message ?: getString(fallbackMessageRes)
        }

        _events.emit(ChatsEvent.ShowMessage(message))
    }
    
    /**
     * Конвертирует ConversationEntity в ConversationItem для отображения из кеша
     */
    private fun ConversationEntity.toConversationItem(meId: String?): ConversationItem {
        val members: List<ConversationMemberDto> = try {
            json.decodeFromString(membersJson)
        } catch (_: Exception) {
            emptyList()
        }
        
        val memberInfos = members.map { member ->
            MemberInfo(
                id = member.userId,
                name = member.displayName ?: member.username ?: member.email?.substringBefore("@") ?: "User",
                avatarUrl = member.avatarUrl,
                isOnline = false, // Нет информации об онлайне в кеше
                joinedAt = member.joinedAt
            )
        }
        
        val otherMember = memberInfos.firstOrNull { it.id != meId }
        val displayTitle = if (type == "DIRECT") {
            otherMember?.name ?: topic ?: "Chat"
        } else {
            topic ?: "Group chat"
        }
        
        return ConversationItem(
            id = id,
            title = displayTitle,
            subtitle = "", // No last message in entity
            unreadCount = unreadCount,
            lastMessageTime = null,
            members = memberInfos,
            isPinned = pinnedAt != null,
            isArchived = isArchived,
            isMuted = isMuted,
            raw = ConversationSummaryDto(
                id = id,
                type = if (type == "DIRECT") ConversationType.DIRECT else ConversationType.GROUP,
                topic = topic,
                createdBy = createdBy,
                createdAt = createdAt,
                members = members
            )
        )
    }
    
    // ============ AI Meeting Extraction ============
    
    /**
     * Извлечь предложения о встречах из текущего чата с помощью AI
     */
    fun extractMeetingsFromCurrentConversation() {
        val conversationId = selectedConversationId ?: return
        
        viewModelScope.launch {
            _detailState.update { it.copy(isExtractingMeetings = true, extractedMeetings = emptyList()) }
            
            meetingRepository.extractMeetings(conversationId)
                .onSuccess { extracted ->
                    val meetings = extracted.map { dto ->
                        ExtractedMeetingInfo(
                            title = dto.title,
                            description = dto.description,
                            dateTime = dto.dateTime,
                            location = dto.location,
                            confidence = dto.confidence,
                            sourceMessageId = dto.sourceMessageId
                        )
                    }
                    _detailState.update { 
                        it.copy(
                            isExtractingMeetings = false,
                            extractedMeetings = meetings,
                            showExtractedMeetingsDialog = meetings.isNotEmpty()
                        )
                    }
                    if (meetings.isEmpty()) {
                        _events.emit(ChatsEvent.ShowMessage(getString(R.string.no_meetings_found)))
                    }
                }
                .onFailure { error ->
                    _detailState.update { it.copy(isExtractingMeetings = false) }
                    _events.emit(ChatsEvent.ShowMessage(error.message ?: getString(R.string.error_extracting_meetings)))
                }
        }
    }
    
    /**
     * Создать встречу из предложения AI
     */
    fun createMeetingFromExtracted(meeting: ExtractedMeetingInfo) {
        val conversationId = selectedConversationId ?: return
        
        viewModelScope.launch {
            meetingRepository.createMeetingFromAi(
                conversationId = conversationId,
                title = meeting.title,
                description = meeting.description,
                dateTime = meeting.dateTime,
                location = meeting.location,
                sourceMessageId = meeting.sourceMessageId
            ).onSuccess {
                _events.emit(ChatsEvent.ShowMessage(getString(R.string.meeting_created)))
                dismissExtractedMeetingsDialog()
            }.onFailure { error ->
                _events.emit(ChatsEvent.ShowMessage(error.message ?: getString(R.string.error_creating_meeting)))
            }
        }
    }
    
    /**
     * Закрыть диалог с извлечёнными встречами
     */
    fun dismissExtractedMeetingsDialog() {
        _detailState.update { 
            it.copy(
                showExtractedMeetingsDialog = false,
                extractedMeetings = emptyList()
            ) 
        }
    }
    
    /**
     * Компаратор для сортировки чатов:
     * 1. Закреплённые чаты всегда вверху
     * 2. Затем по времени последнего сообщения (новые выше)
     */
    private fun conversationComparator(): Comparator<ConversationItem> = compareBy<ConversationItem> { !it.isPinned }
        .thenByDescending { conversation ->
            // Парсим время последнего сообщения
            conversation.raw.lastMessage?.createdAt?.let { parseTimestamp(it) } ?: 0L
        }
    
    private fun parseTimestamp(timestamp: String): Long {
        return try {
            java.time.Instant.parse(timestamp.replace(" ", "T") + "Z").toEpochMilli()
        } catch (e: Exception) {
            try {
                java.time.LocalDateTime.parse(timestamp.substringBefore("."))
                    .atZone(java.time.ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            } catch (e2: Exception) {
                0L
            }
        }
    }
}


