package com.baoji.fitness.core.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = AppleFitnessGreen,
    onPrimary = AppleBlack,
    secondary = AppleSystemBlue,
    onSecondary = AppleTextPrimary,
    tertiary = AppleCautionOrange,
    background = AppleBlack,
    onBackground = AppleTextPrimary,
    surface = AppleGray6,
    onSurface = AppleTextPrimary,
    surfaceVariant = AppleGray5,
    onSurfaceVariant = AppleTextSecondary,
    outline = AppleHairline
)

@Composable
fun BaojiTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = AppleBlack.toArgb()
            window.navigationBarColor = AppleBlack.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
