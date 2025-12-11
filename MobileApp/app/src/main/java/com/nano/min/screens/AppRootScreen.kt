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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRootScreen(
	onLogout: () -> Unit,
	viewModel: ChatsViewModel = koinViewModel()
) {
	val conversationState by viewModel.conversationState.collectAsStateWithLifecycle()
	val detailState by viewModel.detailState.collectAsStateWithLifecycle()

	val snackbarHostState = remember { SnackbarHostState() }
	val context = LocalContext.current
	var showCreateSheet by rememberSaveable { mutableStateOf(false) }
	val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
							onSend = { viewModel.sendMessage() },
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
								onClick = { onSelectConversation(conversation.id) }
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
	onClick: () -> Unit
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
	onSend: () -> Unit,
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
					MessageBubble(message = message)
				}
			}
		}

		val sendEnabled = state.messageInput.isNotBlank() && !state.isSending

		Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
		Surface(
			color = if (message.isMine) colorScheme.primary else colorScheme.surface,
			contentColor = if (message.isMine) colorScheme.onPrimary else colorScheme.onSurface,
			shape = bubbleShape,
			border = if (message.isMine) null else BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.35f)),
			tonalElevation = if (message.isMine) 6.dp else 2.dp,
			shadowElevation = if (message.isMine) 6.dp else 0.dp
		) {
			Text(
				text = message.text,
				modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
				style = MaterialTheme.typography.bodyMedium
			)
		}
		Spacer(modifier = Modifier.height(4.dp))
		Text(
			text = message.timestamp,
			style = MaterialTheme.typography.labelSmall,
			color = colorScheme.onSurfaceVariant
		)
	}
}