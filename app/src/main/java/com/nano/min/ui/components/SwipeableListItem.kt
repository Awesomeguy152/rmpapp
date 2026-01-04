package com.nano.min.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Действия при свайпе
 */
enum class SwipeAction {
    PIN, UNPIN, ARCHIVE, UNARCHIVE, MUTE, UNMUTE, DELETE
}

/**
 * Обёртка для элемента списка с поддержкой свайп-действий
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableListItem(
    isPinned: Boolean = false,
    isArchived: Boolean = false,
    isMuted: Boolean = false,
    onSwipeAction: (SwipeAction) -> Unit,
    content: @Composable () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    
    // Состояние свайпа
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    // Свайп вправо - pin/unpin
                    onSwipeAction(if (isPinned) SwipeAction.UNPIN else SwipeAction.PIN)
                    false // Не dismiss, возвращаем на место
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    // Свайп влево - archive/mute (можно расширить)
                    onSwipeAction(if (isArchived) SwipeAction.UNARCHIVE else SwipeAction.ARCHIVE)
                    false
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
        positionalThreshold = { it * 0.25f }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color by animateColorAsState(
                when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> if (isPinned) colorScheme.tertiary else colorScheme.primary
                    SwipeToDismissBoxValue.EndToStart -> if (isArchived) colorScheme.secondary else colorScheme.secondaryContainer
                    else -> Color.Transparent
                },
                label = "swipe_color"
            )
            val icon = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> if (isPinned) Icons.Default.PushPin else Icons.Default.PushPin
                SwipeToDismissBoxValue.EndToStart -> if (isArchived) Icons.Default.Unarchive else Icons.Default.Archive
                else -> null
            }
            val alignment = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                else -> Alignment.Center
            }
            val scale by animateFloatAsState(
                if (dismissState.targetValue == SwipeToDismissBoxValue.Settled) 0.75f else 1f,
                label = "icon_scale"
            )

            Box(
                Modifier
                    .fillMaxSize()
                    .background(color, RoundedCornerShape(18.dp))
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        modifier = Modifier.scale(scale),
                        tint = Color.White
                    )
                }
            }
        },
        content = { content() }
    )
}

/**
 * Bottom sheet с действиями над чатом
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationActionsSheet(
    isPinned: Boolean,
    isArchived: Boolean,
    isMuted: Boolean,
    isOwner: Boolean,
    onDismiss: () -> Unit,
    onPin: () -> Unit,
    onArchive: () -> Unit,
    onMute: () -> Unit,
    onDelete: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // Pin/Unpin
            ListItem(
                headlineContent = { Text(if (isPinned) "Открепить" else "Закрепить") },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = null,
                        tint = colorScheme.primary
                    )
                },
                modifier = Modifier.clickable {
                    onPin()
                    onDismiss()
                }
            )
            
            // Archive/Unarchive
            ListItem(
                headlineContent = { Text(if (isArchived) "Разархивировать" else "Архивировать") },
                leadingContent = {
                    Icon(
                        imageVector = if (isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                        contentDescription = null,
                        tint = colorScheme.secondary
                    )
                },
                modifier = Modifier.clickable {
                    onArchive()
                    onDismiss()
                }
            )
            
            // Mute/Unmute
            ListItem(
                headlineContent = { Text(if (isMuted) "Включить уведомления" else "Отключить уведомления") },
                leadingContent = {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                        contentDescription = null,
                        tint = colorScheme.tertiary
                    )
                },
                modifier = Modifier.clickable {
                    onMute()
                    onDismiss()
                }
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            // Delete (только для владельца группы или direct)
            ListItem(
                headlineContent = { 
                    Text(
                        text = "Удалить чат",
                        color = colorScheme.error
                    ) 
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = colorScheme.error
                    )
                },
                modifier = Modifier.clickable {
                    onDelete()
                    onDismiss()
                }
            )
        }
    }
}

@Composable
private fun ListItem(
    headlineContent: @Composable () -> Unit,
    leadingContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        leadingContent()
        headlineContent()
    }
}
