package com.example.floatingscreencasting.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ==================== 现代深色主题（默认） ====================
private val modernDarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Info,
    onTertiary = Color.White,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    error = Error,
    onError = Color.White,
    outline = Color(0xFF475569),
    outlineVariant = Color(0xFF334155),
    scrim = Color(0xFF000000)
)

// ==================== iOS 风格浅色主题 ====================
private val iosLightColorScheme = lightColorScheme(
    primary = ios_blue,
    onPrimary = Color.White,
    primaryContainer = ios_blue.copy(alpha = 0.1f),
    onPrimaryContainer = ios_blue,
    secondary = ios_text_secondary,
    onSecondary = Color.White,
    tertiary = ios_text_secondary,
    onTertiary = Color.White,
    background = ios_background,
    onBackground = ios_text_primary,
    surface = ios_card_background,
    onSurface = ios_text_primary,
    onSurfaceVariant = ios_text_secondary,
    surfaceVariant = ios_background,
    error = ios_red,
    onError = Color.White,
    outline = ios_separator,
    outlineVariant = ios_separator.copy(alpha = 0.5f)
)

// ==================== iOS 风格深色主题 ====================
private val iosDarkColorScheme = darkColorScheme(
    primary = ios_blue,
    onPrimary = Color.White,
    primaryContainer = ios_blue.copy(alpha = 0.2f),
    onPrimaryContainer = ios_blue.copy(alpha = 0.8f),
    secondary = Color(0xFFACAAB1),
    onSecondary = Color(0xFF2B2930),
    background = Color(0xFF1C1C1E),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF2C2C2E),
    onSurface = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFFACAAB1),
    error = ios_red,
    onError = Color.White,
    outline = Color(0xFF38383A),
    outlineVariant = Color(0xFF38383A).copy(alpha = 0.5f)
)

@Composable
fun FloatingScreenCastingTheme(
    darkTheme: Boolean = true, // 默认使用深色主题
    dynamicColor: Boolean = false,
    modernDesign: Boolean = true, // 使用现代设计
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        modernDesign && darkTheme -> modernDarkColorScheme
        darkTheme -> iosDarkColorScheme
        else -> iosLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = iosTypography,
        content = content
    )
}
