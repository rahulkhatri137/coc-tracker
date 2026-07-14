package com.rk.clashtracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ClashColorScheme = darkColorScheme(
    primary = ClashGold,
    onPrimary = Color.Black,
    primaryContainer = ClashGoldDark,
    onPrimaryContainer = Color.White,
    secondary = ClashBronze,
    onSecondary = Color.White,
    secondaryContainer = ClashWood,
    onSecondaryContainer = Color.White,
    tertiary = ClashElixir,
    onTertiary = Color.White,
    background = ClashObsidian,
    onBackground = TextPrimary,
    surface = ClashSlate,
    onSurface = TextPrimary,
    surfaceVariant = ClashSlateLight,
    onSurfaceVariant = TextSecondary,
    outline = ClashBronze
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force dark-theme for immersive gaming dashboard feel
    dynamicColor: Boolean = false, // Keep themed colors consistent
    content: @Composable () -> Unit,
) {
    // We enforce our Clash theme to ensure high visual fidelity and avoid generic system styling
    MaterialTheme(
        colorScheme = ClashColorScheme,
        typography = Typography,
        content = content
    )
}
