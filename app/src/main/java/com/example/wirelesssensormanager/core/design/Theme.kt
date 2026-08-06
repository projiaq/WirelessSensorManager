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

@Composable fun WirelessSensorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = IndustrialColors, typography = Typography(), content = content)
}
