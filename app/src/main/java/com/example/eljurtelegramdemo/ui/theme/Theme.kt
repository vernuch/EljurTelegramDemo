package com.example.eljurtelegramdemo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightGreenColorScheme = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    secondary = Color(0xFF66BB6A),
    onSecondary = Color.White,
    background = Color(0xFFE8F5E9),
    onBackground = Color(0xFF0D1F13),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0D1F13),
)

private val DarkGreenColorScheme = darkColorScheme(
    primary = Color(0xFF81C784),
    onPrimary = Color(0xFF003314),
    secondary = Color(0xFFA5D6A7),
    onSecondary = Color(0xFF003314),
    background = Color(0xFF06110A),
    onBackground = Color(0xFFE8F5E9),
    surface = Color(0xFF122017),
    onSurface = Color(0xFFE8F5E9),
)

@Composable
fun EljurTelegramDemoTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkGreenColorScheme else LightGreenColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
