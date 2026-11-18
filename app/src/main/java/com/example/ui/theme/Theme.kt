package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 方案 2：【死生之律者·纯白圣洁】 Light Theme
private val RebirthPureLightColorScheme = lightColorScheme(
    primary = LightColorPrimary,            // #00C9A7 (蒂芙尼青绿)
    onPrimary = Color.White,
    primaryContainer = LightPrimaryContainer, // #E6F7F5
    onPrimaryContainer = LightOnPrimaryContainer, // #005B48
    secondary = LightColorAccent,           // #2CB5E8 (清澈天蓝)
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2F4FC),
    onSecondaryContainer = Color(0xFF004D6B),
    background = LightBgPrimary,             // #F2F5F8 (极浅冰蓝灰)
    onBackground = LightTextPrimary,         // #0F172A (深蓝黑)
    surface = LightBgCard,                   // #FFFFFF (纯白卡片)
    onSurface = LightTextPrimary,            // #0F172A
    surfaceVariant = Color(0xFFEBF3F7),      // 冰蓝微亮面
    onSurfaceVariant = LightTextSecondary,   // #64748B (中灰)
    outline = LightBorderColor,              // #D1EBF5 (冰蓝微边框)
    outlineVariant = Color(0xFFE1EFF5)
)

// 方案 1：【希儿·原装黑紫】 Dark Theme
private val SeeleDarkColorScheme = darkColorScheme(
    primary = DarkColorPrimary,             // #9D4EDD (希儿神秘紫)
    onPrimary = Color.White,
    primaryContainer = DarkPrimaryContainer, // #2B1A42
    onPrimaryContainer = DarkOnPrimaryContainer, // #E9D5FF
    secondary = DarkColorAccent,            // #00F5D4 (暗夜青绿荧光点缀)
    onSecondary = Color(0xFF00382E),
    secondaryContainer = Color(0xFF093833),
    onSecondaryContainer = DarkColorAccent,
    background = DarkBgPrimary,              // #0B0E14 (极深墨黑)
    onBackground = DarkTextPrimary,          // #FFFFFF (纯白)
    surface = DarkBgCard,                    // #161B26 (深蓝灰卡片)
    onSurface = DarkTextPrimary,             // #FFFFFF
    surfaceVariant = Color(0xFF1E2638),
    onSurfaceVariant = DarkTextSecondary,    // #94A3B8 (冷浅灰)
    outline = DarkBorderColor,               // #2A324B (低调深色微边框)
    outlineVariant = Color(0xFF222B3D)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) SeeleDarkColorScheme else RebirthPureLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
