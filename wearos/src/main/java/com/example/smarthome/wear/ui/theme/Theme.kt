package com.example.smarthome.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material.MaterialTheme

@Composable
fun SmartHomeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colors = WearColorPalette,
        typography = Typography,
        content = content
    )
}

val WearColorPalette = androidx.wear.compose.material.Colors(
    primary = Purple200,
    primaryVariant = Purple700,
    secondary = Teal200,
    error = Red400
)
