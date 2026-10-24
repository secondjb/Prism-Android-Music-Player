package com.secondjb.prism.theme

import androidx.compose.ui.graphics.Color
import com.secondjb.prism.ui.theme.DefaultPrismGradientStops
import com.secondjb.prism.ui.theme.PrismAlbumPalette
import com.secondjb.prism.ui.theme.buildDynamicColorScheme
import com.secondjb.prism.ui.theme.computePrismGradientStops
import com.secondjb.prism.ui.theme.contrastContentColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PaletteThemingTest {

    @Test
    fun testPrismGradientStopsGeneration() {
        val testPalette = PrismAlbumPalette(
            primary = Color(0xFF7E57C2),
            secondary = Color(0xFF26A69A),
            tertiary = Color(0xFFFFB74D),
            dominant = Color(0xFF311B92),
            surface = Color(0xFF121212),
            onPrimary = Color.White,
            onSurface = Color.White
        )

        assertEquals(6, testPalette.gradientStops.size)
        assertEquals(6, DefaultPrismGradientStops.size)

        val computed = computePrismGradientStops(Color(0xFF6366F1))
        assertEquals(6, computed.size)
    }

    @Test
    fun testContrastContentColor() {
        val darkBackground = Color(0xFF101010)
        val lightBackground = Color(0xFFF0F0F0)

        assertEquals(Color(0xFFFFFFFF), darkBackground.contrastContentColor())
        assertEquals(Color(0xFF121212), lightBackground.contrastContentColor())
    }

    @Test
    fun testBuildDynamicColorSchemeDarkAndLight() {
        val testPalette = PrismAlbumPalette(
            primary = Color(0xFF7E57C2),
            secondary = Color(0xFF26A69A),
            tertiary = Color(0xFFFFB74D),
            dominant = Color(0xFF311B92),
            surface = Color(0xFF121212),
            onPrimary = Color.White,
            onSurface = Color.White
        )

        val darkScheme = buildDynamicColorScheme(testPalette, isDark = true)
        assertNotNull(darkScheme)
        assertNotNull(darkScheme.primary)
        assertNotNull(darkScheme.surface)

        val lightScheme = buildDynamicColorScheme(testPalette, isDark = false)
        assertNotNull(lightScheme)
        assertNotNull(lightScheme.primary)
        assertNotNull(lightScheme.surface)
    }
}
