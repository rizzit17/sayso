package com.samsung.prism.teachable.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val SaysoLightColorScheme = lightColorScheme(
    primary = SaysoPrimary,
    onPrimary = SaysoOnPrimary,
    primaryContainer = SaysoPrimaryContainer,
    onPrimaryContainer = SaysoOnPrimaryContainer,
    secondary = SaysoSecondary,
    onSecondary = SaysoOnSecondary,
    secondaryContainer = SaysoSecondaryContainer,
    onSecondaryContainer = SaysoOnSecondaryContainer,
    tertiary = SaysoTertiary,
    onTertiary = SaysoOnTertiary,
    tertiaryContainer = SaysoTertiaryContainer,
    onTertiaryContainer = SaysoOnTertiaryContainer,
    background = SaysoSurface,
    onBackground = SaysoOnSurface,
    surface = SaysoSurface,
    onSurface = SaysoOnSurface,
    surfaceVariant = SaysoSurfaceContainerHigh,
    onSurfaceVariant = SaysoOnSurfaceVariant,
    outline = SaysoOutline,
    outlineVariant = SaysoOutlineVariant,
    error = SaysoError,
    onError = SaysoOnError,
    errorContainer = SaysoErrorContainer,
    onErrorContainer = SaysoOnErrorContainer
)

private val SaysoDarkColorScheme = darkColorScheme(
    primary = SaysoPrimaryFixedDim,
    onPrimary = SaysoOnPrimaryFixed,
    primaryContainer = SaysoPrimaryContainer,
    onPrimaryContainer = SaysoPrimaryFixed,
    secondary = SaysoSecondaryFixedDim,
    onSecondary = SaysoOnSecondaryFixed,
    secondaryContainer = SaysoOnSecondaryFixedVariant,
    onSecondaryContainer = SaysoSecondaryFixed,
    tertiary = SaysoTertiaryFixedDim,
    onTertiary = SaysoOnTertiaryFixed,
    tertiaryContainer = SaysoOnTertiaryFixedVariant,
    onTertiaryContainer = SaysoTertiaryFixed,
    background = SaysoInverseSurface,
    onBackground = SaysoInverseOnSurface,
    surface = SaysoInverseSurface,
    onSurface = SaysoInverseOnSurface,
    surfaceVariant = SaysoSurfaceContainerHigh,
    onSurfaceVariant = SaysoOnSurfaceVariant,
    outline = SaysoOutline,
    outlineVariant = SaysoOutlineVariant,
    error = SaysoErrorContainer,
    onError = SaysoOnErrorContainer
)

@Composable
fun PrismTheme(
    darkTheme: Boolean = false, // Sayso Google Stitch default is clean M3 Light
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) SaysoDarkColorScheme else SaysoLightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = SaysoShapes,
        content = content
    )
}
