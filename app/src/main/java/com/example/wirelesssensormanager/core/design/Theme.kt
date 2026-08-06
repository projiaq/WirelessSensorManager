package com.example.wirelesssensormanager.core.design

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val IndustrialColors = lightColorScheme(
    primary = Color(0xFF155E75), onPrimary = Color.White,
    secondary = Color(0xFF3F6212), onSecondary = Color.White,
    tertiary = Color(0xFF9A3412), background = Color(0xFFF7F8FA),
    surface = Color.White, surfaceVariant = Color(0xFFE8EEF0),
    outline = Color(0xFF66777D), error = Color(0xFFB42318)
)
private val MineColors = darkColorScheme(
    primary = Color(0xFF59D4F0), onPrimary = Color(0xFF002F38),
    secondary = Color(0xFFB7D96C), onSecondary = Color(0xFF213600),
    tertiary = Color(0xFFFFB68E), background = Color(0xFF090D0F),
    surface = Color(0xFF111719), surfaceVariant = Color(0xFF1C292D),
    outline = Color(0xFFB6C8CD), error = Color(0xFFFFB4AB)
)

@Composable fun WirelessSensorTheme(mineMode: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (mineMode) MineColors else IndustrialColors, typography = Typography(), content = content)
}
