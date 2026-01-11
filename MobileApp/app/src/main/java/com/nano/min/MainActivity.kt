package com.nano.min

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nano.min.navigation.AppNavigationRoot
import com.nano.min.ui.theme.MinTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val systemDark = isSystemInDarkTheme()
            var darkTheme by rememberSaveable { mutableStateOf(systemDark) }

            MinTheme(darkTheme = darkTheme) {
                MinApp(
                    darkTheme = darkTheme,
                    onToggleTheme = { darkTheme = it }
                )
            }
        }
    }
}

@Composable
fun MinApp(
    darkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit
) {
    AppNavigationRoot(
        modifier = Modifier.padding(8.dp),
        darkTheme = darkTheme,
        onToggleTheme = onToggleTheme
    )
}