package com.nano.min.viewmodel

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.nano.min.R
import com.nano.min.network.AuthService
import com.nano.min.network.ChatService
import com.nano.min.network.ConversationSummaryDto
import com.nano.min.network.ErrorResponse
import com.nano.min.network.MessageDto
import com.nano.min.network.MessageStatus
import com.nano.min.network.MeResponse
import com.nano.min.network.ChatEventDto
import com.nano.min.network.UserProfileDto
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
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

data class ConversationsUiState(
    val profileId: String? = null,
    val profileEmail: String? = null,
    val profileRole: String? = null,
    val profileCreatedAt: String? = null,
    val conversations: List<ConversationItem> = emptyList(),
    val isLoading: Boolean = false,
    val isSearchingContacts: Boolean = false,
    val contactSuggestions: List<ContactSuggestion> = emptyList(),
    val searchQuery: String = "",
    val searchMessageResults: List<MessageSearchResult> = emptyList(),
    val searchUserResults: List<ContactSuggestion> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val error: String? = null
)

data class ConversationDetailUiState(
    val conversation: ConversationItem? = null,
    val messages: List<MessageItem> = emptyList(),
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val messageInput: String = "",
    val attachments: List<PendingAttachment> = emptyList(),
    val error: String? = null
)

data class ConversationItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val unreadCount: Long,
    val lastMessageTime: String?,
    val members: List<MemberInfo>,
    val pinnedAt: String? = null,
    val raw: ConversationSummaryDto
)

data class MessageItem(
    val id: String,
    val text: String,
    val timestamp: String,
    val isMine: Boolean,
    val attachments: List<MessageAttachment>,
    val isDeleted: Boolean = false,
    val isEdited: Boolean = false,
    val status: MessageStatus = MessageStatus.DELIVERED,
    val readBy: List<String> = emptyList()
)

data class MessageSearchResult(
    val id: String,
    val conversationId: String,
    val conversationTitle: String,
    val preview: String,
    val timestamp: String
)

data class MessageAttachment(
    val id: String,
    val fileName: String,
    val contentType: String
)

data class PendingAttachment(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val contentType: String,
    val dataBase64: String
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
    private var globalSearchJob: Job? = null
    private var updatesJob: Job? = null

    private val json = Json { ignoreUnknownKeys = true }
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM HH:mm")
    private val dateOnlyFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    fun editMessage(messageId: String, newBody: String) {
        val conversationId = selectedConversationId ?: return
        if (newBody.isBlank()) {
            viewModelScope.launch { _events.emit(ChatsEvent.ShowMessage(getString(R.string.error_empty_message))) }
            return
        }

        viewModelScope.launch {
            try {
                val updated = chatService.editMessage(messageId, newBody.trim())
                val meId = me?.id
                val updatedItem = updated.toMessageItem(meId, _detailState.value.conversation?.members ?: emptyList())
                _detailState.update { state ->
                    state.copy(messages = state.messages.map { if (it.id == messageId) updatedItem else it })
                }
                refreshConversations(conversationId)
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
                val updatedItem = updated.toMessageItem(meId, _detailState.value.conversation?.members ?: emptyList())
                _detailState.update { state ->
                    state.copy(messages = state.messages.map { if (it.id == messageId) updatedItem else it })
                }
                refreshConversations(conversationId)
            } catch (t: Throwable) {
                handleNetworkError(t, R.string.error_send_message)
            }
        }
    }

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

    fun updateSearchQuery(value: String) {
        _conversationState.update { it.copy(searchQuery = value) }
        if (value.isBlank()) {
            globalSearchJob?.cancel()
            _conversationState.update {
                it.copy(
                    searchMessageResults = emptyList(),
                    searchUserResults = emptyList(),
                    isSearching = false,
                    searchError = null
                )
            }
            return
        }
        performSearch(value.trim())
    }

    private fun performSearch(query: String) {
        globalSearchJob?.cancel()
        globalSearchJob = viewModelScope.launch {
            _conversationState.update { it.copy(isSearching = true, searchError = null) }
            try {
                val conversations = _conversationState.value.conversations.associateBy { it.id }
                val messagesDeferred = async { chatService.searchMessages(query = query) }
                val usersDeferred = async { chatService.searchUsers(query) }

                val messages = messagesDeferred.await().map { dto ->
                    val title = conversations[dto.conversationId]?.title
                        ?: dto.conversationId.takeLast(6)
                    dto.toSearchResult(title)
                }
                val users = usersDeferred.await().map { dto -> dto.toSuggestion() }

                _conversationState.update {
                    it.copy(
                        isSearching = false,
                        searchMessageResults = messages,
                        searchUserResults = users,
                        searchError = null
                    )
                }
            } catch (t: Throwable) {
                _conversationState.update { it.copy(isSearching = false) }
                handleNetworkError(t, R.string.error_search_messages)
                _conversationState.update { it.copy(searchError = getString(R.string.error_search_messages)) }
            }
        }
    }

    fun startDirectConversationFromSearch(userId: String) {
        viewModelScope.launch {
            try {
                val summary = chatService.createDirectConversation(memberId = userId, topic = null)
                refreshConversations(summary.id)
            } catch (t: Throwable) {
                handleNetworkError(t, R.string.error_create_conversation)
            }
        }
    }

    fun updateMessageInput(value: String) {
        _detailState.update { it.copy(messageInput = value) }
    }

    fun sendMessage() {
        val conversationId = selectedConversationId ?: return
        val currentState = _detailState.value
        val conversation = currentState.conversation ?: return
        val message = currentState.messageInput.trim()
        if (message.isEmpty() && currentState.attachments.isEmpty()) {
            _detailState.update { it.copy(error = getString(R.string.error_empty_message)) }
            return
        }

        viewModelScope.launch {
            _detailState.update { it.copy(isSending = true, error = null) }
            try {
                val dto = chatService.sendMessage(
                    conversationId = conversationId,
                    body = message,
                    attachments = currentState.attachments.map { it.toPayload() }
                )
                val meId = me?.id
                val messageItem = dto.toMessageItem(meId, conversation.members)
                _detailState.update {
                    it.copy(
                        isSending = false,
                        messageInput = "",
                        attachments = emptyList(),
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

    fun addPendingAttachments(newOnes: List<PendingAttachment>) {
        if (newOnes.isEmpty()) return
        _detailState.update { state ->
            state.copy(attachments = (state.attachments + newOnes).distinctBy { it.id })
        }
    }

    fun removePendingAttachment(id: String) {
        _detailState.update { state ->
            state.copy(attachments = state.attachments.filterNot { it.id == id })
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

    fun togglePin(conversationId: String, pin: Boolean) {
        viewModelScope.launch {
            try {
                if (pin) {
                    chatService.pinConversation(conversationId)
                } else {
                    chatService.unpinConversation(conversationId)
                }
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
            "conversation_read" -> handleConversationReadEvent(event)
        }
    }

    private fun handleConversationReadEvent(event: ChatEventDto) {
        val conversationId = event.conversationId ?: return
        val readerId = event.readerId ?: return
        val lastReadMessageId = event.messageId

        val current = selectedConversationId
        if (current != conversationId) return

        _detailState.update { state ->
            if (state.conversation == null) return@update state
            val members = state.conversation.members
            val updatedMessages = state.messages.map { msg ->
                if (!msg.isMine || msg.isDeleted) return@map msg
                if (readerId == me?.id) return@map msg

                val shouldMark = when {
                    lastReadMessageId == null -> true
                    else -> state.messages.indexOfFirst { it.id == lastReadMessageId }
                        .takeIf { it >= 0 }
                        ?.let { idx ->
                            val msgIdx = state.messages.indexOfFirst { it.id == msg.id }
                            msgIdx >= 0 && msgIdx <= idx
                        } ?: false
                }

                if (shouldMark) {
                    msg.copy(
                        status = MessageStatus.READ,
                        readBy = (msg.readBy + readerId).distinct()
                    )
                } else msg
            }

            state.copy(messages = updatedMessages)
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
                messages = messages.map { it.toMessageItem(meId, conversation.members) },
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
            pinnedAt = pinnedAt,
            raw = this
        )
    }

    private fun MessageDto.toMessageItem(meId: String?, members: List<MemberInfo> = emptyList()): MessageItem {
        val mine = senderId == meId
        val memberIds = members.map { it.id }.toSet()
        val others = if (memberIds.isEmpty()) emptySet() else memberIds - senderId
        val computedStatus = if (mine) {
            when {
                others.isEmpty() -> MessageStatus.READ
                others.all { readBy.contains(it) } -> MessageStatus.READ
                else -> status
            }
        } else status

        return MessageItem(
            id = id,
            text = when {
                deletedAt != null -> getString(R.string.message_deleted)
                body.isNotBlank() -> body
                attachments.isNotEmpty() -> getString(R.string.message_with_attachment)
                else -> getString(R.string.message_empty_placeholder)
            },
            timestamp = formatTimestamp(createdAt),
            isMine = mine,
            attachments = attachments.map {
                MessageAttachment(
                    id = it.id,
                    fileName = it.fileName,
                    contentType = it.contentType
                )
            },
            isDeleted = deletedAt != null,
            isEdited = editedAt != null && deletedAt == null,
            status = computedStatus,
            readBy = readBy
        )
    }

    private fun MessageDto.toSearchResult(conversationTitle: String): MessageSearchResult = MessageSearchResult(
        id = id,
        conversationId = conversationId,
        conversationTitle = conversationTitle,
        preview = when {
            deletedAt != null -> getString(R.string.message_deleted)
            body.isNotBlank() -> body
            attachments.isNotEmpty() -> getString(R.string.message_with_attachment)
            else -> getString(R.string.message_empty_placeholder)
        },
        timestamp = formatTimestamp(createdAt)
    )

    private fun UserProfileDto.toSuggestion(): ContactSuggestion = ContactSuggestion(
        id = id,
        email = email,
        role = formatRole(role) ?: role,
        joinedAt = formatDate(createdAt)
    )

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

    private fun PendingAttachment.toPayload(): com.nano.min.network.MessageAttachmentPayload =
        com.nano.min.network.MessageAttachmentPayload(
            fileName = fileName,
            contentType = contentType,
            dataBase64 = dataBase64
        )

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

