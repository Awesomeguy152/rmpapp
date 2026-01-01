package com.nano.min.viewmodel

import android.app.Application
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

import com.nano.min.network.MessageStatus

data class ConversationsUiState(
    val profileId: String? = null,
    val profileEmail: String? = null,
    val profileRole: String? = null,
    val profileCreatedAt: String? = null,
    val conversations: List<ConversationItem> = emptyList(),
    val isLoading: Boolean = false,
    val isSearchingContacts: Boolean = false,
    val contactSuggestions: List<ContactSuggestion> = emptyList(),
    val error: String? = null
)

data class ConversationDetailUiState(
    val conversation: ConversationItem? = null,
    val messages: List<MessageItem> = emptyList(),
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val messageInput: String = "",
    val error: String? = null,
    val typingUsers: Set<String> = emptySet()
)

data class ConversationItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val unreadCount: Long,
    val lastMessageTime: String?,
    val members: List<MemberInfo>,
    val raw: ConversationSummaryDto
)

data class MessageItem(
    val id: String,
    val text: String,
    val timestamp: String,
    val isMine: Boolean,
    val reactions: List<ReactionItem> = emptyList(),
    val myReaction: String? = null,
    val status: MessageStatus = MessageStatus.SENT
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
    val joinedAt: String
)

data class MemberInfo(
    val id: String,
    val joinedAt: String
)

sealed interface ChatsEvent {
    data class ShowMessage(val message: String) : ChatsEvent
    object SessionExpired : ChatsEvent
}

class ChatsViewModel(
    application: Application,
    private val authService: AuthService,
    private val chatService: ChatService
) : ViewModelRes(application) {

    private val _conversationState = MutableStateFlow(ConversationsUiState(isLoading = true))
    val conversationState: StateFlow<ConversationsUiState> = _conversationState.asStateFlow()

    private val _detailState = MutableStateFlow(ConversationDetailUiState())
    val detailState: StateFlow<ConversationDetailUiState> = _detailState.asStateFlow()

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
        if (message.isEmpty()) {
            _detailState.update { it.copy(error = getString(R.string.error_empty_message)) }
            return
        }

        viewModelScope.launch {
            _detailState.update { it.copy(isSending = true, error = null) }
            try {
                val dto = chatService.sendMessage(conversationId, message)
                val meId = me?.id
                val messageItem = dto.toMessageItem(meId)
                _detailState.update {
                    it.copy(
                        isSending = false,
                        messageInput = "",
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
            val incoming = event.message.toMessageItem(meId)
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
                        profileCreatedAt = createdAt
                    )
                }
            }
    }

    private suspend fun refreshConversationsInternal(selectConversation: String?) {
        _conversationState.update { it.copy(isLoading = true, error = null) }
        try {
            val meId = me?.id
            val summaries = chatService.getConversations()
            val items = summaries.map { it.toConversationItem(meId) }
            _conversationState.value = _conversationState.value.copy(
                isLoading = false,
                conversations = items,
                error = null
            )

            val targetId = selectConversation
            selectedConversationId = targetId
            if (targetId != null) {
                val targetConversation = items.find { it.id == targetId }
                if (targetConversation != null) {
                    loadMessagesInternal(targetId, targetConversation)
                } else {
                    _detailState.value = ConversationDetailUiState()
                    selectedConversationId = null
                }
            } else {
                _detailState.value = _detailState.value.copy(conversation = null)
            }
        } catch (t: Throwable) {
            val errorMessage = t.localizedMessage ?: t.message ?: getString(R.string.error_load_conversations)
            _conversationState.update { it.copy(isLoading = false, error = errorMessage) }
            handleNetworkError(t, R.string.error_load_conversations)
        }
    }

    private suspend fun loadMessagesInternal(conversationId: String, conversation: ConversationItem) {
        _detailState.update { it.copy(conversation = conversation, isLoading = true, error = null) }
        try {
            val meId = me?.id
            val messages = chatService.getMessages(conversationId)
            _detailState.value = ConversationDetailUiState(
                conversation = conversation,
                messages = messages.map { it.toMessageItem(meId) },
                isLoading = false,
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

    private fun ConversationSummaryDto.toConversationItem(meId: String?): ConversationItem {
        val normalizedTopic = topic?.takeIf { it.isNotBlank() }
        val otherMemberId = members.map { it.userId }.firstOrNull { it != meId }
        val title = when {
            !normalizedTopic.isNullOrEmpty() -> normalizedTopic
            type.name == "GROUP" -> getString(R.string.title_group_chat)
            else -> otherMemberId?.let { getString(R.string.title_direct_chat, it.takeLast(6)) }
                ?: getString(R.string.title_direct_chat_fallback)
        }

        val formattedMembers = members.map { member ->
            MemberInfo(
                id = member.userId,
                joinedAt = formatDate(member.joinedAt)
            )
        }

        val lastMessagePreview = lastMessage?.let { message ->
            when {
                message.deletedAt != null -> getString(R.string.message_deleted)
                message.body.isNotBlank() -> message.body
                message.attachments.isNotEmpty() -> getString(R.string.message_with_attachment)
                else -> getString(R.string.message_empty_placeholder)
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
            raw = this
        )
    }

    private fun MessageDto.toMessageItem(meId: String?): MessageItem = MessageItem(
        id = id,
        text = when {
            deletedAt != null -> getString(R.string.message_deleted)
            body.isNotBlank() -> body
            attachments.isNotEmpty() -> getString(R.string.message_with_attachment)
            else -> getString(R.string.message_empty_placeholder)
        },
        timestamp = formatTimestamp(createdAt),
        isMine = senderId == meId,
        reactions = reactions.map { ReactionItem(it.emoji, it.count, it.reactedByMe) },
        myReaction = reactions.firstOrNull { it.reactedByMe }?.emoji,
        status = status
    )

    private fun UserProfileDto.toSuggestion(): ContactSuggestion = ContactSuggestion(
        id = id,
        email = email,
        role = formatRole(role) ?: role,
        joinedAt = formatDate(createdAt)
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
}

