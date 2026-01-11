package com.nano.min.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    secondary = TextGray,
    onSecondary = Color.White,
    tertiary = AccentLime,
    background = LightBackground,
    onBackground = Color(0xFF111827),
    surface = Color.White,
    onSurface = Color(0xFF111827),
    outlineVariant = LightOutline
)

private val DarkColorScheme = darkColorScheme(
    primary = AccentLime,
    onPrimary = Color(0xFF0A0F0A),
    secondary = Color(0xFF7DD3FC),
    onSecondary = Color(0xFF081018),
    tertiary = PrimaryBlue,
    background = DarkBackground,
    onBackground = Color(0xFFE5E7EB),
    surface = DarkSurface,
    onSurface = Color(0xFFE5E7EB),
    outlineVariant = DarkOutline
)

@Composable
fun MinTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
//     Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}