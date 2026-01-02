package com.nano.min.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nano.min.R
import kotlinx.coroutines.launch

// ------------------------ MAIN SCREEN ------------------------
@Composable
fun MainScreen() {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var isViewingProfile by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                UserAccountHeader(
                    avatarResId = R.drawable.ic_car,
                    nickname = "NanoUser",
                    status = "Всегда на связи"
                )
                Spacer(modifier = Modifier.height(12.dp))

                NavigationDrawerItem(
                    label = { Text("Главный экран") },
                    selected = !isViewingProfile,
                    onClick = {
                        isViewingProfile = false
                        scope.launch { drawerState.close() }
                    }
                )
                NavigationDrawerItem(
                    label = { Text("Мой профиль") },
                    selected = isViewingProfile,
                    onClick = {
                        //isViewingProfile = true
                        scope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        if (isViewingProfile) {
            // ProfileScreen has been updated and requires ViewModel injection.
            // This MainScreen seems to be legacy/prototype code.
            // ProfileScreen() 
            Text("Profile Screen is not available in this preview.")
        } else {
            HomeContent(
                onOpenMenu = { scope.launch { drawerState.open() } }
            )
        }
    }
}

// ------------------------ HOME CONTENT ------------------------
@Composable
fun HomeContent(onOpenMenu: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Верхняя панель
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = "Menu",
                modifier = Modifier
                    .size(36.dp)
                    .clickable { onOpenMenu() }
            )
            Spacer(modifier = Modifier.width(8.dp))

            // Поиск
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .background(Color(0xFFEFEFEF), RoundedCornerShape(20.dp))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = "",
                    onValueChange = {},
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        Box {
                            Text("Поиск чата...", color = Color.Gray)
                            innerTextField()
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Image(
                painter = painterResource(R.drawable.new_chat),
                contentDescription = "New Chat",
                modifier = Modifier.size(36.dp)
            )
        }

        // Горизонтальное меню
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp)
        ) {
            repeat(5) { i ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(80.dp, 40.dp)
                        .background(Color.LightGray, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Cat $i")
                }
            }
        }

        // Список чатов
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(10) { index ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text("Chat $index")
                    }
                }
            }
        }
    }
}

// ------------------------ USER HEADER ------------------------
@Composable
fun UserAccountHeader(
    avatarResId: Int,
    nickname: String,
    status: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFEFEFEF)
        ) {
            // Вместо painterResource используйте:
            Icon(
                imageVector = Icons.Filled.Person, // или Icons.Default.AccountCircle
                contentDescription = "User icon",
                modifier = Modifier.size(48.dp) // настройте размер
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = nickname, style = MaterialTheme.typography.titleMedium)
            Text(text = status, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        }
    }
}

// ------------------------ PREVIEW ------------------------
@Preview(showSystemUi = true, showBackground = true)
@Composable
fun MainScreenStaticPreview() {
    MaterialTheme {

        // Заглушки вместо ресурсов
        CompositionLocalProvider {
            Column {
                HomeContent(onOpenMenu = {})
                Spacer(modifier = Modifier.height(20.dp))
                MainScreen()
            }
        }

    }
}

