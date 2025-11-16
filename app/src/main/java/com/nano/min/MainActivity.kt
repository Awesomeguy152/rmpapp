package com.nano.min

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nano.min.navigation.AppNavigationRoot
import com.nano.min.ui.theme.MinTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MinTheme {
                MinApp()
            }
        }
    }
}

@Composable
fun MinApp() {
    AppNavigationRoot(
        modifier = Modifier.padding(8.dp),
    )
}