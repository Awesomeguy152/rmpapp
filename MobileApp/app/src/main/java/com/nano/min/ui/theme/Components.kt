package com.nano.min.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.modifier.modifierLocalConsumer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@ExperimentalMaterial3Api
@Composable
fun ScreenContainer(modifier: Modifier = Modifier, title: String = "", content: @Composable () -> Unit) {
    TopAppBar(title = {
        androidx.compose.material3.Text(text = title)
    })
    content()
}
@Composable
fun LargeTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = Typography.titleLarge,
        modifier = modifier
    )
}

@Composable
fun AppButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier,
        shape = ShapeDefaults.Small,
        enabled = enabled,
    ) {
        Text(text = text)
    }
}

@Preview
@Composable
private fun Components() {
    MinTheme {
        Column(
            Modifier.padding(vertical = 80.dp).fillMaxSize(),
            verticalArrangement = Arrangement.SpaceAround
        ) {

            LargeTitle(text = "Large Title")
            AppButton(
                text = "App Button",
                onClick = {},
                Modifier.fillMaxWidth()
            )

        }
    }
}