package com.example.ui.theme

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

private val StudioDarkColorScheme = darkColorScheme(
    primary = CyanNeon,
    onPrimary = Color(0xFF00222B),
    primaryContainer = Color(0xFF003644),
    onPrimaryContainer = Color(0xFF8CEEFF),

    secondary = ElectricViolet,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF321B66),
    onSecondaryContainer = Color(0xFFD6C6FF),

    tertiary = AmberGold,
    onTertiary = Color(0xFF261900),
    tertiaryContainer = Color(0xFF473100),
    onTertiaryContainer = Color(0xFFFFDF9E),

    background = StudioBackground,
    onBackground = DarkTextPrimary,
    surface = StudioSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = StudioSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = StudioSurfaceBorder,

    error = DangerRed,
    onError = Color.White
)

private val StudioLightColorScheme = lightColorScheme(
    primary = CyanAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F7FA),
    onPrimaryContainer = Color(0xFF004D5A),

    secondary = ElectricViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE7F6),
    onSecondaryContainer = Color(0xFF311B92),

    tertiary = AmberGold,
    onTertiary = Color.Black,

    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),

    error = DangerRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Editor visual prioritiza o tema de estúdio escuro profissional
    dynamicColor: Boolean = false, // Mantém a identidade visual consistente de estúdio
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) StudioDarkColorScheme else StudioLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
