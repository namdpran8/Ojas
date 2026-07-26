package com.pranshu.ojas.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF00FF88),
    secondary = Color(0xFF00AAFF),
    tertiary = Color(0xFFFFAA00),
    background = Color(0xFF0A0E27),
    surface = Color(0xFF1A1F3A),
    surfaceVariant = Color(0xFF1A1F3A),
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    error = Color(0xFFFF4444)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF00A355), // darker green for light mode
    secondary = Color(0xFF0077B3), // darker blue for light mode
    tertiary = Color(0xFFB37700),
    background = Color(0xFFF0F4F8),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE1E8EE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1A1F3A),
    onSurface = Color(0xFF1A1F3A),
    error = Color(0xFFCC0000)
)

@Composable
fun OjasTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
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

val Typography = androidx.compose.material3.Typography()