package com.secondjb.prism.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
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

    val defaultAccent = Color(0xFF8B5CF6) // Fallback vibrant purple
    val candidatePrimary = vibrant ?: lightVibrant ?: dominant
    val primaryColor = if (candidatePrimary.toHsl()[2] < 0.22f) {
        lightVibrant ?: defaultAccent
    } else {
        candidatePrimary
    }
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
 * Builds a harmonious Material 3 ColorScheme from album art swatches, modeled after PixelPlayer and LastWave.
 * Keeps surface/background anchored to legible Material 3 tokens while using the album palette strictly
 * for vibrant accents (primary, primaryContainer, secondary, tertiary).
 */
fun buildDynamicColorScheme(palette: PrismAlbumPalette, isDark: Boolean): ColorScheme {
    return if (isDark) {
        // Guarantee accessible primary color with high contrast on dark surface (Tone ~80)
        val primary = palette.primary.adjustLuminance(1.15f).clampLightness(minL = 0.68f, maxL = 0.88f)
        val primaryContainer = palette.primary.adjustLuminance(0.28f).clampLightness(minL = 0.18f, maxL = 0.32f)
        val secondary = palette.secondary.adjustLuminance(1.1f).clampLightness(minL = 0.60f, maxL = 0.85f)
        val secondaryContainer = palette.secondary.adjustLuminance(0.24f).clampLightness(minL = 0.15f, maxL = 0.30f)

        // Maintain rock-solid dark surface hierarchy (never black-out)
        val surfaceBase = Color(0xFF141519)
        val surfaceContainer = Color(0xFF1E2026)
        val surfaceVariant = Color(0xFF282A32)

        darkColorScheme(
            primary = primary,
            onPrimary = primary.contrastContentColor(),
            primaryContainer = primaryContainer,
            onPrimaryContainer = Color(0xFFEDEAEF),
            secondary = secondary,
            onSecondary = secondary.contrastContentColor(),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = Color(0xFFEDEAEF),
            tertiary = palette.tertiary,
            onTertiary = palette.tertiary.contrastContentColor(),
            background = surfaceBase,
            onBackground = Color(0xFFEDEAEF),
            surface = surfaceContainer,
            onSurface = Color(0xFFEDEAEF),
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = Color(0xFFC7C5D0),
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = Color(0xFF2A2C34),
            surfaceContainerHighest = Color(0xFF33353E)
        )
    } else {
        val primary = palette.primary.adjustLuminance(0.85f)
        val primaryContainer = palette.primary.adjustLuminance(0.92f)
        val secondary = palette.secondary.adjustLuminance(0.80f)

        lightColorScheme(
            primary = primary,
            onPrimary = primary.contrastContentColor(),
            primaryContainer = primaryContainer,
            onPrimaryContainer = palette.primary.adjustLuminance(0.2f),
            secondary = secondary,
            onSecondary = secondary.contrastContentColor(),
            secondaryContainer = palette.secondary.adjustLuminance(0.92f),
            onSecondaryContainer = palette.secondary.adjustLuminance(0.2f),
            tertiary = palette.tertiary,
            onTertiary = palette.tertiary.contrastContentColor(),
            background = Color(0xFFF9F9FC),
            onBackground = Color(0xFF1B1B1F),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF1B1B1F),
            surfaceVariant = Color(0xFFE2E1EC),
            onSurfaceVariant = Color(0xFF45464F),
            surfaceContainer = Color(0xFFF0EFF6),
            surfaceContainerHigh = Color(0xFFEAE9F0),
            surfaceContainerHighest = Color(0xFFE4E3EA)
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

private val paletteCache = LruCache<String, PrismAlbumPalette>(64)

/**
 * Composable hook to asynchronously load artwork and extract its palette via Coil and AndroidX Palette.
 * Retains the active palette during track changes to prevent flashing back to the default theme colors.
 */
@Composable
fun rememberAlbumPalette(artworkData: Any?): PrismAlbumPalette? {
    val context = LocalContext.current
    val cacheKey = artworkData?.toString()

    // Initialize with cached palette if available, or keep current state across track skips
    val initialCached = cacheKey?.let { paletteCache.get(it) }
    var palette by remember { mutableStateOf<PrismAlbumPalette?>(initialCached) }

    LaunchedEffect(artworkData) {
        if (artworkData == null) {
            palette = null
            return@LaunchedEffect
        }

        val key = artworkData.toString()
        val cached = paletteCache.get(key)
        if (cached != null) {
            palette = cached
            return@LaunchedEffect
        }

        // NOTE: We intentionally retain the previous palette while decoding/extracting
        // the new artwork on Dispatchers.IO. This eliminates the split-second flash back to default colors.
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
                    paletteCache.put(key, extracted)
                    withContext(Dispatchers.Main) {
                        palette = extracted
                    }
                }
            } catch (e: Exception) {
                // Keep previous palette on error
            }
        }
    }

    return palette
}
