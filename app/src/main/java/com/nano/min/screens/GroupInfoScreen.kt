package com.nano.min.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nano.min.R
import com.nano.min.viewmodel.ConversationItem
import com.nano.min.viewmodel.MemberInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoScreen(
    conversation: ConversationItem,
    currentUserId: String?,
    isOwner: Boolean,
    isBusy: Boolean,
    onUpdateTopic: (String) -> Unit,
    onAddMembers: (String) -> Unit,
    onRemoveMember: (String) -> Unit,
    onLeaveConversation: () -> Unit,
    onBack: () -> Unit
) {
    var topicText by rememberSaveable(conversation.id, "topic") {
        mutableStateOf(conversation.raw.topic.orEmpty())
    }
    var membersInput by rememberSaveable(conversation.id, "membersInput") {
        mutableStateOf("")
    }
    var showLeaveDialog by remember { mutableStateOf(false) }
    
    // Обновляем topic при изменении
    LaunchedEffect(conversation.raw.topic) {
        topicText = conversation.raw.topic.orEmpty()
    }
    
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
                        onBack()
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.group_info_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Название группы
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Group,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = conversation.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        
                        Text(
                            text = stringResource(R.string.conversation_members_count, conversation.members.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Тема группы (только для владельца)
            if (isOwner) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.conversation_group_owner_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            
                            OutlinedTextField(
                                value = topicText,
                                onValueChange = { topicText = it },
                                label = { Text(stringResource(R.string.conversation_topic_hint)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
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
                }
            }
            
            // Участники
            item {
                Text(
                    text = stringResource(R.string.conversation_members_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            items(conversation.members, key = { it.id }) { member ->
                GroupMemberCard(
                    member = member,
                    canRemove = isOwner && member.id != currentUserId,
                    isBusy = isBusy,
                    onRemove = onRemoveMember
                )
            }
            
            // Добавить участников (только для владельца)
            if (isOwner) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.PersonAdd,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = stringResource(R.string.conversation_add_members),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            
                            OutlinedTextField(
                                value = membersInput,
                                onValueChange = { membersInput = it },
                                label = { Text(stringResource(R.string.conversation_member_hint)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
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
            
            // Кнопка выхода из группы
            item {
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
    }
}

@Composable
private fun GroupMemberCard(
    member: MemberInfo,
    canRemove: Boolean,
    isBusy: Boolean,
    onRemove: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = member.name ?: stringResource(R.string.conversation_member_entry, member.id.takeLast(6), member.joinedAt),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(R.string.profile_joined_value, member.joinedAt),
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
                        contentDescription = stringResource(R.string.remove_member),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
