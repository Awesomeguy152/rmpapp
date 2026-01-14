package com.nano.min.screens

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nano.min.R
import com.nano.min.viewmodel.ChatsEvent
import com.nano.min.viewmodel.ChatsViewModel
import com.nano.min.viewmodel.ConversationDetailUiState
import com.nano.min.viewmodel.ConversationItem
import com.nano.min.viewmodel.ConversationsUiState
import com.nano.min.viewmodel.AttachmentItem
import com.nano.min.viewmodel.MessageItem
import com.nano.min.viewmodel.ExtractedMeetingInfo
import com.nano.min.viewmodel.ContactSuggestion
import com.nano.min.viewmodel.MemberInfo
import com.nano.min.network.ConversationType
import com.nano.min.network.MessageStatus
import com.nano.min.ui.components.SwipeableListItem
import com.nano.min.ui.components.ConversationActionsSheet
import com.nano.min.ui.components.SwipeAction
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// Функция для относительного времени
private fun formatRelativeTime(timeString: String?): String {
    if (timeString.isNullOrEmpty()) return ""
    return try {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val messageTime = LocalDateTime.parse(timeString.substringBefore("."), formatter)
        val now = LocalDateTime.now()
        
        val minutesAgo = ChronoUnit.MINUTES.between(messageTime, now)
        val hoursAgo = ChronoUnit.HOURS.between(messageTime, now)
        val daysAgo = ChronoUnit.DAYS.between(messageTime, now)
        
        when {
            minutesAgo < 1 -> "сейчас"
            minutesAgo < 60 -> "$minutesAgo мин"
            hoursAgo < 24 -> "$hoursAgo ч"
            daysAgo == 1L -> "вчера"
            daysAgo < 7 -> "$daysAgo дн"
            else -> messageTime.format(DateTimeFormatter.ofPattern("dd.MM.yy"))
        }
    } catch (e: Exception) {
        timeString.substringBefore("T").takeLast(5) // fallback: показать дату
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRootScreen(
	onLogout: () -> Unit,
    onProfileClick: () -> Unit = {},
    onMeetingsClick: () -> Unit = {},
	viewModel: ChatsViewModel = koinViewModel()
) {
	val conversationState by viewModel.conversationState.collectAsStateWithLifecycle()
	val detailState by viewModel.detailState.collectAsStateWithLifecycle()

	val snackbarHostState = remember { SnackbarHostState() }
	val context = LocalContext.current
	var showCreateSheet by rememberSaveable { mutableStateOf(false) }
	val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

	// Перезагружаем данные профиля при входе (важно после регистрации/логина нового пользователя)
	LaunchedEffect(Unit) {
		viewModel.reloadAfterLogin()
	}

	LaunchedEffect(viewModel) {
		viewModel.events.collect { event ->
			when (event) {
				is ChatsEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
				ChatsEvent.SessionExpired -> {
					snackbarHostState.showSnackbar(context.getString(R.string.session_expired))
					onLogout()
				}
			}
		}
	}

	// Автоматическое обновление при входе в приложение (resume)
	LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
		viewModel.refreshConversations()
	}

	if (showCreateSheet) {
		ModalBottomSheet(
			onDismissRequest = {
				showCreateSheet = false
				viewModel.clearContactSuggestions()
			},
			sheetState = bottomSheetState
		) {
			CreateConversationSheet(
				isSearching = conversationState.isSearchingContacts,
				suggestions = conversationState.contactSuggestions,
				onSearchContacts = viewModel::searchContacts,
				onClearSuggestions = viewModel::clearContactSuggestions,
				onCreateConversation = { members, topic ->
					viewModel.createConversation(members, topic)
					showCreateSheet = false
				}
			)
		}
	}

	val colorScheme = MaterialTheme.colorScheme
	val gradient = remember(colorScheme.primary, colorScheme.surface, colorScheme.secondaryContainer) {
		Brush.verticalGradient(
			colors = listOf(
				colorScheme.primary.copy(alpha = 0.10f),
				colorScheme.secondaryContainer.copy(alpha = 0.06f),
				colorScheme.surface
			)
		)
	}

	Scaffold(
		snackbarHost = { SnackbarHost(snackbarHostState) },
		topBar = {
			CenterAlignedTopAppBar(
				colors = TopAppBarDefaults.topAppBarColors(
					containerColor = Color.Transparent,
					titleContentColor = colorScheme.onSurface
				),
				title = {
					Column(
						horizontalAlignment = Alignment.CenterHorizontally,
						verticalArrangement = Arrangement.spacedBy(6.dp)
					) {
						Text(
							text = stringResource(R.string.screen_chats),
							style = MaterialTheme.typography.headlineMedium,
							fontWeight = FontWeight.SemiBold,
							color = colorScheme.onSurface
						)
						val displayName = conversationState.profileDisplayName?.takeIf { it.isNotBlank() }
                            ?: conversationState.profileUsername?.takeIf { it.isNotBlank() }?.let { "@$it" }
                            ?: conversationState.profileEmail

						displayName?.let { name ->
							Surface(
								shape = RoundedCornerShape(18.dp),
								color = colorScheme.primary.copy(alpha = 0.08f),
								border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.35f))
							) {
								Text(
									text = name,
									style = MaterialTheme.typography.labelLarge,
									color = colorScheme.primary,
									modifier = Modifier
										.padding(horizontal = 12.dp, vertical = 6.dp)
								)
							}
						}
						conversationState.profileRole?.let { role ->
							Text(
								text = stringResource(R.string.profile_role_value, role),
								style = MaterialTheme.typography.labelSmall,
								color = colorScheme.onSurfaceVariant
							)
						}
						conversationState.profileCreatedAt?.let { joined ->
							Text(
								text = stringResource(R.string.profile_joined_value, joined),
								style = MaterialTheme.typography.labelSmall,
								color = colorScheme.onSurfaceVariant
							)
						}
					}
				},
				actions = {
					IconButton(onClick = onMeetingsClick) {
						Icon(
							imageVector = Icons.Default.CalendarMonth,
							contentDescription = stringResource(R.string.screen_meetings),
							tint = colorScheme.onSurface
						)
					}
					IconButton(onClick = onProfileClick) {
						Icon(
							imageVector = Icons.Default.AccountCircle,
							contentDescription = "Profile",
							tint = colorScheme.onSurface
						)
					}
					IconButton(onClick = {
						Log.d("AppRootScreen", "LOGOUT CLICKED!")
						viewModel.logout()
						Log.d("AppRootScreen", "viewModel.logout() done, calling onLogout()")
						onLogout()
						Log.d("AppRootScreen", "onLogout() done")
					}) {
						Icon(
							imageVector = Icons.AutoMirrored.Filled.Logout,
							contentDescription = stringResource(R.string.action_logout)
						)
					}
				}
			)
		}
	) { paddingValues ->
		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(gradient)
				.padding(paddingValues)
		) {
			Box(modifier = Modifier.fillMaxSize()) {
				Box(
					modifier = Modifier
						.size(260.dp)
						.offset(x = 120.dp, y = (-80).dp)
						.align(Alignment.TopEnd)
						.background(
							brush = Brush.radialGradient(
								colors = listOf(
									colorScheme.primary.copy(alpha = 0.24f),
									Color.Transparent
								)
							),
							shape = CircleShape
						)
						.alpha(0.6f)
				)
				Box(
					modifier = Modifier
						.size(320.dp)
						.offset(x = (-160).dp, y = 180.dp)
						.align(Alignment.BottomStart)
						.background(
							brush = Brush.radialGradient(
								colors = listOf(
									colorScheme.tertiary.copy(alpha = 0.16f),
									Color.Transparent
								)
							),
							shape = CircleShape
						)
						.alpha(0.55f)
				)
			}
			BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
				val isCompact = maxWidth < 720.dp
				if (isCompact) {
					if (detailState.conversation != null) {
						ConversationDetailPanel(
							state = detailState,
							currentUserId = conversationState.profileId,
							onMessageChange = viewModel::updateMessageInput,
							onSend = { viewModel.sendMessage() },
							onEditMessage = viewModel::editMessage,
							onDeleteMessage = viewModel::deleteMessage,
							onToggleReaction = { messageId, emoji -> viewModel.toggleReaction(messageId, emoji) },
							availableReactions = viewModel.availableReactions,
							onUpdateTopic = viewModel::updateCurrentConversationTopic,
							onAddMembers = viewModel::addMembersToCurrentConversation,
							onRemoveMember = viewModel::removeMemberFromCurrentConversation,
							onLeaveConversation = viewModel::leaveCurrentConversation,
							onReplyToMessage = viewModel::startReplyTo,
							onCancelReply = viewModel::cancelReply,
							onLoadMore = viewModel::loadMoreMessages,
							onToggleSearch = viewModel::toggleSearchMode,
							onSearchQueryChange = viewModel::updateSearchQuery,
							onClearSearch = viewModel::clearSearch,
							onSendVoiceMessage = viewModel::sendVoiceMessage,
							onStartVoiceRecording = viewModel::startVoiceRecording,
							onStopVoiceRecording = viewModel::stopVoiceRecording,
							onCancelVoiceRecording = viewModel::cancelVoiceRecording,
							onPinMessage = viewModel::pinMessage,
							onUnpinMessage = viewModel::unpinMessage,
							onTogglePinnedPanel = viewModel::togglePinnedMessagesPanel,
							onExtractMeetings = viewModel::extractMeetingsFromCurrentConversation,
							onCreateMeetingFromExtracted = viewModel::createMeetingFromExtracted,
							onDismissExtractedMeetings = viewModel::dismissExtractedMeetingsDialog,
							conversations = conversationState.conversations,
							onBack = { viewModel.clearConversationSelection() },
							modifier = Modifier.fillMaxSize()
						)
					} else {
						ConversationListPanel(
							state = conversationState,
							selectedConversationId = detailState.conversation?.id,
							onCreateConversation = { showCreateSheet = true },
							onRefresh = { viewModel.refreshConversations() },
							onToggleArchived = { viewModel.toggleArchivedChats() },
							onUnarchiveConversation = { viewModel.unarchiveConversation(it) },
							onSelectConversation = { viewModel.selectConversation(it) },
							onPinConversation = { viewModel.pinConversation(it) },
							onArchiveConversation = { viewModel.archiveConversation(it) },
							onMuteConversation = { viewModel.muteConversation(it) },
							onDeleteConversation = { viewModel.deleteConversation(it) },
							modifier = Modifier.fillMaxSize()
						)
					}
				} else {
					Row(modifier = Modifier.fillMaxSize()) {
						ConversationListPanel(
							state = conversationState,
							selectedConversationId = detailState.conversation?.id,
							onCreateConversation = { showCreateSheet = true },
							onRefresh = { viewModel.refreshConversations() },
							onToggleArchived = { viewModel.toggleArchivedChats() },
							onUnarchiveConversation = { viewModel.unarchiveConversation(it) },
							onSelectConversation = { viewModel.selectConversation(it) },
							onPinConversation = { viewModel.pinConversation(it) },
							onArchiveConversation = { viewModel.archiveConversation(it) },
							onMuteConversation = { viewModel.muteConversation(it) },
							onDeleteConversation = { viewModel.deleteConversation(it) },
							modifier = Modifier
								.widthIn(max = 360.dp)
								.fillMaxHeight()
						)
						Box(
							modifier = Modifier
								.fillMaxHeight()
								.width(1.dp)
								.background(MaterialTheme.colorScheme.outlineVariant)
						)
						ConversationDetailPanel(
							state = detailState,
							currentUserId = conversationState.profileId,
							onMessageChange = viewModel::updateMessageInput,
							onSend = { viewModel.sendMessage() },
							onEditMessage = viewModel::editMessage,
							onDeleteMessage = viewModel::deleteMessage,
							onToggleReaction = { messageId, emoji -> viewModel.toggleReaction(messageId, emoji) },
							availableReactions = viewModel.availableReactions,
							onUpdateTopic = viewModel::updateCurrentConversationTopic,
							onAddMembers = viewModel::addMembersToCurrentConversation,
							onRemoveMember = viewModel::removeMemberFromCurrentConversation,
							onLeaveConversation = viewModel::leaveCurrentConversation,
							onReplyToMessage = viewModel::startReplyTo,
							onCancelReply = viewModel::cancelReply,
							onLoadMore = viewModel::loadMoreMessages,
							onToggleSearch = viewModel::toggleSearchMode,
							onSearchQueryChange = viewModel::updateSearchQuery,
							onClearSearch = viewModel::clearSearch,
							onSendVoiceMessage = viewModel::sendVoiceMessage,
							onStartVoiceRecording = viewModel::startVoiceRecording,
							onStopVoiceRecording = viewModel::stopVoiceRecording,
							onCancelVoiceRecording = viewModel::cancelVoiceRecording,
							onPinMessage = viewModel::pinMessage,
							onUnpinMessage = viewModel::unpinMessage,
							onTogglePinnedPanel = viewModel::togglePinnedMessagesPanel,
							onExtractMeetings = viewModel::extractMeetingsFromCurrentConversation,
							onCreateMeetingFromExtracted = viewModel::createMeetingFromExtracted,
							onDismissExtractedMeetings = viewModel::dismissExtractedMeetingsDialog,
							conversations = conversationState.conversations,
							onBack = null,
							modifier = Modifier.weight(1f)
						)
					}
				}
			}
		}
	}
}

@Composable
private fun ConversationListPanel(
	state: ConversationsUiState,
	selectedConversationId: String?,
	onCreateConversation: () -> Unit,
	onRefresh: () -> Unit,
	onToggleArchived: () -> Unit = {},
	onUnarchiveConversation: (String) -> Unit = {},
	onSelectConversation: (String) -> Unit,
	onPinConversation: (String) -> Unit,
	onArchiveConversation: (String) -> Unit,
	onMuteConversation: (String) -> Unit,
	onDeleteConversation: (String) -> Unit,
	modifier: Modifier = Modifier
) {
	val colorScheme = MaterialTheme.colorScheme
	Column(
		modifier = modifier
			.fillMaxSize()
			.padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(16.dp)
	) {
		Surface(
			shape = RoundedCornerShape(28.dp),
			tonalElevation = 8.dp,
			border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.4f)),
			modifier = Modifier.fillMaxWidth()
		) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 22.dp, vertical = 18.dp),
				verticalArrangement = Arrangement.spacedBy(10.dp)
			) {
				Text(
					text = stringResource(R.string.conversation_recent),
					style = MaterialTheme.typography.titleMedium,
					color = colorScheme.onSurface
				)
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(8.dp)
				) {
					AssistChip(
						onClick = onCreateConversation,
						label = { Text(stringResource(R.string.conversation_create)) },
						leadingIcon = {
							Icon(
								imageVector = Icons.Filled.Create,
								contentDescription = null
							)
						},
						colors = AssistChipDefaults.assistChipColors(
							containerColor = colorScheme.primaryContainer.copy(alpha = 0.6f),
							labelColor = colorScheme.onPrimaryContainer,
							leadingIconContentColor = colorScheme.onPrimaryContainer
						)
					)
					AssistChip(
						onClick = onRefresh,
						label = { Text(stringResource(R.string.conversation_refresh)) },
						leadingIcon = {
							Icon(
								imageVector = Icons.Filled.Refresh,
								contentDescription = null
							)
						},
						enabled = !state.isLoading,
						colors = AssistChipDefaults.assistChipColors(
							containerColor = colorScheme.secondaryContainer.copy(alpha = 0.65f),
							labelColor = colorScheme.onSecondaryContainer,
							leadingIconContentColor = colorScheme.onSecondaryContainer,
							disabledContainerColor = colorScheme.surfaceVariant,
							disabledLabelColor = colorScheme.onSurfaceVariant
						)
					)
				}
				state.profileEmail?.let { email ->
					Text(
						text = email,
						style = MaterialTheme.typography.bodyMedium,
						color = colorScheme.onSurfaceVariant
					)
				}
				Row(
					horizontalArrangement = Arrangement.spacedBy(12.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					state.profileRole?.let { role ->
						Text(
							text = stringResource(R.string.profile_role_value, role),
							style = MaterialTheme.typography.labelMedium,
							color = colorScheme.primary
						)
					}
					state.profileCreatedAt?.let { joined ->
						Text(
							text = stringResource(R.string.profile_joined_value, joined),
							style = MaterialTheme.typography.labelSmall,
							color = colorScheme.onSurfaceVariant
						)
					}
					Spacer(modifier = Modifier.weight(1f))
					AssistChip(
						onClick = onToggleArchived,
						label = { 
							Text(
								if (state.showArchivedChats) stringResource(R.string.active_chats) 
								else stringResource(R.string.archived_chats)
							)
						},
						leadingIcon = {
							Icon(
								imageVector = Icons.Filled.Archive,
								contentDescription = null,
								modifier = Modifier.size(18.dp)
							)
						},
						colors = AssistChipDefaults.assistChipColors(
							containerColor = if (state.showArchivedChats) colorScheme.tertiaryContainer else colorScheme.surfaceVariant.copy(alpha = 0.65f),
							labelColor = if (state.showArchivedChats) colorScheme.onTertiaryContainer else colorScheme.onSurfaceVariant,
							leadingIconContentColor = if (state.showArchivedChats) colorScheme.onTertiaryContainer else colorScheme.onSurfaceVariant
						)
					)
				}
			}
		}

		if (state.isLoading) {
			LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
		}

		state.error?.let { error ->
			Surface(
				color = MaterialTheme.colorScheme.errorContainer,
				contentColor = MaterialTheme.colorScheme.onErrorContainer,
				shape = RoundedCornerShape(18.dp),
				modifier = Modifier.fillMaxWidth()
			) {
				Text(
					text = error,
					style = MaterialTheme.typography.bodyMedium,
					modifier = Modifier.padding(16.dp)
				)
			}
		}

		Surface(
			shape = RoundedCornerShape(26.dp),
			tonalElevation = 4.dp,
			modifier = Modifier
				.weight(1f)
				.fillMaxWidth()
		) {
			// Определяем какой список показывать
			val displayList = if (state.showArchivedChats) state.archivedConversations else state.conversations
			
			when {
				state.isLoading && displayList.isEmpty() -> {
					Box(
						modifier = Modifier.fillMaxSize(),
						contentAlignment = Alignment.Center
					) {
						CircularProgressIndicator()
					}
				}

				displayList.isEmpty() -> {
					Column(
						modifier = Modifier
							.fillMaxSize()
							.padding(24.dp),
						horizontalAlignment = Alignment.CenterHorizontally,
						verticalArrangement = Arrangement.Center
					) {
						Icon(
							imageVector = if (state.showArchivedChats) Icons.Filled.Archive else Icons.AutoMirrored.Filled.Send,
							contentDescription = null,
							tint = MaterialTheme.colorScheme.onSurfaceVariant,
							modifier = Modifier.size(48.dp)
						)
						Spacer(modifier = Modifier.height(12.dp))
						Text(
							text = stringResource(if (state.showArchivedChats) R.string.archived_chats_empty else R.string.conversation_list_empty),
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							textAlign = TextAlign.Center
						)
					}
				}

				else -> {
					// Состояние для bottom sheet с действиями
					var showActionsForConversation by remember { mutableStateOf<ConversationItem?>(null) }
					
					PullToRefreshBox(
						isRefreshing = state.isLoading,
						onRefresh = onRefresh,
						modifier = Modifier.fillMaxSize()
					) {
						LazyColumn(
							modifier = Modifier.fillMaxSize(),
							contentPadding = PaddingValues(vertical = 16.dp),
							verticalArrangement = Arrangement.spacedBy(12.dp)
						) {
							items(displayList, key = { it.id }) { conversation ->
								SwipeableListItem(
									isPinned = conversation.isPinned,
									isArchived = conversation.isArchived,
									isMuted = conversation.isMuted,
									onSwipeAction = { action ->
										when (action) {
											SwipeAction.PIN, SwipeAction.UNPIN -> onPinConversation(conversation.id)
											SwipeAction.ARCHIVE, SwipeAction.UNARCHIVE -> {
												if (state.showArchivedChats) {
													onUnarchiveConversation(conversation.id)
												} else {
													onArchiveConversation(conversation.id)
												}
											}
											SwipeAction.MUTE, SwipeAction.UNMUTE -> onMuteConversation(conversation.id)
											SwipeAction.DELETE -> onDeleteConversation(conversation.id)
										}
									}
								) {
									ConversationListItem(
										conversation = conversation,
										currentUserId = state.profileId,
										isSelected = conversation.id == selectedConversationId,
										onClick = { onSelectConversation(conversation.id) },
										onLongClick = { showActionsForConversation = conversation }
									)
								}
							}
						}
					}
					
					// Bottom sheet для действий над чатом
					showActionsForConversation?.let { conv ->
						ConversationActionsSheet(
							isPinned = conv.isPinned,
							isArchived = conv.isArchived || state.showArchivedChats,
							isMuted = conv.isMuted,
							isOwner = true, // Для direct всегда владелец
							onDismiss = { showActionsForConversation = null },
							onPin = { onPinConversation(conv.id) },
							onArchive = { 
								if (state.showArchivedChats) {
									onUnarchiveConversation(conv.id)
								} else {
									onArchiveConversation(conv.id) 
								}
							},
							onMute = { onMuteConversation(conv.id) },
							onDelete = { onDeleteConversation(conv.id) }
						)
					}
				}
			}
		}
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationListItem(
	conversation: ConversationItem,
	currentUserId: String?,
	isSelected: Boolean,
	onClick: () -> Unit,
	onLongClick: () -> Unit = {}
) {
	val colorScheme = MaterialTheme.colorScheme
	val accentColor = if (isSelected) colorScheme.primary else colorScheme.primary.copy(alpha = 0.18f)
	val surfaceColor = if (isSelected) colorScheme.primaryContainer else colorScheme.surface
	Surface(
		shape = RoundedCornerShape(18.dp),
		color = surfaceColor,
		tonalElevation = if (isSelected) 8.dp else 2.dp,
		shadowElevation = if (isSelected) 6.dp else 0.dp,
		border = BorderStroke(1.dp, if (isSelected) colorScheme.primary.copy(alpha = 0.45f) else colorScheme.outlineVariant.copy(alpha = 0.35f)),
		modifier = Modifier
			.fillMaxWidth()
			.combinedClickable(
				onClick = onClick,
				onLongClick = onLongClick
			)
	) {
		Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
			Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
				if (conversation.raw.type == ConversationType.DIRECT) {
					val otherMember = conversation.members.firstOrNull { it.id != currentUserId }
					val isOnline = otherMember?.isOnline ?: false
					val displayName = otherMember?.name ?: conversation.title
					val initials = displayName.split(" ")
						.take(2)
						.mapNotNull { it.firstOrNull()?.uppercaseChar() }
						.joinToString("")
						.ifEmpty { displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "#" }

					Box {
						// WhatsApp-style avatar placeholder with initials
						Surface(
							shape = CircleShape,
							color = accentColor,
							tonalElevation = if (isSelected) 2.dp else 0.dp,
							modifier = Modifier.size(44.dp)
						) {
							Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
								Text(
									text = initials,
									style = MaterialTheme.typography.titleMedium,
									color = if (isSelected) colorScheme.onPrimary else colorScheme.primary
								)
							}
						}
						// Online indicator
						if (isOnline) {
							Box(
								modifier = Modifier
									.size(12.dp)
									.align(Alignment.BottomEnd)
									.offset(x = 2.dp, y = 2.dp)
									.background(Color(0xFF4CAF50), CircleShape)
									.background(
										brush = Brush.radialGradient(
											colors = listOf(Color(0xFF66BB6A), Color(0xFF43A047))
										),
										shape = CircleShape
									)
							)
						}
					}
				} else {
					// Group avatar placeholder
					Surface(
						shape = CircleShape,
						color = accentColor,
						tonalElevation = if (isSelected) 2.dp else 0.dp,
						modifier = Modifier.size(44.dp)
					) {
						Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
							Text(
								text = conversation.title.firstOrNull()?.uppercaseChar()?.toString() ?: "#",
								style = MaterialTheme.typography.titleMedium,
								color = if (isSelected) colorScheme.onPrimary else colorScheme.primary
							)
						}
					}
				}
				Column(modifier = Modifier.weight(1f)) {
					Row(verticalAlignment = Alignment.CenterVertically) {
						val displayTitle = if (conversation.raw.type == ConversationType.DIRECT) {
							conversation.members.firstOrNull { it.id != currentUserId }?.name ?: conversation.title
						} else {
							conversation.title
						}
						// Иконка закреплённого чата
						if (conversation.isPinned) {
							Icon(
								imageVector = Icons.Default.PushPin,
								contentDescription = null,
								modifier = Modifier.size(14.dp),
								tint = colorScheme.primary
							)
							Spacer(modifier = Modifier.width(4.dp))
						}
						Text(
							text = displayTitle,
							style = MaterialTheme.typography.titleMedium,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
							modifier = Modifier.weight(1f, fill = false)
						)
						// Иконка замьюченного чата
						if (conversation.isMuted) {
							Spacer(modifier = Modifier.width(6.dp))
							Icon(
								imageVector = Icons.Default.NotificationsOff,
								contentDescription = stringResource(R.string.conversation_muted),
								modifier = Modifier.size(16.dp),
								tint = colorScheme.onSurfaceVariant
							)
						}
						Spacer(modifier = Modifier.width(8.dp))
						conversation.lastMessageTime?.let { time ->
							Text(
								text = formatRelativeTime(time),
								style = MaterialTheme.typography.labelSmall,
								color = colorScheme.onSurfaceVariant
							)
						}
					}
					Spacer(modifier = Modifier.height(4.dp))
					Text(
						text = conversation.subtitle,
						style = MaterialTheme.typography.bodySmall,
						color = colorScheme.onSurfaceVariant,
						maxLines = 2,
						overflow = TextOverflow.Ellipsis
					)
					Spacer(modifier = Modifier.height(6.dp))
					Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
						Text(
							text = stringResource(R.string.conversation_members_count, conversation.members.size),
							style = MaterialTheme.typography.labelSmall,
							color = colorScheme.onSurfaceVariant
						)
						val typeLabel = if (conversation.raw.type == ConversationType.GROUP) {
							stringResource(R.string.conversation_type_group_label)
						} else {
							stringResource(R.string.conversation_type_direct_label)
						}
						Badge(
							containerColor = colorScheme.primary,
							contentColor = colorScheme.onPrimary
						) {
							Text(text = typeLabel)
						}
					}
				}
				if (conversation.unreadCount > 0) {
					Badge(
						containerColor = colorScheme.primary,
						contentColor = colorScheme.onPrimary
					) {
						Text(conversation.unreadCount.toString())
					}
				}
			}
		}
	}
}

@Composable
private fun ContactSuggestionList(
	suggestions: List<ContactSuggestion>,
	onSuggestionSelected: (ContactSuggestion) -> Unit
) {
	val colorScheme = MaterialTheme.colorScheme
	Surface(
		shape = RoundedCornerShape(16.dp),
		color = colorScheme.surface,
		tonalElevation = 6.dp,
		border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.25f)),
		modifier = Modifier.fillMaxWidth()
	) {
		Column {
			suggestions.forEachIndexed { index, suggestion ->
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.clickable { onSuggestionSelected(suggestion) }
						.padding(horizontal = 16.dp, vertical = 12.dp)
				) {
					if (suggestion.name != null) {
						Text(
							text = suggestion.name,
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onSurface,
							fontWeight = FontWeight.Bold
						)
						Text(
							text = suggestion.email,
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant
						)
					} else {
						Text(
							text = suggestion.email,
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onSurface
						)
					}
					Spacer(modifier = Modifier.height(2.dp))
					Text(
						text = stringResource(R.string.contact_role_value, suggestion.role),
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
					Text(
						text = stringResource(R.string.contact_joined_value, suggestion.joinedAt),
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
				if (index != suggestions.lastIndex) {
					HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.2f))
				}
			}
		}
	}
}

@Composable
private fun CreateConversationSheet(
	isSearching: Boolean,
	suggestions: List<ContactSuggestion>,
	onSearchContacts: (String) -> Unit,
	onClearSuggestions: () -> Unit,
	onCreateConversation: (String, String?) -> Unit
) {
	var memberInput by rememberSaveable { mutableStateOf("") }
	var topicInput by rememberSaveable { mutableStateOf("") }
	var isGroupChat by rememberSaveable { mutableStateOf(false) }
	var selectedMembers by rememberSaveable { mutableStateOf(listOf<ContactSuggestion>()) }
	val colorScheme = MaterialTheme.colorScheme

	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 24.dp, vertical = 16.dp),
		verticalArrangement = Arrangement.spacedBy(16.dp)
	) {
		Text(
			text = if (isGroupChat) stringResource(R.string.create_group_chat) else stringResource(R.string.conversation_create_title),
			style = MaterialTheme.typography.headlineSmall
		)

		// Переключатель для группового чата
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically
		) {
			Text(
				text = stringResource(R.string.group_chat_toggle),
				style = MaterialTheme.typography.bodyMedium
			)
			androidx.compose.material3.Switch(
				checked = isGroupChat,
				onCheckedChange = { 
					isGroupChat = it
					if (!it) {
						// Если переключили на личный чат, оставляем только первого участника
						selectedMembers = selectedMembers.take(1)
					}
				}
			)
		}

		// Отображение выбранных участников (chips)
		if (selectedMembers.isNotEmpty()) {
			androidx.compose.foundation.lazy.LazyRow(
				horizontalArrangement = Arrangement.spacedBy(8.dp),
				modifier = Modifier.fillMaxWidth()
			) {
				items(selectedMembers.size) { index ->
					val member = selectedMembers[index]
					AssistChip(
						onClick = {
							selectedMembers = selectedMembers.filterNot { it.id == member.id }
						},
						label = { Text(member.name ?: member.email) },
						trailingIcon = {
							Icon(
								Icons.Default.Clear,
								contentDescription = "Remove",
								modifier = Modifier.size(16.dp)
							)
						},
						colors = AssistChipDefaults.assistChipColors(
							containerColor = colorScheme.primaryContainer
						)
					)
				}
			}
		}

		OutlinedTextField(
			value = memberInput,
			onValueChange = {
				memberInput = it
				onSearchContacts(it)
			},
			label = { Text(stringResource(R.string.conversation_member_hint)) },
			placeholder = { Text(stringResource(R.string.conversation_member_placeholder)) },
			modifier = Modifier.fillMaxWidth(),
			shape = RoundedCornerShape(18.dp),
			colors = OutlinedTextFieldDefaults.colors(
				focusedBorderColor = colorScheme.primary,
				unfocusedBorderColor = colorScheme.outlineVariant,
				focusedContainerColor = colorScheme.surface,
				unfocusedContainerColor = colorScheme.surface,
				cursorColor = colorScheme.primary
			),
			trailingIcon = {
				if (memberInput.isNotBlank()) {
					IconButton(onClick = {
						memberInput = ""
						onClearSuggestions()
					}) {
						Icon(
							imageVector = Icons.Filled.Clear,
							contentDescription = stringResource(R.string.action_clear_input)
						)
					}
				}
			}
		)

		if (isSearching) {
			LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
		}

		// Название группы (обязательно для групповых чатов)
		if (isGroupChat) {
			OutlinedTextField(
				value = topicInput,
				onValueChange = { topicInput = it },
				label = { Text(stringResource(R.string.group_name_label)) },
				placeholder = { Text(stringResource(R.string.group_name_placeholder)) },
				singleLine = true,
				modifier = Modifier.fillMaxWidth(),
				shape = RoundedCornerShape(18.dp),
				colors = OutlinedTextFieldDefaults.colors(
					focusedBorderColor = colorScheme.primary,
					unfocusedBorderColor = colorScheme.outlineVariant,
					focusedContainerColor = colorScheme.surface,
					unfocusedContainerColor = colorScheme.surface,
					cursorColor = colorScheme.primary
				)
			)
		} else {
			OutlinedTextField(
				value = topicInput,
				onValueChange = { topicInput = it },
				label = { Text(stringResource(R.string.conversation_topic_hint)) },
				placeholder = { Text(stringResource(R.string.conversation_topic_placeholder)) },
				singleLine = true,
				modifier = Modifier.fillMaxWidth(),
				shape = RoundedCornerShape(18.dp),
				colors = OutlinedTextFieldDefaults.colors(
					focusedBorderColor = colorScheme.primary,
					unfocusedBorderColor = colorScheme.outlineVariant,
					focusedContainerColor = colorScheme.surface,
					unfocusedContainerColor = colorScheme.surface,
					cursorColor = colorScheme.primary
				)
			)
		}

		if (suggestions.isNotEmpty()) {
			Text(
				text = stringResource(R.string.search_results),
				style = MaterialTheme.typography.labelMedium,
				color = colorScheme.onSurfaceVariant
			)
			LazyColumn(
				modifier = Modifier.heightIn(max = 200.dp),
				verticalArrangement = Arrangement.spacedBy(4.dp)
			) {
				items(suggestions.size) { index ->
					val suggestion = suggestions[index]
					val isAlreadySelected = selectedMembers.any { it.id == suggestion.id }
					Surface(
						onClick = {
							if (!isAlreadySelected) {
								if (isGroupChat) {
									selectedMembers = selectedMembers + suggestion
								} else {
									selectedMembers = listOf(suggestion)
								}
								memberInput = ""
								onClearSuggestions()
							}
						},
						shape = RoundedCornerShape(12.dp),
						color = if (isAlreadySelected) colorScheme.primaryContainer else colorScheme.surfaceVariant,
						modifier = Modifier.fillMaxWidth()
					) {
						Row(
							modifier = Modifier.padding(12.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(12.dp)
						) {
							Surface(
								shape = CircleShape,
								color = colorScheme.primary,
								modifier = Modifier.size(36.dp)
							) {
								Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
									Text(
										text = (suggestion.name ?: suggestion.email).first().uppercaseChar().toString(),
										color = colorScheme.onPrimary,
										style = MaterialTheme.typography.titleSmall
									)
								}
							}
							Column(modifier = Modifier.weight(1f)) {
								Text(
									text = suggestion.name ?: suggestion.email,
									style = MaterialTheme.typography.bodyMedium
								)
								if (suggestion.name != null) {
									Text(
										text = suggestion.email,
										style = MaterialTheme.typography.bodySmall,
										color = colorScheme.onSurfaceVariant
									)
								}
							}
							if (isAlreadySelected) {
								Icon(
									Icons.Default.Check,
									contentDescription = "Selected",
									tint = colorScheme.primary
								)
							}
						}
					}
				}
			}
		} else if (memberInput.isEmpty() && selectedMembers.isEmpty()) {
			Text(
				text = stringResource(R.string.conversation_create_hint),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}

		// Валидация: для группы нужно минимум 2 участника и название
		val canCreate = if (isGroupChat) {
			selectedMembers.size >= 2 && topicInput.isNotBlank()
		} else {
			selectedMembers.isNotEmpty() || memberInput.split(',', ';', ' ', '\n').any { it.trim().isNotEmpty() }
		}

		Button(
			onClick = {
				val membersToCreate = if (selectedMembers.isNotEmpty()) {
					selectedMembers.joinToString(",") { it.id }
				} else {
					memberInput.trim()
				}
				val normalizedTopic = topicInput.trim().ifBlank { null }
				onCreateConversation(membersToCreate, normalizedTopic)
				memberInput = ""
				topicInput = ""
				selectedMembers = emptyList()
				isGroupChat = false
				onClearSuggestions()
			},
			enabled = canCreate,
			shape = RoundedCornerShape(18.dp),
			modifier = Modifier
				.fillMaxWidth()
				.heightIn(min = 52.dp)
		) {
			Icon(imageVector = if (isGroupChat) Icons.Default.Create else Icons.AutoMirrored.Filled.Send, contentDescription = null)
			Spacer(modifier = Modifier.width(8.dp))
			Text(text = if (isGroupChat) stringResource(R.string.create_group) else stringResource(R.string.conversation_create))
		}
	}
}

@Composable
private fun ConversationDetailPanel(
	state: ConversationDetailUiState,
	currentUserId: String?,
	onMessageChange: (String) -> Unit,
	onSend: () -> Unit,
	onEditMessage: (String, String) -> Unit,
	onDeleteMessage: (String) -> Unit,
	onToggleReaction: (messageId: String, emoji: String) -> Unit,
	availableReactions: List<String>,
	onUpdateTopic: (String) -> Unit,
	onAddMembers: (String) -> Unit,
	onRemoveMember: (String) -> Unit,
	onLeaveConversation: () -> Unit,
	onReplyToMessage: (MessageItem) -> Unit,
	onCancelReply: () -> Unit,
	onLoadMore: () -> Unit,
	onToggleSearch: () -> Unit,
	onSearchQueryChange: (String) -> Unit,
	onClearSearch: () -> Unit,
	onSendVoiceMessage: (ByteArray, Long) -> Unit,
	onStartVoiceRecording: () -> Unit,
	onStopVoiceRecording: () -> Unit,
	onCancelVoiceRecording: () -> Unit,
	onPinMessage: (String) -> Unit,
	onUnpinMessage: (String) -> Unit,
	onTogglePinnedPanel: () -> Unit,
	onExtractMeetings: () -> Unit,
	onCreateMeetingFromExtracted: (ExtractedMeetingInfo) -> Unit,
	onDismissExtractedMeetings: () -> Unit,
	conversations: List<ConversationItem>,
	onBack: (() -> Unit)?,
	modifier: Modifier = Modifier
	) {
	val conversation = state.conversation
	if (conversation == null) {
		Box(
			modifier = modifier.fillMaxSize(),
			contentAlignment = Alignment.Center
		) {
			Text(
				text = stringResource(R.string.conversation_select_placeholder),
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				style = MaterialTheme.typography.bodyMedium,
				textAlign = TextAlign.Center,
				modifier = Modifier.padding(24.dp)
			)
		}
		return
	}

	var editingMessageId by remember { mutableStateOf<String?>(null) }
	var showGroupInfo by remember { mutableStateOf(false) }
	val haptic = LocalHapticFeedback.current
	val colorScheme = MaterialTheme.colorScheme
	val context = LocalContext.current
	
	// Voice recording setup
	var mediaRecorder by remember { mutableStateOf<android.media.MediaRecorder?>(null) }
	var voiceRecordingFile by remember { mutableStateOf<java.io.File?>(null) }
	
	val permissionLauncher = rememberLauncherForActivityResult(
		contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
	) { isGranted ->
		if (isGranted) {
			// Start recording after permission granted
			try {
				val cacheDir = context.cacheDir
				val audioFile = java.io.File(cacheDir, "voice_${System.currentTimeMillis()}.m4a")
				voiceRecordingFile = audioFile
				
				val recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
					android.media.MediaRecorder(context)
				} else {
					@Suppress("DEPRECATION")
					android.media.MediaRecorder()
				}
				recorder.apply {
					setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
					setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
					setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
					setAudioEncodingBitRate(128000)
					setAudioSamplingRate(44100)
					setOutputFile(audioFile.absolutePath)
					prepare()
					start()
				}
				mediaRecorder = recorder
				onStartVoiceRecording()
			} catch (e: Exception) {
				e.printStackTrace()
			}
		}
	}
	
	// Function to start voice recording with permission check
	fun startRecordingWithPermission() {
		if (androidx.core.content.ContextCompat.checkSelfPermission(
				context, android.Manifest.permission.RECORD_AUDIO
			) == android.content.pm.PackageManager.PERMISSION_GRANTED
		) {
			try {
				val cacheDir = context.cacheDir
				val audioFile = java.io.File(cacheDir, "voice_${System.currentTimeMillis()}.m4a")
				voiceRecordingFile = audioFile
				
				val recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
					android.media.MediaRecorder(context)
				} else {
					@Suppress("DEPRECATION")
					android.media.MediaRecorder()
				}
				recorder.apply {
					setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
					setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
					setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
					setAudioEncodingBitRate(128000)
					setAudioSamplingRate(44100)
					setOutputFile(audioFile.absolutePath)
					prepare()
					start()
				}
				mediaRecorder = recorder
				onStartVoiceRecording()
			} catch (e: Exception) {
				e.printStackTrace()
			}
		} else {
			permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
		}
	}
	
	// Function to stop recording and send voice message
	fun stopRecordingAndSend() {
		try {
			mediaRecorder?.apply {
				stop()
				release()
			}
			mediaRecorder = null
			
			voiceRecordingFile?.let { file ->
				if (file.exists()) {
					val audioData = file.readBytes()
					val durationMs = System.currentTimeMillis() - state.voiceRecordingStartTime
					onSendVoiceMessage(audioData, durationMs)
					file.delete()
				}
			}
			voiceRecordingFile = null
			onStopVoiceRecording()
		} catch (e: Exception) {
			e.printStackTrace()
			onCancelVoiceRecording()
		}
	}
	
	// Function to cancel recording
	fun cancelRecording() {
		try {
			mediaRecorder?.apply {
				stop()
				release()
			}
		} catch (e: Exception) {
			// Ignore errors when stopping
		}
		mediaRecorder = null
		voiceRecordingFile?.delete()
		voiceRecordingFile = null
		onCancelVoiceRecording()
	}

	// If editing, update input field with message text
	LaunchedEffect(editingMessageId) {
		editingMessageId?.let { id ->
			val msg = state.messages.find { it.id == id }
			if (msg != null) {
				onMessageChange(msg.text)
			}
		}
	}

	val isGroupConversation = conversation.raw.type == ConversationType.GROUP
	val isOwner = currentUserId != null && conversation.raw.createdBy == currentUserId
	val listState = rememberLazyListState()
	// Header теперь вне LazyColumn, считаем только оставшиеся элементы
	val headerItemsCount = 
		(if (isGroupConversation) 1 else if (conversation.members.isNotEmpty()) 1 else 0) +
		(if (state.isLoading) 1 else 0) +
		(if (state.error != null) 1 else 0) +
		(if (state.isSearchMode) 1 else 0)

	LaunchedEffect(state.messages.size) {
		if (state.messages.isNotEmpty()) {
			val targetIndex = headerItemsCount + state.messages.lastIndex
			listState.scrollToItem(targetIndex)
		}
	}

	// Detect scroll to top for pagination
	val shouldLoadMore = remember {
		derivedStateOf {
			val firstVisibleIndex = listState.firstVisibleItemIndex
			val hasMore = state.hasMoreMessages && !state.isLoadingMore && !state.isLoading
			firstVisibleIndex <= headerItemsCount + 3 && hasMore && state.messages.isNotEmpty()
		}
	}

	LaunchedEffect(shouldLoadMore.value) {
		if (shouldLoadMore.value) {
			onLoadMore()
		}
	}
	
	// Показываем экран информации о группе
	if (showGroupInfo && isGroupConversation) {
		GroupInfoScreen(
			conversation = conversation,
			currentUserId = currentUserId,
			isOwner = isOwner,
			isBusy = state.isLoading || state.isSending,
			onUpdateTopic = onUpdateTopic,
			onAddMembers = onAddMembers,
			onRemoveMember = onRemoveMember,
			onLeaveConversation = onLeaveConversation,
			onBack = { showGroupInfo = false }
		)
		return
	}

	Surface(
		modifier = modifier.fillMaxSize(),
		shape = RoundedCornerShape(28.dp),
		color = colorScheme.surface.copy(alpha = 0.95f),
		tonalElevation = 10.dp,
		shadowElevation = 6.dp,
		border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.35f))
	) {
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(20.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp)
		) {
			// Фиксированный header
			if (onBack != null) {
				Row(verticalAlignment = Alignment.CenterVertically) {
					IconButton(onClick = onBack) {
						Icon(
							imageVector = Icons.AutoMirrored.Filled.ArrowBack,
							contentDescription = stringResource(R.string.conversation_back)
						)
					}
					Text(
						text = stringResource(R.string.conversation_back),
						style = MaterialTheme.typography.titleMedium,
						modifier = Modifier.weight(1f)
					)
					// Кнопка информации о группе
					if (isGroupConversation) {
						IconButton(onClick = { showGroupInfo = true }) {
							Icon(
								imageVector = Icons.Default.Group,
								contentDescription = stringResource(R.string.group_info_title),
								tint = colorScheme.primary
							)
						}
					}
					// Кнопка AI извлечения встреч
					IconButton(
						onClick = onExtractMeetings,
						enabled = !state.isExtractingMeetings
					) {
						if (state.isExtractingMeetings) {
							CircularProgressIndicator(
								modifier = Modifier.size(24.dp),
								strokeWidth = 2.dp
							)
						} else {
							Icon(
								imageVector = Icons.Default.SmartToy,
								contentDescription = stringResource(R.string.ai_extract_meetings),
								tint = colorScheme.primary
							)
						}
					}
					// Кнопка закреплённых сообщений
					if (state.pinnedMessages.isNotEmpty()) {
						Badge(
							containerColor = colorScheme.primary,
							contentColor = colorScheme.onPrimary
						) {
							Text(state.pinnedMessages.size.toString())
						}
					}
					IconButton(onClick = onTogglePinnedPanel) {
						Icon(
							imageVector = Icons.Default.PushPin,
							contentDescription = stringResource(R.string.pinned_messages),
							tint = if (state.showPinnedMessages) colorScheme.primary else colorScheme.onSurfaceVariant
						)
					}
				}
			}
			
			// Диалог с извлечёнными встречами
			if (state.showExtractedMeetingsDialog && state.extractedMeetings.isNotEmpty()) {
				ExtractedMeetingsDialog(
					meetings = state.extractedMeetings,
					onCreateMeeting = onCreateMeetingFromExtracted,
					onDismiss = onDismissExtractedMeetings
				)
			}
			
			// Фиксированная панель закреплённых сообщений
			if (state.showPinnedMessages) {
				Surface(
					shape = RoundedCornerShape(16.dp),
					color = colorScheme.primaryContainer.copy(alpha = 0.5f),
					border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.3f)),
					modifier = Modifier.fillMaxWidth()
				) {
					Column(modifier = Modifier.padding(12.dp)) {
						Row(
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(8.dp)
						) {
							Icon(
								imageVector = Icons.Default.PushPin,
								contentDescription = null,
								tint = colorScheme.primary,
								modifier = Modifier.size(20.dp)
							)
							Text(
								text = stringResource(R.string.pinned_messages),
								style = MaterialTheme.typography.titleSmall,
								color = colorScheme.primary
							)
						}
						Spacer(modifier = Modifier.height(8.dp))
						if (state.pinnedMessages.isEmpty()) {
							Text(
								text = stringResource(R.string.no_pinned_messages),
								style = MaterialTheme.typography.bodySmall,
								color = colorScheme.onSurfaceVariant
							)
						} else {
							state.pinnedMessages.forEach { pinned ->
								Surface(
									shape = RoundedCornerShape(12.dp),
									color = colorScheme.surface,
									modifier = Modifier
										.fillMaxWidth()
										.padding(vertical = 4.dp)
										.clickable { onUnpinMessage(pinned.messageId) }
								) {
									Row(
										modifier = Modifier.padding(10.dp),
										verticalAlignment = Alignment.Top,
										horizontalArrangement = Arrangement.spacedBy(8.dp)
									) {
										Column(modifier = Modifier.weight(1f)) {
											Text(
												text = pinned.senderName,
												style = MaterialTheme.typography.labelMedium,
												color = colorScheme.primary
											)
											Text(
												text = pinned.messageBody,
												style = MaterialTheme.typography.bodySmall,
												maxLines = 2,
												overflow = TextOverflow.Ellipsis
											)
										}
										IconButton(
											onClick = { onUnpinMessage(pinned.messageId) },
											modifier = Modifier.size(24.dp)
										) {
											Icon(
												imageVector = Icons.Default.Clear,
												contentDescription = stringResource(R.string.unpin_message),
												modifier = Modifier.size(16.dp),
												tint = colorScheme.onSurfaceVariant
											)
										}
									}
								}
							}
						}
					}
				}
			}
			
			LazyColumn(
				modifier = Modifier
					.weight(1f)
					.fillMaxWidth(),
				state = listState,
				contentPadding = PaddingValues(bottom = 16.dp),
				verticalArrangement = Arrangement.spacedBy(14.dp)
			) {

			// Search bar
			if (state.isSearchMode) {
				item("search_bar_${conversation.id}") {
					OutlinedTextField(
						value = state.searchQuery,
						onValueChange = onSearchQueryChange,
						placeholder = { Text("Search messages...") },
						modifier = Modifier.fillMaxWidth(),
						singleLine = true,
						leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
						trailingIcon = {
							if (state.searchQuery.isNotEmpty()) {
								IconButton(onClick = { onSearchQueryChange("") }) {
									Icon(Icons.Default.Clear, contentDescription = "Clear")
								}
							}
						},
						shape = RoundedCornerShape(16.dp)
					)
					
					if (state.isSearching) {
						LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
					}
					
					if (state.searchResults.isNotEmpty()) {
						Text(
							text = "${state.searchResults.size} results found",
							style = MaterialTheme.typography.labelSmall,
							color = colorScheme.onSurfaceVariant,
							modifier = Modifier.padding(top = 4.dp)
						)
					}
				}
			}

			item("header_${conversation.id}") {
				Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically
					) {
						val displayTitle = if (conversation.raw.type == ConversationType.DIRECT) {
							conversation.members.firstOrNull { it.id != currentUserId }?.name ?: conversation.title
						} else {
							conversation.title
						}
						Text(
							text = displayTitle,
							style = MaterialTheme.typography.headlineSmall,
							fontWeight = FontWeight.SemiBold,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
							modifier = Modifier.weight(1f)
						)
						IconButton(onClick = onToggleSearch) {
							Icon(
								imageVector = if (state.isSearchMode) Icons.Default.Clear else Icons.Default.Search,
								contentDescription = "Search"
							)
						}
					}
					Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
						conversation.lastMessageTime?.let { time ->
							Text(
								text = formatRelativeTime(time),
								style = MaterialTheme.typography.labelSmall,
								color = colorScheme.onSurfaceVariant
							)
						}
						Text(
							text = stringResource(R.string.conversation_members_count, conversation.members.size),
							style = MaterialTheme.typography.labelSmall,
							color = colorScheme.onSurfaceVariant
						)
					}
					val topic = conversation.raw.topic?.takeIf { it.isNotBlank() }
					if (topic != null) {
						Text(
							text = stringResource(R.string.conversation_topic_current, topic),
							style = MaterialTheme.typography.bodySmall,
							color = colorScheme.onSurfaceVariant
						)
					} else if (isGroupConversation) {
						Text(
							text = stringResource(R.string.conversation_topic_empty),
							style = MaterialTheme.typography.bodySmall,
							color = colorScheme.onSurfaceVariant
						)
					}
				}
			}

			// Для личных чатов показываем информацию об участнике
			if (!isGroupConversation && conversation.members.isNotEmpty()) {
				item("members_${conversation.id}") {
					Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
						Text(
							text = stringResource(R.string.conversation_members_title),
							style = MaterialTheme.typography.labelSmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant
						)
						conversation.members.forEach { member ->
							Text(
								text = if (member.name != null) {
									"${member.name} • ${stringResource(R.string.profile_joined_value, member.joinedAt)}"
								} else {
									stringResource(
										R.string.conversation_member_entry,
										member.id.takeLast(6),
										member.joinedAt
									)
								},
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
					}
				}
			}

			// Loading more indicator
			if (state.isLoadingMore) {
				item("loading_more_${conversation.id}") {
					Box(
						modifier = Modifier
							.fillMaxWidth()
							.padding(vertical = 8.dp),
						contentAlignment = Alignment.Center
					) {
						CircularProgressIndicator(modifier = Modifier.size(24.dp))
					}
				}
			}

			if (state.isLoading) {
				item("loading_${conversation.id}") {
					LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
				}
			}

			state.error?.let { error ->
				item("error_${conversation.id}") {
					Text(
						text = error,
						color = colorScheme.error,
						style = MaterialTheme.typography.bodyMedium
					)
				}
			}

			if (state.isLoading && state.messages.isEmpty()) {
				item("loading_empty_${conversation.id}") {
					Box(
						modifier = Modifier
							.fillMaxWidth()
							.padding(vertical = 32.dp),
						contentAlignment = Alignment.Center
					) {
						CircularProgressIndicator()
					}
				}
			} else if (state.messages.isEmpty()) {
				item("empty_${conversation.id}") {
					Text(
						text = stringResource(R.string.message_no_history),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						textAlign = TextAlign.Center,
						modifier = Modifier
							.fillMaxWidth()
							.padding(horizontal = 24.dp, vertical = 32.dp)
					)
				}
			} else {
				var lastDate: String? = null
				items(
					items = state.messages,
					key = { it.id }
				) { message ->
					if (message.dateHeader != lastDate) {
						lastDate = message.dateHeader
						Box(
							modifier = Modifier
								.fillMaxWidth()
								.padding(vertical = 8.dp),
							contentAlignment = Alignment.Center
						) {
							Surface(
								shape = RoundedCornerShape(12.dp),
								color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
								contentColor = colorScheme.onSurfaceVariant
							) {
								Text(
									text = message.dateHeader,
									style = MaterialTheme.typography.labelSmall,
									modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
								)
							}
						}
					}
					MessageBubble(
						message = message,
						availableReactions = availableReactions,
						onReact = { emoji -> onToggleReaction(message.id, emoji) },
						onEdit = { 
							editingMessageId = message.id
							onMessageChange(message.text)
						},
						onDelete = { onDeleteMessage(message.id) },
						onReply = { onReplyToMessage(message) },
						onPin = { 
							val isPinned = state.pinnedMessages.any { it.messageId == message.id }
							if (isPinned) onUnpinMessage(message.id) else onPinMessage(message.id)
						},
						isPinned = state.pinnedMessages.any { it.messageId == message.id }
					)
				}
			}

			if (state.typingUsers.isNotEmpty()) {
				item("typing_indicator") {
					Text(
						text = "Typing...",
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.primary,
						modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
					)
				}
			}
		}

		val sendEnabled = state.messageInput.isNotBlank() && !state.isSending
		
		val context = LocalContext.current

		// Voice recording indicator
		if (state.isRecordingVoice) {
			var recordingSeconds by remember { mutableStateOf(0) }
			
			LaunchedEffect(state.voiceRecordingStartTime) {
				while (true) {
					recordingSeconds = ((System.currentTimeMillis() - state.voiceRecordingStartTime) / 1000).toInt()
					kotlinx.coroutines.delay(1000)
				}
			}
			
			Surface(
				color = colorScheme.errorContainer,
				shape = RoundedCornerShape(12.dp),
				modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.SpaceBetween,
					modifier = Modifier.padding(12.dp)
				) {
					Row(verticalAlignment = Alignment.CenterVertically) {
						// Pulsating red dot
						Box(
							modifier = Modifier
								.size(12.dp)
								.background(colorScheme.error, CircleShape)
						)
						Spacer(modifier = Modifier.width(8.dp))
						Text(
							text = String.format("%d:%02d", recordingSeconds / 60, recordingSeconds % 60),
							style = MaterialTheme.typography.bodyMedium,
							fontWeight = FontWeight.Bold,
							color = colorScheme.onErrorContainer
						)
						Spacer(modifier = Modifier.width(8.dp))
						Text(
							text = stringResource(R.string.recording_voice),
							style = MaterialTheme.typography.bodySmall,
							color = colorScheme.onErrorContainer
						)
					}
						IconButton(onClick = { cancelRecording() }) {
						Icon(
							imageVector = Icons.Default.Clear,
							contentDescription = stringResource(R.string.cancel_recording),
							tint = colorScheme.onErrorContainer
						)
					}
				}
			}
		}

		// Reply panel
		if (state.replyingToMessage != null) {
			Surface(
				color = MaterialTheme.colorScheme.surfaceVariant,
				shape = RoundedCornerShape(12.dp),
				modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					modifier = Modifier.padding(8.dp)
				) {
					Box(
						modifier = Modifier
							.width(3.dp)
							.height(32.dp)
							.background(colorScheme.primary, RoundedCornerShape(2.dp))
					)
					Spacer(modifier = Modifier.width(8.dp))
					Column(modifier = Modifier.weight(1f)) {
						Text(
							text = state.replyingToMessage.senderName ?: "You",
							style = MaterialTheme.typography.labelSmall,
							color = colorScheme.primary,
							fontWeight = FontWeight.Bold
						)
						Text(
							text = state.replyingToMessage.text,
							style = MaterialTheme.typography.bodySmall,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis
						)
					}
					IconButton(onClick = { onCancelReply() }) {
						Icon(Icons.Default.Clear, contentDescription = "Cancel Reply")
					}
				}
			}
		}

		if (editingMessageId != null) {
			Surface(
				color = MaterialTheme.colorScheme.surfaceVariant,
				modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					modifier = Modifier.padding(8.dp)
				) {
					Icon(Icons.Default.Edit, contentDescription = null, tint = colorScheme.primary)
					Spacer(modifier = Modifier.width(8.dp))
					Column(modifier = Modifier.weight(1f)) {
						Text(
							text = "Editing Message",
							style = MaterialTheme.typography.labelSmall,
							color = colorScheme.primary
						)
						Text(
							text = state.messages.find { it.id == editingMessageId }?.text.orEmpty(),
							style = MaterialTheme.typography.bodySmall,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis
						)
					}
					IconButton(onClick = { 
						editingMessageId = null
						onMessageChange("")
					}) {
						Icon(Icons.Default.Clear, contentDescription = "Cancel Edit")
					}
				}
			}
		}

		Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			OutlinedTextField(
				value = state.messageInput,
				onValueChange = onMessageChange,
				placeholder = { Text(stringResource(R.string.message_input_hint)) },
				modifier = Modifier.weight(1f),
				maxLines = 4,
				shape = RoundedCornerShape(20.dp),
				colors = OutlinedTextFieldDefaults.colors(
					focusedBorderColor = colorScheme.primary,
					unfocusedBorderColor = colorScheme.outlineVariant,
					focusedContainerColor = colorScheme.surface,
					unfocusedContainerColor = colorScheme.surface,
					cursorColor = colorScheme.primary
				),
				keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Send),
				keyboardActions = KeyboardActions(onSend = {
					if (sendEnabled) {
						haptic.performHapticFeedback(HapticFeedbackType.LongPress)
						if (editingMessageId != null) {
							onEditMessage(editingMessageId!!, state.messageInput)
							editingMessageId = null
							onMessageChange("")
						} else {
							onSend()
						}
					}
				})
			)
			FilledIconButton(
				onClick = {
					haptic.performHapticFeedback(HapticFeedbackType.LongPress)
					if (sendEnabled) {
						// Send message
						if (editingMessageId != null) {
							onEditMessage(editingMessageId!!, state.messageInput)
							editingMessageId = null
							onMessageChange("")
						} else {
							onSend()
						}
					} else if (!state.isSending) {
						// Voice recording toggle
						if (state.isRecordingVoice) {
							stopRecordingAndSend()
						} else {
							startRecordingWithPermission()
						}
					}
				},
				enabled = sendEnabled || !state.isSending,
				shape = RoundedCornerShape(18.dp),
				colors = IconButtonDefaults.filledIconButtonColors(
					containerColor = when {
						state.isRecordingVoice -> colorScheme.error
						sendEnabled -> colorScheme.primary
						else -> colorScheme.primary.copy(alpha = 0.7f)
					}
				)
			) {
				if (state.isSending) {
					CircularProgressIndicator(
						modifier = Modifier.size(18.dp),
						strokeWidth = 2.dp,
						color = colorScheme.onPrimary
					)
				} else if (sendEnabled) {
					Icon(
						imageVector = Icons.AutoMirrored.Filled.Send,
						contentDescription = stringResource(R.string.action_send),
						tint = colorScheme.onPrimary
					)
				} else if (state.isRecordingVoice) {
					// Show stop icon when recording
					Icon(
						imageVector = Icons.Default.Stop,
						contentDescription = stringResource(R.string.stop_recording),
						tint = colorScheme.onPrimary
					)
				} else {
					// Show mic icon when input is empty
					Icon(
						imageVector = Icons.Default.Mic,
						contentDescription = stringResource(R.string.voice_message),
						tint = colorScheme.onPrimary
					)
				}
			}
		}
	}
}
}

@Composable
private fun GroupManagementSection(
	conversation: ConversationItem,
	currentUserId: String?,
	isOwner: Boolean,
	isBusy: Boolean,
	onUpdateTopic: (String) -> Unit,
	onAddMembers: (String) -> Unit,
	onRemoveMember: (String) -> Unit,
	onLeaveConversation: () -> Unit
) {
	var topicText by rememberSaveable(conversation.id, "topic") {
		mutableStateOf(conversation.raw.topic.orEmpty())
	}
	var membersInput by rememberSaveable(conversation.id, "membersInput") {
		mutableStateOf("")
	}
	var showLeaveDialog by remember { mutableStateOf(false) }
	
	if (showLeaveDialog) {
		AlertDialog(
			onDismissRequest = { showLeaveDialog = false },
			title = { Text(stringResource(R.string.leave_conversation)) },
			text = { Text(stringResource(R.string.leave_conversation_confirm)) },
			confirmButton = {
				TextButton(
					onClick = {
						showLeaveDialog = false
						onLeaveConversation()
					}
				) {
					Text(stringResource(R.string.leave_conversation), color = MaterialTheme.colorScheme.error)
				}
			},
			dismissButton = {
				TextButton(onClick = { showLeaveDialog = false }) {
					Text(stringResource(android.R.string.cancel))
				}
			}
		)
	}

	LaunchedEffect(conversation.raw.topic) {
		topicText = conversation.raw.topic.orEmpty()
	}

	Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
		if (isOwner) {
			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				Text(
					text = stringResource(R.string.conversation_group_owner_title),
					style = MaterialTheme.typography.titleMedium
				)
				OutlinedTextField(
					value = topicText,
					onValueChange = { topicText = it },
					label = { Text(stringResource(R.string.conversation_topic_hint)) },
					singleLine = true,
					modifier = Modifier.fillMaxWidth(),
					shape = RoundedCornerShape(16.dp),
					colors = OutlinedTextFieldDefaults.colors(
						focusedBorderColor = MaterialTheme.colorScheme.primary,
						unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
						focusedContainerColor = MaterialTheme.colorScheme.surface,
						unfocusedContainerColor = MaterialTheme.colorScheme.surface,
						cursorColor = MaterialTheme.colorScheme.primary
					)
				)
				val canUpdateTopic = topicText.trim() != conversation.raw.topic.orEmpty()
				Button(
					onClick = { onUpdateTopic(topicText.trim()) },
					enabled = canUpdateTopic && !isBusy,
					modifier = Modifier.align(Alignment.End)
				) {
					Text(stringResource(R.string.conversation_update_topic))
				}
			}
		}

		Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
			Text(
				text = stringResource(R.string.conversation_members_title),
				style = MaterialTheme.typography.labelSmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
			conversation.members.forEach { member ->
				GroupMemberRow(
					member = member,
					canRemove = isOwner && member.id != currentUserId,
					isBusy = isBusy,
					onRemove = onRemoveMember
				)
			}
		}

		if (isOwner) {
			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				OutlinedTextField(
					value = membersInput,
					onValueChange = { membersInput = it },
					label = { Text(stringResource(R.string.conversation_member_hint)) },
					modifier = Modifier.fillMaxWidth(),
					shape = RoundedCornerShape(16.dp),
					colors = OutlinedTextFieldDefaults.colors(
						focusedBorderColor = MaterialTheme.colorScheme.primary,
						unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
						focusedContainerColor = MaterialTheme.colorScheme.surface,
						unfocusedContainerColor = MaterialTheme.colorScheme.surface,
						cursorColor = MaterialTheme.colorScheme.primary
					)
				)
				Text(
					text = stringResource(R.string.conversation_members_manage_hint),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
				val canAddMembers = membersInput.split(',', ';', ' ', '\n')
					.any { it.trim().isNotEmpty() }
				Button(
					onClick = {
						onAddMembers(membersInput)
						membersInput = ""
					},
					enabled = canAddMembers && !isBusy,
					modifier = Modifier.align(Alignment.End)
				) {
					Text(stringResource(R.string.conversation_add_members))
				}
			}
		}
		
		// Кнопка выхода из группы (для всех пользователей)
		Spacer(modifier = Modifier.height(16.dp))
		OutlinedButton(
			onClick = { showLeaveDialog = true },
			modifier = Modifier.fillMaxWidth(),
			colors = ButtonDefaults.outlinedButtonColors(
				contentColor = MaterialTheme.colorScheme.error
			),
			border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
		) {
			Icon(
				imageVector = Icons.AutoMirrored.Filled.ExitToApp,
				contentDescription = null,
				modifier = Modifier.size(18.dp)
			)
			Spacer(modifier = Modifier.width(8.dp))
			Text(stringResource(R.string.leave_conversation))
		}
	}
}

@Composable
private fun GroupMemberRow(
	member: MemberInfo,
	canRemove: Boolean,
	isBusy: Boolean,
	onRemove: (String) -> Unit
) {
	Surface(
		shape = RoundedCornerShape(12.dp),
		color = MaterialTheme.colorScheme.surface,
		border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
		tonalElevation = 4.dp,
		modifier = Modifier.fillMaxWidth()
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 16.dp, vertical = 12.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			Column(modifier = Modifier.weight(1f)) {
				Text(
					text = if (member.name != null) {
						"${member.name} • ${stringResource(R.string.profile_joined_value, member.joinedAt)}"
					} else {
						stringResource(
							R.string.conversation_member_entry,
							member.id.takeLast(6),
							member.joinedAt
						)
					},
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}
			if (canRemove) {
				IconButton(
					onClick = { onRemove(member.id) },
					enabled = !isBusy
				) {
					Icon(
						imageVector = Icons.Filled.Delete,
						contentDescription = stringResource(R.string.conversation_member_remove)
					)
				}
			}
		}
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
	message: MessageItem,
	availableReactions: List<String>,
	onReact: (String) -> Unit,
	onEdit: (String) -> Unit,
	onDelete: () -> Unit,
	onReply: () -> Unit = {},
	onPin: () -> Unit = {},
	isPinned: Boolean = false
) {
	var showReactions by remember(message.id) { mutableStateOf(false) }
	var showMenu by remember { mutableStateOf(false) }

	Column(
		modifier = Modifier.fillMaxWidth(),
		horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start
	) {
		val colorScheme = MaterialTheme.colorScheme
		val bubbleShape = if (message.isMine) {
			RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 6.dp, bottomStart = 20.dp)
		} else {
			RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp)
		}
		Box {
			Surface(
				color = if (message.isMine) colorScheme.primary else colorScheme.secondaryContainer,
				contentColor = if (message.isMine) colorScheme.onPrimary else colorScheme.onSecondaryContainer,
				shape = bubbleShape,
				border = null,
				tonalElevation = if (message.isMine) 6.dp else 1.dp,
				shadowElevation = if (message.isMine) 6.dp else 0.dp,
				modifier = Modifier.combinedClickable(
					onDoubleClick = { showReactions = !showReactions },
					onLongClick = { showMenu = true },
					onClick = {}
				)
			) {
				Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
					// Reply preview
					if (message.replyTo != null) {
						Surface(
							color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
							shape = RoundedCornerShape(8.dp),
							modifier = Modifier.padding(bottom = 8.dp)
						) {
							Row(
								modifier = Modifier.padding(8.dp),
								verticalAlignment = Alignment.CenterVertically
							) {
								Box(
									modifier = Modifier
										.width(3.dp)
										.height(32.dp)
										.background(colorScheme.primary, RoundedCornerShape(2.dp))
								)
								Spacer(modifier = Modifier.width(8.dp))
								Column {
									Text(
										text = message.replyTo.senderName,
										style = MaterialTheme.typography.labelSmall,
										color = colorScheme.primary,
										fontWeight = FontWeight.Bold
									)
									Text(
										text = message.replyTo.body,
										style = MaterialTheme.typography.bodySmall,
										maxLines = 1,
										overflow = TextOverflow.Ellipsis,
										color = colorScheme.onSurfaceVariant
									)
								}
							}
						}
					}
					
					if (!message.isMine && message.senderName != null) {
						Text(
							text = message.senderName,
							style = MaterialTheme.typography.labelSmall,
							color = colorScheme.primary,
							fontWeight = FontWeight.Bold
						)
						Spacer(modifier = Modifier.height(2.dp))
					}
					
					// Display voice messages only (attachments disabled)
					message.attachments.filter { it.contentType.startsWith("audio/") }.forEach { attachment ->
						VoiceMessagePlayer(
							attachment = attachment,
							isMine = message.isMine,
							colorScheme = colorScheme
						)
						Spacer(modifier = Modifier.height(4.dp))
					}
					
					// Show text message
					if (message.text.isNotBlank() && message.attachments.none { it.contentType.startsWith("audio/") }) {
						Text(
							text = message.text,
							style = MaterialTheme.typography.bodyMedium
						)
					} else if (message.text.isNotBlank() && message.attachments.all { !it.contentType.startsWith("audio/") }) {
						Text(
							text = message.text,
							style = MaterialTheme.typography.bodyMedium
						)
					}
				}
			}
			
			DropdownMenu(
				expanded = showMenu,
				onDismissRequest = { showMenu = false }
			) {
				val clipboardManager = LocalClipboardManager.current
				DropdownMenuItem(
					text = { Text("Copy") },
					onClick = {
						showMenu = false
						clipboardManager.setText(AnnotatedString(message.text))
					},
					leadingIcon = { Icon(Icons.Default.ContentCopy, null) }
				)
				DropdownMenuItem(
					text = { Text("Reply") },
					onClick = {
						showMenu = false
						onReply()
					},
					leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, null) }
				)
				DropdownMenuItem(
					text = { Text(if (isPinned) "Unpin" else "Pin") },
					onClick = {
						showMenu = false
						onPin()
					},
					leadingIcon = { Icon(Icons.Default.PushPin, null) }
				)
				if (message.isMine) {
					DropdownMenuItem(
						text = { Text("Edit") },
						onClick = {
							showMenu = false
							onEdit(message.text)
						},
						leadingIcon = { Icon(Icons.Default.Edit, null) }
					)
					DropdownMenuItem(
						text = { Text("Delete") },
						onClick = {
							showMenu = false
							onDelete()
						},
						leadingIcon = { Icon(Icons.Default.Delete, null) }
					)
				}
			}
		}
		if (message.reactions.isNotEmpty()) {
			Spacer(modifier = Modifier.height(6.dp))
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				message.reactions.forEach { reaction ->
					val selected = reaction.reactedByMe
					Surface(
						shape = RoundedCornerShape(12.dp),
						color = if (selected) colorScheme.primaryContainer else colorScheme.surface,
						border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.3f)),
						tonalElevation = 2.dp,
						modifier = Modifier
							.clickable { onReact(reaction.emoji) }
					) {
						Row(
							modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
							horizontalArrangement = Arrangement.spacedBy(6.dp),
							verticalAlignment = Alignment.CenterVertically
						) {
							Text(text = reaction.emoji, style = MaterialTheme.typography.bodyLarge)
							Text(
								text = reaction.count.toString(),
								style = MaterialTheme.typography.labelMedium,
								color = colorScheme.onSurfaceVariant
							)
						}
					}
				}
			}
		}
		Spacer(modifier = Modifier.height(6.dp))
		if (showReactions) {
			Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
				availableReactions.forEach { emoji ->
					val selected = message.myReaction == emoji
					Surface(
						shape = CircleShape,
						color = if (selected) colorScheme.primaryContainer else colorScheme.surface,
						border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.3f)),
						modifier = Modifier
							.size(34.dp)
							.clickable {
							onReact(emoji)
							showReactions = false
						}
					) {
						Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
							Text(text = emoji)
						}
					}
				}
			}
		}
		Spacer(modifier = Modifier.height(4.dp))
		Row(
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(4.dp)
		) {
			Text(
				text = message.timestamp,
				style = MaterialTheme.typography.labelSmall,
				color = colorScheme.onSurfaceVariant
			)
			if (message.isMine) {
				val icon = when (message.status) {
					MessageStatus.SENT -> Icons.Default.Check
					MessageStatus.DELIVERED -> Icons.Default.DoneAll
					MessageStatus.READ -> Icons.Default.DoneAll
				}
				val tint = if (message.status == MessageStatus.READ) Color.Blue else colorScheme.onSurfaceVariant
				Icon(
					imageVector = icon,
					contentDescription = null,
					modifier = Modifier.size(16.dp),
					tint = tint
				)
			}
		}
	}
}

@Composable
private fun VoiceMessagePlayer(
	attachment: AttachmentItem,
	isMine: Boolean,
	colorScheme: ColorScheme
) {
	var isPlaying by remember { mutableStateOf(false) }
	var mediaPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
	val context = LocalContext.current
	
	DisposableEffect(Unit) {
		onDispose {
			mediaPlayer?.release()
		}
	}
	
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.background(
				if (isMine) colorScheme.primaryContainer.copy(alpha = 0.3f)
				else colorScheme.secondaryContainer.copy(alpha = 0.5f),
				RoundedCornerShape(12.dp)
			)
			.padding(8.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		FilledIconButton(
			onClick = {
				if (isPlaying) {
					mediaPlayer?.pause()
					isPlaying = false
				} else {
					if (mediaPlayer == null) {
						try {
							val audioBytes = android.util.Base64.decode(attachment.dataBase64, android.util.Base64.DEFAULT)
							val tempFile = java.io.File.createTempFile("voice_", ".m4a", context.cacheDir)
							tempFile.writeBytes(audioBytes)
							
							val player = android.media.MediaPlayer().apply {
								setDataSource(tempFile.absolutePath)
								prepare()
								setOnCompletionListener {
									isPlaying = false
								}
								start()
							}
							mediaPlayer = player
							isPlaying = true
						} catch (e: Exception) {
							e.printStackTrace()
						}
					} else {
						mediaPlayer?.start()
						isPlaying = true
					}
				}
			},
			colors = IconButtonDefaults.filledIconButtonColors(
				containerColor = if (isMine) colorScheme.primary else colorScheme.secondary
			),
			modifier = Modifier.size(40.dp)
		) {
			Icon(
				imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
				contentDescription = if (isPlaying) "Stop" else "Play",
				tint = if (isMine) colorScheme.onPrimary else colorScheme.onSecondary
			)
		}
		Spacer(modifier = Modifier.width(12.dp))
		Column {
			Text(
				text = stringResource(R.string.voice_message),
				style = MaterialTheme.typography.bodyMedium,
				fontWeight = FontWeight.Medium
			)
		}
	}
}

/**
 * Диалог с извлечёнными AI встречами
 */
@Composable
private fun ExtractedMeetingsDialog(
	meetings: List<ExtractedMeetingInfo>,
	onCreateMeeting: (ExtractedMeetingInfo) -> Unit,
	onDismiss: () -> Unit
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(8.dp)
			) {
				Icon(
					imageVector = Icons.Default.SmartToy,
					contentDescription = null,
					tint = MaterialTheme.colorScheme.primary
				)
				Text(stringResource(R.string.ai_meetings_dialog_title))
			}
		},
		text = {
			LazyColumn(
				verticalArrangement = Arrangement.spacedBy(12.dp)
			) {
				items(meetings) { meeting ->
					ExtractedMeetingCard(
						meeting = meeting,
						onCreateMeeting = { onCreateMeeting(meeting) }
					)
				}
			}
		},
		confirmButton = {
			TextButton(onClick = onDismiss) {
				Text(stringResource(R.string.cancel))
			}
		}
	)
}

/**
 * Карточка извлечённой встречи
 */
@Composable
private fun ExtractedMeetingCard(
	meeting: ExtractedMeetingInfo,
	onCreateMeeting: () -> Unit
) {
	val colorScheme = MaterialTheme.colorScheme
	
	Surface(
		shape = RoundedCornerShape(12.dp),
		color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
		border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.3f))
	) {
		Column(
			modifier = Modifier.padding(12.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			// Заголовок
			Text(
				text = meeting.title,
				style = MaterialTheme.typography.titleSmall,
				fontWeight = FontWeight.SemiBold
			)
			
			// Описание
			meeting.description?.let { desc ->
				Text(
					text = desc,
					style = MaterialTheme.typography.bodySmall,
					color = colorScheme.onSurfaceVariant,
					maxLines = 3,
					overflow = TextOverflow.Ellipsis
				)
			}
			
			// Дата и место
			Row(
				horizontalArrangement = Arrangement.spacedBy(16.dp)
			) {
				meeting.dateTime?.let { dateTime ->
					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(4.dp)
					) {
						Icon(
							imageVector = Icons.Default.CalendarMonth,
							contentDescription = null,
							modifier = Modifier.size(14.dp),
							tint = colorScheme.primary
						)
						Text(
							text = dateTime,
							style = MaterialTheme.typography.labelSmall,
							color = colorScheme.primary
						)
					}
				}
			}
			
			// Кнопка создания встречи
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.End,
				verticalAlignment = Alignment.CenterVertically
			) {
				Button(
					onClick = onCreateMeeting,
					contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
				) {
					Icon(
						imageVector = Icons.Default.Check,
						contentDescription = null,
						modifier = Modifier.size(16.dp)
					)
					Spacer(modifier = Modifier.width(4.dp))
					Text(
						text = stringResource(R.string.ai_create_meeting),
						style = MaterialTheme.typography.labelMedium
					)
				}
			}
		}
	}
}