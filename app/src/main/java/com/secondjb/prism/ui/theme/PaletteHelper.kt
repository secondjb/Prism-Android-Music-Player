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
 * Default smooth, rich Prism color stops (Indigo -> Purple -> Pink -> Fuchsia -> Blue -> Violet)
 * identical to Desktop Windows theme stops.
 */
val DefaultPrismGradientStops: List<Color> = listOf(
    Color(0xFF6366F1), // Indigo
    Color(0xFF8B5CF6), // Purple
    Color(0xFFEC4899), // Pink
    Color(0xFFD946EF), // Fuchsia
    Color(0xFF3B82F6), // Blue
    Color(0xFF818CF8)  // Light Indigo
)

/**
 * Calculates 6 harmonic, vivid color stops centered around the active album art palette,
 * mirroring the Windows desktop colorExtractor implementation.
 */
fun computePrismGradientStops(
    primaryColor: Color,
    secondaryColor: Color? = null,
    tertiaryColor: Color? = null
): List<Color> {
    val (h, s, l) = primaryColor.toHsl()
    val normSat = (s * 1.15f).coerceIn(0.55f, 0.88f)
    val normLight = l.coerceIn(0.46f, 0.62f)

    val stop1 = hslToColor(h, normSat, normLight)
    val stop2 = if (secondaryColor != null) {
        val (secH, secS, secL) = secondaryColor.toHsl()
        hslToColor(secH, (secS * 1.1f).coerceIn(0.50f, 0.85f), secL.coerceIn(0.45f, 0.60f))
    } else {
        hslToColor((h + 25f) % 360f, normSat * 0.95f, normLight)
    }
    val stop3 = if (tertiaryColor != null) {
        val (tertH, tertS, tertL) = tertiaryColor.toHsl()
        hslToColor(tertH, (tertS * 1.1f).coerceIn(0.50f, 0.85f), tertL.coerceIn(0.45f, 0.60f))
    } else {
        hslToColor((h - 30f + 360f) % 360f, normSat * 0.90f, (normLight * 1.05f).coerceIn(0f, 1f))
    }
    val stop4 = hslToColor((h + 50f) % 360f, normSat * 0.85f, (normLight * 1.08f).coerceIn(0f, 0.65f))
    val stop5 = hslToColor((h - 45f + 360f) % 360f, normSat * 0.90f, (normLight * 0.92f).coerceAtLeast(0.42f))
    val stop6 = hslToColor((h + 15f) % 360f, (normSat * 1.05f).coerceIn(0f, 0.90f), (normLight * 1.12f).coerceIn(0f, 0.68f))

    return listOf(stop1, stop2, stop3, stop4, stop5, stop6)
}

/**
 * Encapsulates vibrant and dominant swatches extracted from music album art,
 * including dynamic gradient stops for brand headers and logo refraction waves.
 */
data class PrismAlbumPalette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val dominant: Color,
    val surface: Color,
    val onPrimary: Color,
    val onSurface: Color,
    val gradientStops: List<Color> = computePrismGradientStops(primary, secondary, tertiary)
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

    val (h, s, l) = primaryColor.toHsl()

    // Harmonious tonal palette: derive secondary and tertiary from the primary seed hue
    // Secondary: same hue family (subtle 8° shift), slightly calmer saturation and tone
    val secondaryColor = hslToColor(
        h = (h + 8f) % 360f,
        s = (s * 0.70f).coerceIn(0.25f, 0.65f),
        l = (l * 0.90f).coerceIn(0.35f, 0.60f)
    )

    // Tertiary: analogous tonal shift (22° shift), harmonious accent
    val tertiaryColor = hslToColor(
        h = (h + 22f) % 360f,
        s = (s * 0.85f).coerceIn(0.30f, 0.75f),
        l = (l * 1.05f).coerceIn(0.40f, 0.70f)
    )

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

/**
 * Smoothly animates the 6 harmonic Prism color stops with a 350ms tween
 * to seamlessly transition brand text and logo refraction when album art changes.
 */
@Composable
fun rememberAnimatedPrismGradient(
    palette: PrismAlbumPalette? = LocalPrismPalette.current
): List<Color> {
    val targetStops = palette?.gradientStops?.takeIf { it.size == 6 } ?: DefaultPrismGradientStops
    val duration = 350
    val c0 by animateColorAsState(targetStops[0], tween(duration), label = "grad0")
    val c1 by animateColorAsState(targetStops[1], tween(duration), label = "grad1")
    val c2 by animateColorAsState(targetStops[2], tween(duration), label = "grad2")
    val c3 by animateColorAsState(targetStops[3], tween(duration), label = "grad3")
    val c4 by animateColorAsState(targetStops[4], tween(duration), label = "grad4")
    val c5 by animateColorAsState(targetStops[5], tween(duration), label = "grad5")

    return remember(c0, c1, c2, c3, c4, c5) {
        listOf(c0, c1, c2, c3, c4, c5)
    }
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
            // NOTE: Retain previous album palette if available to prevent flashing default colors during track or device transitions
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
