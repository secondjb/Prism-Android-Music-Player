package com.secondjb.prism.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

// Default Prism Expressive Light Palette
val PrismPrimaryLight = Color(0xFF6750A4)
val PrismOnPrimaryLight = Color(0xFFFFFFFF)
val PrismPrimaryContainerLight = Color(0xFFEADDFF)
val PrismOnPrimaryContainerLight = Color(0xFF21005D)
val PrismSecondaryLight = Color(0xFF625B71)
val PrismOnSecondaryLight = Color(0xFFFFFFFF)
val PrismSecondaryContainerLight = Color(0xFFE8DEF8)
val PrismTertiaryLight = Color(0xFF7D5260)
val PrismBackgroundLight = Color(0xFFFEF7FF)
val PrismSurfaceLight = Color(0xFFFEF7FF)
val PrismSurfaceVariantLight = Color(0xFFE7E0EC)
val PrismOnSurfaceLight = Color(0xFF1D1B20)
val PrismOnSurfaceVariantLight = Color(0xFF49454F)

// Default Prism Expressive Dark Palette
val PrismPrimaryDark = Color(0xFFD0BCFF)
val PrismOnPrimaryDark = Color(0xFF381E72)
val PrismPrimaryContainerDark = Color(0xFF4F378B)
val PrismOnPrimaryContainerDark = Color(0xFFEADDFF)
val PrismSecondaryDark = Color(0xFFCCC2DC)
val PrismOnSecondaryDark = Color(0xFF332D41)
val PrismSecondaryContainerDark = Color(0xFF4A4458)
val PrismTertiaryDark = Color(0xFFEFB8C8)
val PrismBackgroundDark = Color(0xFF141218)
val PrismSurfaceDark = Color(0xFF141218)
val PrismSurfaceVariantDark = Color(0xFF49454F)
val PrismOnSurfaceDark = Color(0xFFE6E0E9)
val PrismOnSurfaceVariantDark = Color(0xFFCAC4D0)

/**
 * Pure Kotlin RGB to HSL conversion, independent of Android platform stubs.
 */
fun Color.toHsl(): FloatArray {
    val r = red
    val g = green
    val b = blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val l = (max + min) / 2f
    val h: Float
    val s: Float

    if (delta == 0f) {
        h = 0f
        s = 0f
    } else {
        s = if (l <= 0.5f) delta / (max + min) else delta / (2f - max - min)
        h = when (max) {
            r -> ((g - b) / delta + (if (g < b) 6f else 0f)) * 60f
            g -> ((b - r) / delta + 2f) * 60f
            else -> ((r - g) / delta + 4f) * 60f
        }
    }
    return floatArrayOf(h, s, l)
}

/**
 * Pure Kotlin HSL to Color conversion.
 */
fun hslToColor(h: Float, s: Float, l: Float, alpha: Float = 1.0f): Color {
    val normalizedH = ((h % 360f) + 360f) % 360f
    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs((normalizedH / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (r1, g1, b1) = when ((normalizedH / 60f).toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        red = (r1 + m).coerceIn(0f, 1f),
        green = (g1 + m).coerceIn(0f, 1f),
        blue = (b1 + m).coerceIn(0f, 1f),
        alpha = alpha
    )
}

/**
 * Shifts the lightness of a Color while maintaining its perceptual hue.
 */
fun Color.adjustLuminance(factor: Float): Color {
    val hsl = toHsl()
    val newL = (hsl[2] * factor).coerceIn(0f, 1f)
    return hslToColor(hsl[0], hsl[1], newL, alpha)
}

/**
 * Ensures the lightness of this color is at least [minL] and at most [maxL],
 * guaranteeing high contrast against dark surfaces while preserving vibrant color.
 */
fun Color.clampLightness(minL: Float = 0.65f, maxL: Float = 0.90f): Color {
    val hsl = toHsl()
    val newL = hsl[2].coerceIn(minL, maxL)
    val newS = if (hsl[1] < 0.25f) 0.50f else hsl[1]
    return hslToColor(hsl[0], newS, newL, alpha)
}

/**
 * Calculates a high-contrast text/icon color (pure black or white) for any background color.
 */
fun Color.contrastContentColor(): Color {
    val luminance = (0.299 * red + 0.587 * green + 0.114 * blue)
    return if (luminance > 0.5) Color(0xFF121212) else Color(0xFFFFFFFF)
}
