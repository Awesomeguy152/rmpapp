package com.nano.min.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
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
import com.nano.min.viewmodel.MessageItem
import com.nano.min.viewmodel.ContactSuggestion
import com.nano.min.viewmodel.MemberInfo
import com.nano.min.network.ConversationType
import com.nano.min.network.MessageStatus
import com.nano.min.viewmodel.PendingAttachment
import org.koin.androidx.compose.koinViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRootScreen(
	onLogout: () -> Unit,
	darkTheme: Boolean,
	onToggleTheme: (Boolean) -> Unit,
	viewModel: ChatsViewModel = koinViewModel()
) {
	val conversationState by viewModel.conversationState.collectAsStateWithLifecycle()
	val detailState by viewModel.detailState.collectAsStateWithLifecycle()

	val snackbarHostState = remember { SnackbarHostState() }
	var showCreateSheet by rememberSaveable { mutableStateOf(false) }
	val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

	val context = LocalContext.current
	val scope = rememberCoroutineScope()

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

	val attachmentPicker = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.GetMultipleContents()
	) { uris ->
		scope.launch(Dispatchers.IO) {
			val attachments = uris.mapNotNull { uri ->
				context.contentResolver.openInputStream(uri)?.use { stream ->
					val fileName = resolveFileName(context, uri) ?: "file"
					val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
					val base64 = stream.readBytes().let { bytes ->
						Base64.encodeToString(bytes, Base64.NO_WRAP)
					}
					PendingAttachment(
						id = UUID.randomUUID().toString(),
						fileName = fileName,
						contentType = mime,
						dataBase64 = base64
					)
				}
			}
			withContext(Dispatchers.Main) {
				viewModel.addPendingAttachments(attachments)
			}
		}
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
				colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
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
						conversationState.profileEmail?.let { email ->
							Surface(
								shape = RoundedCornerShape(18.dp),
								color = colorScheme.primary.copy(alpha = 0.08f),
								border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.35f))
							) {
								Text(
									text = email,
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
					IconButton(onClick = { onToggleTheme(!darkTheme) }) {
						Icon(
							imageVector = if (darkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
							contentDescription = stringResource(R.string.action_toggle_theme)
						)
					}
					IconButton(onClick = {
						viewModel.logout()
						onLogout()
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
							onPickAttachments = { attachmentPicker.launch("*/*") },
							onRemoveAttachment = viewModel::removePendingAttachment,
							onSend = { viewModel.sendMessage() },
							onEditMessage = viewModel::editMessage,
							onDeleteMessage = viewModel::deleteMessage,
							onUpdateTopic = viewModel::updateCurrentConversationTopic,
							onAddMembers = viewModel::addMembersToCurrentConversation,
							onRemoveMember = viewModel::removeMemberFromCurrentConversation,
							onBack = { viewModel.clearConversationSelection() },
							modifier = Modifier.fillMaxSize()
						)
					} else {
						ConversationListPanel(
							state = conversationState,
							selectedConversationId = detailState.conversation?.id,
							onCreateConversation = { showCreateSheet = true },
							onRefresh = { viewModel.refreshConversations() },
							onSelectConversation = { viewModel.selectConversation(it) },
							onSearchQueryChange = viewModel::updateSearchQuery,
							onOpenMessageFromSearch = { convoId, _ -> viewModel.selectConversation(convoId) },
							onStartChatWithUser = viewModel::startDirectConversationFromSearch,
							onTogglePin = { id, pin -> viewModel.togglePin(id, pin) },
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
							onSelectConversation = { viewModel.selectConversation(it) },
							onSearchQueryChange = viewModel::updateSearchQuery,
							onOpenMessageFromSearch = { convoId, _ -> viewModel.selectConversation(convoId) },
							onStartChatWithUser = viewModel::startDirectConversationFromSearch,
							onTogglePin = { id, pin -> viewModel.togglePin(id, pin) },
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
							onPickAttachments = { attachmentPicker.launch("*/*") },
							onRemoveAttachment = viewModel::removePendingAttachment,
							onSend = { viewModel.sendMessage() },
							onEditMessage = viewModel::editMessage,
							onDeleteMessage = viewModel::deleteMessage,
							onUpdateTopic = viewModel::updateCurrentConversationTopic,
							onAddMembers = viewModel::addMembersToCurrentConversation,
							onRemoveMember = viewModel::removeMemberFromCurrentConversation,
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
	onSelectConversation: (String) -> Unit,
	onSearchQueryChange: (String) -> Unit,
	onOpenMessageFromSearch: (String, String) -> Unit,
	onStartChatWithUser: (String) -> Unit,
	onTogglePin: (String, Boolean) -> Unit,
	modifier: Modifier = Modifier
) {
	val colorScheme = MaterialTheme.colorScheme
	Column(
		modifier = modifier
			.fillMaxSize()
			.padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(16.dp)
	) {
		OutlinedTextField(
			value = state.searchQuery,
			onValueChange = onSearchQueryChange,
			placeholder = { Text(stringResource(R.string.search_messages_placeholder)) },
			leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
			trailingIcon = {
				if (state.searchQuery.isNotBlank()) {
					IconButton(onClick = { onSearchQueryChange("") }) {
						Icon(imageVector = Icons.Filled.Clear, contentDescription = stringResource(R.string.action_clear_input))
					}
				}
			},
			singleLine = true,
			shape = RoundedCornerShape(18.dp),
			colors = OutlinedTextFieldDefaults.colors(
				focusedBorderColor = colorScheme.primary,
				unfocusedBorderColor = colorScheme.outlineVariant,
				focusedContainerColor = colorScheme.surface,
				unfocusedContainerColor = colorScheme.surface
			)
		)

		if (state.isSearching) {
			LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
		}

		state.searchError?.let { error ->
			Surface(
				color = MaterialTheme.colorScheme.errorContainer,
				contentColor = MaterialTheme.colorScheme.onErrorContainer,
				shape = RoundedCornerShape(18.dp),
				modifier = Modifier.fillMaxWidth()
			) {
				Text(text = error, modifier = Modifier.padding(12.dp))
			}
		}

		if (state.searchQuery.isNotBlank()) {
			Surface(
				shape = RoundedCornerShape(20.dp),
				tonalElevation = 4.dp,
				modifier = Modifier.fillMaxWidth()
			) {
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.verticalScroll(rememberScrollState())
						.padding(14.dp),
					verticalArrangement = Arrangement.spacedBy(12.dp)
				) {
					if (state.searchMessageResults.isNotEmpty()) {
						Text(
							text = stringResource(R.string.search_messages_results),
							style = MaterialTheme.typography.titleSmall,
							color = MaterialTheme.colorScheme.onSurface
						)
						state.searchMessageResults.forEach { result ->
							Surface(
								shape = RoundedCornerShape(14.dp),
								modifier = Modifier
									.fillMaxWidth()
									.clickable { onOpenMessageFromSearch(result.conversationId, result.id) },
								color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
							) {
								Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
									Text(
										text = result.conversationTitle,
										style = MaterialTheme.typography.labelMedium,
										color = MaterialTheme.colorScheme.primary
									)
									Text(
										text = result.preview,
										style = MaterialTheme.typography.bodyMedium,
										color = MaterialTheme.colorScheme.onSurface,
										maxLines = 2,
										overflow = TextOverflow.Ellipsis
									)
									Text(
										text = result.timestamp,
										style = MaterialTheme.typography.labelSmall,
										color = MaterialTheme.colorScheme.onSurfaceVariant
									)
								}
							}
						}
					}

					if (state.searchUserResults.isNotEmpty()) {
						Text(
							text = stringResource(R.string.search_contacts_results),
							style = MaterialTheme.typography.titleSmall,
							color = MaterialTheme.colorScheme.onSurface
						)
						state.searchUserResults.forEach { user ->
							Surface(
								shape = RoundedCornerShape(14.dp),
								modifier = Modifier.fillMaxWidth(),
								color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
							) {
								Row(
									modifier = Modifier
										.fillMaxWidth()
										.padding(12.dp),
									horizontalArrangement = Arrangement.SpaceBetween,
									verticalAlignment = Alignment.CenterVertically
								) {
									Column(modifier = Modifier.weight(1f)) {
										Text(
											text = user.email,
											style = MaterialTheme.typography.bodyMedium,
											color = MaterialTheme.colorScheme.onSurface,
											maxLines = 1,
											overflow = TextOverflow.Ellipsis
										)
										Text(
											text = user.role,
											style = MaterialTheme.typography.labelSmall,
											color = MaterialTheme.colorScheme.onSurfaceVariant
										)
									}
									Button(onClick = { onStartChatWithUser(user.id) }) {
										Text(text = stringResource(R.string.conversation_create))
									}
								}
							}
						}
					}
				}
			}
		}
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
				Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
			when {
				state.isLoading && state.conversations.isEmpty() -> {
					Box(
						modifier = Modifier.fillMaxSize(),
						contentAlignment = Alignment.Center
					) {
						CircularProgressIndicator()
					}
				}

				state.conversations.isEmpty() -> {
					Column(
						modifier = Modifier
							.fillMaxSize()
							.padding(24.dp),
						horizontalAlignment = Alignment.CenterHorizontally,
						verticalArrangement = Arrangement.Center
					) {
						Icon(
							imageVector = Icons.AutoMirrored.Filled.Send,
							contentDescription = null,
							tint = MaterialTheme.colorScheme.onSurfaceVariant,
							modifier = Modifier.size(48.dp)
						)
						Spacer(modifier = Modifier.height(12.dp))
						Text(
							text = stringResource(R.string.conversation_list_empty),
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							textAlign = TextAlign.Center
						)
					}
				}

				else -> {
					LazyColumn(
						modifier = Modifier.fillMaxSize(),
						contentPadding = PaddingValues(vertical = 16.dp),
						verticalArrangement = Arrangement.spacedBy(12.dp)
					) {
						items(state.conversations, key = { it.id }) { conversation ->
							ConversationListItem(
								conversation = conversation,
								isSelected = conversation.id == selectedConversationId,
								onClick = { onSelectConversation(conversation.id) },
								onTogglePin = { pin -> onTogglePin(conversation.id, pin) }
							)
						}
					}
				}
			}
		}
	}
}

@Composable
private fun ConversationListItem(
	conversation: ConversationItem,
	isSelected: Boolean,
	onClick: () -> Unit,
	onTogglePin: (Boolean) -> Unit
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
			.clickable(onClick = onClick)
	) {
		Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
			Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
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
				Column(modifier = Modifier.weight(1f)) {
					Row(verticalAlignment = Alignment.CenterVertically) {
						Text(
							text = conversation.title,
							style = MaterialTheme.typography.titleMedium,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
							modifier = Modifier.weight(1f, fill = false)
						)
						conversation.lastMessageTime?.let { time ->
							Text(
								text = time,
								style = MaterialTheme.typography.labelSmall,
								color = colorScheme.onSurfaceVariant
							)
						}
						IconButton(
							onClick = { onTogglePin(conversation.pinnedAt == null) },
							modifier = Modifier.size(32.dp)
						) {
							Icon(
								imageVector = if (conversation.pinnedAt != null) Icons.Filled.PushPin else Icons.Outlined.PushPin,
								contentDescription = null,
								tint = if (conversation.pinnedAt != null) colorScheme.primary else colorScheme.onSurfaceVariant
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
					Text(
						text = suggestion.email,
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurface
					)
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
	val colorScheme = MaterialTheme.colorScheme

	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 24.dp, vertical = 16.dp),
		verticalArrangement = Arrangement.spacedBy(16.dp)
	) {
		Text(
			text = stringResource(R.string.conversation_create_title),
			style = MaterialTheme.typography.headlineSmall
		)

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

		if (suggestions.isNotEmpty()) {
			ContactSuggestionList(
				suggestions = suggestions,
				onSuggestionSelected = { suggestion ->
					memberInput = suggestion.id
					topicInput = suggestion.email.takeIf { topicInput.isBlank() } ?: topicInput
					onClearSuggestions()
				}
			)
		} else {
			Text(
				text = stringResource(R.string.conversation_create_hint),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}

		val canCreate = memberInput.split(',', ';', ' ', '\n').any { it.trim().isNotEmpty() }

		Button(
			onClick = {
				val normalizedMembers = memberInput.trim()
				val normalizedTopic = topicInput.trim().ifBlank { null }
				onCreateConversation(normalizedMembers, normalizedTopic)
				memberInput = ""
				topicInput = ""
				onClearSuggestions()
			},
			enabled = canCreate,
			shape = RoundedCornerShape(18.dp),
			modifier = Modifier
				.fillMaxWidth()
				.heightIn(min = 52.dp)
		) {
			Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null)
			Spacer(modifier = Modifier.width(8.dp))
			Text(text = stringResource(R.string.conversation_create))
		}
	}
}

@Composable
private fun ConversationDetailPanel(
	state: ConversationDetailUiState,
	currentUserId: String?,
	onMessageChange: (String) -> Unit,
	onPickAttachments: () -> Unit,
	onRemoveAttachment: (String) -> Unit,
	onSend: () -> Unit,
	onEditMessage: (String, String) -> Unit,
	onDeleteMessage: (String) -> Unit,
	onUpdateTopic: (String) -> Unit,
	onAddMembers: (String) -> Unit,
	onRemoveMember: (String) -> Unit,
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

	val isGroupConversation = conversation.raw.type == ConversationType.GROUP
	val isOwner = currentUserId != null && conversation.raw.createdBy == currentUserId
	val colorScheme = MaterialTheme.colorScheme
	val listState = rememberLazyListState()
	val headerItemsCount = (if (onBack != null) 1 else 0) +
		1 +
		(if (isGroupConversation) 1 else if (conversation.members.isNotEmpty()) 1 else 0) +
		(if (state.isLoading) 1 else 0) +
		(if (state.error != null) 1 else 0)

	LaunchedEffect(state.messages.size) {
		if (state.messages.isNotEmpty()) {
			val targetIndex = headerItemsCount + state.messages.lastIndex
			listState.scrollToItem(targetIndex)
		}
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
			var editingMessage by rememberSaveable { mutableStateOf<MessageItem?>(null) }
			var editingText by rememberSaveable { mutableStateOf("") }

			if (editingMessage != null) {
				AlertDialog(
					onDismissRequest = {
						editingMessage = null
						editingText = ""
					},
					title = { Text(stringResource(R.string.edit_message_title)) },
					text = {
						Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
							OutlinedTextField(
								value = editingText,
								onValueChange = { editingText = it },
								placeholder = { Text(stringResource(R.string.edit_message_hint)) },
								maxLines = 4,
								modifier = Modifier.fillMaxWidth()
							)
						}
					},
					confirmButton = {
						TextButton(
							enabled = editingText.isNotBlank(),
							onClick = {
								editingMessage?.let { onEditMessage(it.id, editingText) }
								editingMessage = null
								editingText = ""
							}
						) {
							Text(stringResource(R.string.action_save))
						}
					},
					dismissButton = {
						TextButton(onClick = {
							editingMessage = null
							editingText = ""
						}) {
							Text(stringResource(R.string.action_cancel))
						}
					}
				)
			}

			LazyColumn(
				modifier = Modifier
					.weight(1f)
					.fillMaxWidth(),
				state = listState,
				contentPadding = PaddingValues(bottom = 16.dp),
				verticalArrangement = Arrangement.spacedBy(14.dp)
			) {
			if (onBack != null) {
				item("back_${conversation.id}") {
					Row(verticalAlignment = Alignment.CenterVertically) {
						IconButton(onClick = onBack) {
							Icon(
								imageVector = Icons.AutoMirrored.Filled.ArrowBack,
								contentDescription = stringResource(R.string.conversation_back)
							)
						}
						Text(
							text = stringResource(R.string.conversation_back),
							style = MaterialTheme.typography.titleMedium
						)
					}
				}
			}

			item("header_${conversation.id}") {
				Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
					Text(
						text = conversation.title,
						style = MaterialTheme.typography.headlineSmall,
						fontWeight = FontWeight.SemiBold,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
					Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
						conversation.lastMessageTime?.let { time ->
							Text(
								text = time,
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

			if (isGroupConversation) {
				item("group_${conversation.id}") {
					GroupManagementSection(
						conversation = conversation,
						currentUserId = currentUserId,
						isOwner = isOwner,
						isBusy = state.isLoading || state.isSending,
						onUpdateTopic = onUpdateTopic,
						onAddMembers = onAddMembers,
						onRemoveMember = onRemoveMember
					)
				}
			} else if (conversation.members.isNotEmpty()) {
				item("members_${conversation.id}") {
					Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
						Text(
							text = stringResource(R.string.conversation_members_title),
							style = MaterialTheme.typography.labelSmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant
						)
						conversation.members.forEach { member ->
							Text(
								text = stringResource(
									R.string.conversation_member_entry,
									member.id.takeLast(6),
									member.joinedAt
								),
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
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
				items(
					items = state.messages,
					key = { it.id }
				) { message ->
					Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
						MessageBubble(message = message)
						if (message.isMine && !message.isDeleted) {
							Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.align(Alignment.End)) {
								AssistChip(
									onClick = {
										editingMessage = message
										editingText = message.text
									},
									label = { Text(stringResource(R.string.action_edit)) },
									leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) }
								)
								AssistChip(
									onClick = { onDeleteMessage(message.id) },
									label = { Text(stringResource(R.string.action_delete)) },
									leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
									colors = AssistChipDefaults.assistChipColors(
										containerColor = MaterialTheme.colorScheme.errorContainer,
										labelColor = MaterialTheme.colorScheme.onErrorContainer,
										leadingIconContentColor = MaterialTheme.colorScheme.onErrorContainer
									)
								)
							}
						}
					}
				}
			}
		}

		val hasAttachments = state.attachments.isNotEmpty()
		val sendEnabled = (state.messageInput.isNotBlank() || hasAttachments) && !state.isSending

		if (hasAttachments) {
			LazyRow(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(8.dp)
			) {
				items(state.attachments, key = { it.id }) { attachment ->
					Surface(
						shape = RoundedCornerShape(16.dp),
						color = colorScheme.surfaceVariant,
						border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f))
					) {
						Row(
							modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
							horizontalArrangement = Arrangement.spacedBy(8.dp),
							verticalAlignment = Alignment.CenterVertically
						) {
							Icon(
								imageVector = Icons.Filled.AttachFile,
								contentDescription = null,
								tint = colorScheme.onSurfaceVariant
							)
							Column(modifier = Modifier.weight(1f)) {
								Text(
									text = attachment.fileName,
									style = MaterialTheme.typography.bodySmall,
									color = colorScheme.onSurface
								)
								Text(
									text = attachment.contentType,
									style = MaterialTheme.typography.labelSmall,
									color = colorScheme.onSurfaceVariant
								)
							}
							IconButton(onClick = { onRemoveAttachment(attachment.id) }) {
								Icon(
									imageVector = Icons.Filled.Clear,
									contentDescription = stringResource(R.string.remove_attachment)
								)
							}
						}
					}
				}
			}
		}

		Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
			IconButton(
				onClick = onPickAttachments,
				enabled = !state.isSending
			) {
				Icon(
					imageVector = Icons.Filled.AttachFile,
					contentDescription = stringResource(R.string.add_attachment)
				)
			}
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
						onSend()
					}
				})
			)
			FilledIconButton(
				onClick = onSend,
				enabled = sendEnabled,
				shape = RoundedCornerShape(18.dp),
				colors = IconButtonDefaults.filledIconButtonColors(
					containerColor = if (sendEnabled) colorScheme.primary else colorScheme.primary.copy(alpha = 0.4f)
				)
			) {
				if (state.isSending) {
					CircularProgressIndicator(
						modifier = Modifier.size(18.dp),
						strokeWidth = 2.dp,
						color = colorScheme.onPrimary
					)
				} else {
					Icon(
						imageVector = Icons.AutoMirrored.Filled.Send,
						contentDescription = stringResource(R.string.action_send),
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
	onRemoveMember: (String) -> Unit
) {
	var topicText by rememberSaveable(conversation.id, "topic") {
		mutableStateOf(conversation.raw.topic.orEmpty())
	}
	var membersInput by rememberSaveable(conversation.id, "membersInput") {
		mutableStateOf("")
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
					text = stringResource(
						R.string.conversation_member_entry,
						member.id.takeLast(6),
						member.joinedAt
					),
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

@Composable
private fun MessageBubble(message: MessageItem) {
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
		val showTextBubble = message.text.isNotBlank() || message.isDeleted
		val bubbleColor = when {
			message.isDeleted -> colorScheme.surfaceVariant.copy(alpha = if (message.isMine) 0.4f else 0.65f)
			message.isMine -> colorScheme.primary
			else -> colorScheme.surface
		}
		val bubbleContentColor = when {
			message.isDeleted -> colorScheme.onSurfaceVariant
			message.isMine -> colorScheme.onPrimary
			else -> colorScheme.onSurface
		}
		if (showTextBubble) {
			Surface(
				color = bubbleColor,
				contentColor = bubbleContentColor,
				shape = bubbleShape,
				border = if (message.isMine || message.isDeleted) null else BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.35f)),
				tonalElevation = if (message.isMine && !message.isDeleted) 6.dp else 2.dp,
				shadowElevation = if (message.isMine && !message.isDeleted) 6.dp else 0.dp,
				modifier = Modifier.alpha(if (message.isDeleted) 0.8f else 1f)
			) {
				Text(
					text = if (message.isDeleted) "Сообщение удалено" else message.text,
					modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
					style = MaterialTheme.typography.bodyMedium,
					color = bubbleContentColor,
					fontStyle = if (message.isDeleted) FontStyle.Italic else FontStyle.Normal
				)
			}
		}

		if (message.attachments.isNotEmpty() && !message.isDeleted) {
			Spacer(modifier = Modifier.height(6.dp))
			Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
				message.attachments.forEach { attachment ->
					Surface(
						shape = RoundedCornerShape(14.dp),
						color = colorScheme.surfaceVariant.copy(alpha = if (message.isMine) 0.45f else 0.85f),
						border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.4f))
					) {
						Row(
							modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
							horizontalArrangement = Arrangement.spacedBy(10.dp),
							verticalAlignment = Alignment.CenterVertically
						) {
							Icon(
								imageVector = Icons.Filled.AttachFile,
								contentDescription = null,
								tint = colorScheme.onSurfaceVariant
							)
							Column(modifier = Modifier.weight(1f)) {
								Text(
									text = attachment.fileName,
									style = MaterialTheme.typography.bodyMedium,
									color = colorScheme.onSurface
								)
								Text(
									text = attachment.contentType,
									style = MaterialTheme.typography.labelSmall,
									color = colorScheme.onSurfaceVariant
								)
							}
						}
					}
				}
			}
		}

		if (showTextBubble || message.attachments.isNotEmpty()) {
			Spacer(modifier = Modifier.height(4.dp))
		}
		Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
			if (message.isMine && !message.isDeleted) {
				val (statusIcon, statusColor) = when (message.status) {
					MessageStatus.SENT -> Icons.Filled.Done to MaterialTheme.colorScheme.onSurfaceVariant
					MessageStatus.DELIVERED -> Icons.Filled.DoneAll to MaterialTheme.colorScheme.onSurfaceVariant
					MessageStatus.READ -> Icons.Filled.DoneAll to MaterialTheme.colorScheme.primary
					else -> Icons.Filled.Done to MaterialTheme.colorScheme.onSurfaceVariant
				}
				Icon(
					imageVector = statusIcon,
					contentDescription = null,
					tint = statusColor,
					modifier = Modifier.size(16.dp)
				)
			}
			Text(
				text = message.timestamp,
				style = MaterialTheme.typography.labelSmall,
				color = colorScheme.onSurfaceVariant
			)
			if (message.isEdited && !message.isDeleted) {
				Text(
					text = "(изменено)",
					style = MaterialTheme.typography.labelSmall,
					color = colorScheme.onSurfaceVariant
				)
			}
			if (message.isDeleted) {
				Text(
					text = "(удалено)",
					style = MaterialTheme.typography.labelSmall,
					color = colorScheme.onSurfaceVariant,
					fontStyle = FontStyle.Italic
				)
			}
		}
	}
}

private fun resolveFileName(context: Context, uri: Uri): String? {
	return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
		?.use { cursor ->
			val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
			if (nameIndex == -1) return null
			if (cursor.moveToFirst()) cursor.getString(nameIndex) else null
		}
}