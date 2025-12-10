package com.nano.min.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.nano.min.R

data class Message(val text: String, val isSentByUser: Boolean)

@Composable
fun ChatScreen(
    chatName: String = "Имя пользователя",
    avatarResId: Int = R.drawable.ic_car,
    messages: List<Message> = listOf(
        Message("Привет!", false),
        Message("Привет, как дела?", true),
        Message("Всё хорошфсо!", false)
    )
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Хедер
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { /* back action */ }) {
                Icon(
                    painter = painterResource(R.drawable.ic_back),
                    contentDescription = "Back"
                )
            }

            Text(
                text = chatName,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            )

            Image(
                painter = painterResource(avatarResId),
                contentDescription = "Chat avatar",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }

        // Соо
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            reverseLayout = true
        ) {
            items(messages.reversed()) { msg ->
                MessageItem(msg)
            }
        }

        // Инпут
        var inputText by remember { mutableStateOf("") }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Сообщение") },
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { /* send action */ }) {
                Icon(
                    painter = painterResource(android.R.drawable.ic_menu_send),
                    contentDescription = "Send"
                )
            }
        }
    }
}

@Composable
fun MessageItem(message: Message) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (message.isSentByUser) Arrangement.End else Arrangement.Start
    ) {
        Box {
            Text(
                text = message.text,
                color = if (message.isSentByUser) Color.White else Color.Black,
                modifier = Modifier
                    .background(
                        color = if (message.isSentByUser) Color(0xFF3B82F6) else Color(0xFFE5E5EA),
                        shape = MaterialTheme.shapes.medium
                    )
                    .padding(12.dp)
                    .let {
                        if (message.isSentByUser) {
                            it.clickable { showMenu = true }
                        } else {
                            it
                        }
                    }
            )

            if (message.isSentByUser) {
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Удалить") },
                        onClick = { showMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Переписать") },
                        onClick = { showMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Запланировать") },
                        onClick = { showMenu = false }
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChatScreenPreview() {
    ChatScreen()
}
