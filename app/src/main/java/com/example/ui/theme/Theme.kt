package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    themeIndex: Int = 0,
    content: @Composable () -> Unit,
) {
    currentThemeIndex.value = themeIndex
    val context = LocalContext.current

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> {
            darkColorScheme(
                primary = NeonCyan,
                secondary = NeonPurple,
                tertiary = NeonMagenta,
                background = SpaceBlack,
                surface = DeepSlate,
                onPrimary = Color.Black,
                onSecondary = Color.Black,
                onTertiary = Color.White,
                onBackground = TextPrimary,
                onSurface = TextPrimary,
                surfaceVariant = CyberGray,
                onSurfaceVariant = TextSecondary,
                outline = BorderColor
            )
        }
        else -> {
            lightColorScheme(
                primary = NeonPurple,
                secondary = NeonCyan,
                tertiary = NeonMagenta,
                background = Color(0xFFF2F2F7),
                surface = Color.White,
                onPrimary = Color.White,
                onSecondary = Color.Black,
                onTertiary = Color.White,
                onBackground = Color.Black,
                onSurface = Color.Black,
                surfaceVariant = Color(0xFFE5E5EA),
                onSurfaceVariant = Color.DarkGray,
                outline = Color(0xFFC7C7CC)
            )
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
