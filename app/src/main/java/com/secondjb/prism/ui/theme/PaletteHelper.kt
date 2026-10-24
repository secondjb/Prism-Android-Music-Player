package com.secondjb.prism.ui.theme

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Encapsulates vibrant and dominant swatches extracted from music album art.
 */
data class PrismAlbumPalette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val dominant: Color,
    val surface: Color,
    val onPrimary: Color,
    val onSurface: Color
)

/**
 * Extracts key swatches from a Bitmap using AndroidX Palette.
 */
fun extractPaletteFromBitmap(bitmap: Bitmap): PrismAlbumPalette {
    val palette = Palette.from(bitmap)
        .maximumColorCount(24)
        .clearFilters() // include wide spectrum of colors
        .generate()

    val vibrant = palette.vibrantSwatch?.rgb?.let { Color(it) }
    val darkVibrant = palette.darkVibrantSwatch?.rgb?.let { Color(it) }
    val lightVibrant = palette.lightVibrantSwatch?.rgb?.let { Color(it) }
    val muted = palette.mutedSwatch?.rgb?.let { Color(it) }
    val dominant = palette.dominantSwatch?.rgb?.let { Color(it) } ?: Color(0xFF6750A4)

    val primaryColor = vibrant ?: lightVibrant ?: dominant
    val secondaryColor = darkVibrant ?: muted ?: dominant.adjustLuminance(0.7f)
    val tertiaryColor = lightVibrant ?: muted ?: primaryColor.adjustLuminance(1.2f)

    return PrismAlbumPalette(
        primary = primaryColor,
        secondary = secondaryColor,
        tertiary = tertiaryColor,
        dominant = dominant,
        surface = dominant.adjustLuminance(0.12f),
        onPrimary = primaryColor.contrastContentColor(),
        onSurface = Color.White
    )
}

/**
 * Builds a harmonious Material 3 ColorScheme from album art swatches.
 */
fun buildDynamicColorScheme(palette: PrismAlbumPalette, isDark: Boolean): ColorScheme {
    return if (isDark) {
        val primary = palette.primary.adjustLuminance(1.1f)
        val surface = palette.dominant.adjustLuminance(0.14f)
        val surfaceVariant = palette.dominant.adjustLuminance(0.22f)
        darkColorScheme(
            primary = primary,
            onPrimary = primary.contrastContentColor(),
            primaryContainer = palette.primary.adjustLuminance(0.35f),
            onPrimaryContainer = Color.White,
            secondary = palette.secondary.adjustLuminance(1.15f),
            onSecondary = palette.secondary.contrastContentColor(),
            secondaryContainer = palette.secondary.adjustLuminance(0.30f),
            onSecondaryContainer = Color.White,
            tertiary = palette.tertiary,
            onTertiary = palette.tertiary.contrastContentColor(),
            background = palette.dominant.adjustLuminance(0.08f),
            onBackground = Color(0xFFEDEAEF),
            surface = surface,
            onSurface = Color(0xFFEDEAEF),
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = Color(0xFFCAC4D0),
            surfaceContainer = surfaceVariant
        )
    } else {
        val primary = palette.primary.adjustLuminance(0.85f)
        val surface = palette.dominant.adjustLuminance(0.96f)
        val surfaceVariant = palette.dominant.adjustLuminance(0.90f)
        lightColorScheme(
            primary = primary,
            onPrimary = primary.contrastContentColor(),
            primaryContainer = palette.primary.adjustLuminance(0.90f),
            onPrimaryContainer = palette.primary.adjustLuminance(0.2f),
            secondary = palette.secondary.adjustLuminance(0.8f),
            onSecondary = palette.secondary.contrastContentColor(),
            secondaryContainer = palette.secondary.adjustLuminance(0.92f),
            onSecondaryContainer = palette.secondary.adjustLuminance(0.2f),
            tertiary = palette.tertiary,
            onTertiary = palette.tertiary.contrastContentColor(),
            background = Color(0xFFFAF8FD),
            onBackground = Color(0xFF1D1B20),
            surface = surface,
            onSurface = Color(0xFF1D1B20),
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = Color(0xFF49454F),
            surfaceContainer = surfaceVariant
        )
    }
}

/**
 * Smoothly animates every token in a ColorScheme with a 350ms tween.
 */
@Composable
fun animateColorScheme(target: ColorScheme): ColorScheme {
    val duration = 350
    val primary by animateColorAsState(target.primary, tween(duration), label = "primary")
    val onPrimary by animateColorAsState(target.onPrimary, tween(duration), label = "onPrimary")
    val primaryContainer by animateColorAsState(target.primaryContainer, tween(duration), label = "primaryContainer")
    val onPrimaryContainer by animateColorAsState(target.onPrimaryContainer, tween(duration), label = "onPrimaryContainer")
    val secondary by animateColorAsState(target.secondary, tween(duration), label = "secondary")
    val onSecondary by animateColorAsState(target.onSecondary, tween(duration), label = "onSecondary")
    val secondaryContainer by animateColorAsState(target.secondaryContainer, tween(duration), label = "secondaryContainer")
    val onSecondaryContainer by animateColorAsState(target.onSecondaryContainer, tween(duration), label = "onSecondaryContainer")
    val tertiary by animateColorAsState(target.tertiary, tween(duration), label = "tertiary")
    val onTertiary by animateColorAsState(target.onTertiary, tween(duration), label = "onTertiary")
    val background by animateColorAsState(target.background, tween(duration), label = "background")
    val onBackground by animateColorAsState(target.onBackground, tween(duration), label = "onBackground")
    val surface by animateColorAsState(target.surface, tween(duration), label = "surface")
    val onSurface by animateColorAsState(target.onSurface, tween(duration), label = "onSurface")
    val surfaceVariant by animateColorAsState(target.surfaceVariant, tween(duration), label = "surfaceVariant")
    val onSurfaceVariant by animateColorAsState(target.onSurfaceVariant, tween(duration), label = "onSurfaceVariant")

    return target.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant
    )
}

/**
 * Composable hook to asynchronously load artwork and extract its palette via Coil and AndroidX Palette.
 */
@Composable
fun rememberAlbumPalette(artworkData: Any?): PrismAlbumPalette? {
    val context = LocalContext.current
    var palette by remember(artworkData) { mutableStateOf<PrismAlbumPalette?>(null) }

    LaunchedEffect(artworkData) {
        if (artworkData == null) {
            palette = null
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            try {
                val imageLoader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(artworkData)
                    .allowHardware(false) // required to access pixel buffer for Palette
                    .size(128) // downscale for fast palette generation
                    .build()

                val result = imageLoader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = result.drawable.toBitmap(128, 128)
                    val extracted = extractPaletteFromBitmap(bitmap)
                    withContext(Dispatchers.Main) {
                        palette = extracted
                    }
                }
            } catch (e: Exception) {
                // Keep previous or fallback
            }
        }
    }

    return palette
}
