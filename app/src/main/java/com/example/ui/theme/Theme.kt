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

private val DarkColorScheme = darkColorScheme(
    primary = DarkBloodRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3B0707),
    onPrimaryContainer = Color(0xFFFFD5D5),
    secondary = DonorCoral,
    onSecondary = Color.White,
    background = DarkGreyBg,
    onBackground = LightText,
    surface = DarkSurface,
    onSurface = LightText,
    error = Color(0xFFEF5350)
)

private val LightColorScheme = lightColorScheme(
    primary = BloodRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFEBF0), // Soft red-100 ambient tint
    onPrimaryContainer = Color(0xFFC62828), // Deep blood red contrast
    secondary = DonorCoral,
    onSecondary = Color.White,
    background = LightRoseBg, // HTML themed #FCF8F8
    onBackground = DarkText,  // HTML text-slate-900 `#0F172A`
    surface = Color.White,
    onSurface = DarkText,
    error = Color(0xFFC62828)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disable dynamic colors to enforce branding
    content: @Composable () -> Unit,
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
