package com.secondjb.prism.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalPrismPalette = staticCompositionLocalOf<PrismAlbumPalette?> { null }

private val DefaultDarkColorScheme = darkColorScheme(
    primary = PrismPrimaryDark,
    onPrimary = PrismOnPrimaryDark,
    primaryContainer = PrismPrimaryContainerDark,
    onPrimaryContainer = PrismOnPrimaryContainerDark,
    secondary = PrismSecondaryDark,
    onSecondary = PrismOnSecondaryDark,
    secondaryContainer = PrismSecondaryContainerDark,
    tertiary = PrismTertiaryDark,
    background = PrismBackgroundDark,
    surface = PrismSurfaceDark,
    surfaceVariant = PrismSurfaceVariantDark,
    onBackground = PrismOnSurfaceDark,
    onSurface = PrismOnSurfaceDark,
    onSurfaceVariant = PrismOnSurfaceVariantDark
)

private val DefaultLightColorScheme = lightColorScheme(
    primary = PrismPrimaryLight,
    onPrimary = PrismOnPrimaryLight,
    primaryContainer = PrismPrimaryContainerLight,
    onPrimaryContainer = PrismOnPrimaryContainerLight,
    secondary = PrismSecondaryLight,
    onSecondary = PrismOnSecondaryLight,
    secondaryContainer = PrismSecondaryContainerLight,
    tertiary = PrismTertiaryLight,
    background = PrismBackgroundLight,
    surface = PrismSurfaceLight,
    surfaceVariant = PrismSurfaceVariantLight,
    onBackground = PrismOnSurfaceLight,
    onSurface = PrismOnSurfaceLight,
    onSurfaceVariant = PrismOnSurfaceVariantLight
)

/**
 * Core dynamic theme provider for Prism Android.
 * Dynamically extracts colors from the active track's album art using AndroidX Palette
 * and pipes them into the Material 3 ColorScheme with smooth animations.
 */
@Composable
fun PrismTheme(
    artworkData: Any? = null,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val albumPalette = rememberAlbumPalette(artworkData = artworkData)

    val targetColorScheme: ColorScheme = when {
        albumPalette != null -> {
            buildDynamicColorScheme(albumPalette, isDark = darkTheme)
        }
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DefaultDarkColorScheme
        else -> DefaultLightColorScheme
    }

    // Smoothly animate color scheme transitions when album art changes
    val animatedColorScheme = animateColorScheme(target = targetColorScheme)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalPrismPalette provides albumPalette
    ) {
        MaterialTheme(
            colorScheme = animatedColorScheme,
            shapes = PrismShapes,
            typography = Typography,
            content = content
        )
    }
}

/**
 * Reusable dynamic theme provider wrapper that can wrap any screen or component subtree.
 */
@Composable
fun PrismDynamicThemeProvider(
    artworkData: Any? = null,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    PrismTheme(
        artworkData = artworkData,
        darkTheme = darkTheme,
        dynamicColor = true,
        content = content
    )
}
