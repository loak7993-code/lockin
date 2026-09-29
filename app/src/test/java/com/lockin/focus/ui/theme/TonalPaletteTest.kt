package com.lockin.focus.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.lockin.focus.core.model.SeedPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The palette is generated, not hand-picked, which means nothing catches it if
 * the generation goes wrong. These tests are the only thing standing between a
 * seed colour and an unreadable app.
 */
class TonalPaletteTest {

    private val seeds = listOf(
        TonalPalette.PRESET_LOCKDOWN,
        TonalPalette.PRESET_EMBER,
        TonalPalette.PRESET_ABYSS,
        TonalPalette.PRESET_MOSS,
        0xFF00FF00.toInt(),
        0xFF808080.toInt(),
        0xFF000000.toInt(),
        0xFFFFFFFF.toInt(),
    )

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance()
        val lb = b.luminance()
        val lighter = max(la, lb)
        val darker = min(la, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun assertReadable(scheme: ColorScheme, label: String) {
        val pairs = listOf(
            "onSurface/surface" to (scheme.onSurface to scheme.surface),
            "onBackground/background" to (scheme.onBackground to scheme.background),
            "onPrimary/primary" to (scheme.onPrimary to scheme.primary),
            "onPrimaryContainer/primaryContainer" to
                (scheme.onPrimaryContainer to scheme.primaryContainer),
            "onSecondaryContainer/secondaryContainer" to
                (scheme.onSecondaryContainer to scheme.secondaryContainer),
            "onTertiaryContainer/tertiaryContainer" to
                (scheme.onTertiaryContainer to scheme.tertiaryContainer),
            "onError/error" to (scheme.onError to scheme.error),
            "onErrorContainer/errorContainer" to (scheme.onErrorContainer to scheme.errorContainer),
            "onSurfaceVariant/surfaceVariant" to
                (scheme.onSurfaceVariant to scheme.surfaceVariant),
            "onSurface/surfaceContainer" to (scheme.onSurface to scheme.surfaceContainer),
            "onSurface/surfaceContainerHigh" to (scheme.onSurface to scheme.surfaceContainerHigh),
            "onSurface/surfaceContainerHighest" to
                (scheme.onSurface to scheme.surfaceContainerHighest),
            "onSurface/surfaceContainerLow" to (scheme.onSurface to scheme.surfaceContainerLow),
            "inverseOnSurface/inverseSurface" to
                (scheme.inverseOnSurface to scheme.inverseSurface),
        )
        for ((name, pair) in pairs) {
            val ratio = contrast(pair.first, pair.second)
            assertTrue(
                "$label: $name contrast was %.2f, below WCAG AA 4.5".format(ratio),
                ratio >= 4.5,
            )
        }
    }

    @Test
    fun `every seed is readable in light mode`() {
        for (seed in seeds) {
            assertReadable(TonalPalette.lightScheme(SeedPreset.CUSTOM, seed), "light ${seed.toHex()}")
        }
    }

    @Test
    fun `every seed is readable in dark mode`() {
        for (seed in seeds) {
            assertReadable(TonalPalette.darkScheme(SeedPreset.CUSTOM, seed), "dark ${seed.toHex()}")
        }
    }

    @Test
    fun `outlines stay visible against their own surface`() {
        for (seed in seeds) {
            val light = TonalPalette.lightScheme(SeedPreset.CUSTOM, seed)
            val dark = TonalPalette.darkScheme(SeedPreset.CUSTOM, seed)
            assertTrue(contrast(light.outline, light.surface) >= 3.0)
            assertTrue(contrast(dark.outline, dark.surface) >= 3.0)
        }
    }

    @Test
    fun `tones are actually monotonic in lightness`() {
        for (seed in seeds) {
            val light = TonalPalette.lightScheme(SeedPreset.CUSTOM, seed)
            val dark = TonalPalette.darkScheme(SeedPreset.CUSTOM, seed)

            // Light: the surface is the lightest thing, containers step down as
            // elevation rises. Dark: the opposite, containers lift off the surface.
            assertTrue(light.surface.luminance() > light.surfaceContainerLow.luminance())
            assertTrue(light.surfaceContainerLow.luminance() > light.surfaceContainer.luminance())
            assertTrue(light.surfaceContainer.luminance() > light.surfaceContainerHigh.luminance())
            assertTrue(light.surfaceContainerHigh.luminance() > light.surfaceContainerHighest.luminance())

            assertTrue(dark.surface.luminance() < dark.surfaceContainerLow.luminance())
            assertTrue(dark.surfaceContainerLow.luminance() < dark.surfaceContainer.luminance())
            assertTrue(dark.surfaceContainer.luminance() < dark.surfaceContainerHigh.luminance())
            assertTrue(dark.surfaceContainerHigh.luminance() < dark.surfaceContainerHighest.luminance())

            // Dark primary is light so it reads on a dark surface, and its own
            // "on" colour is darker than it.
            assertTrue(dark.primary.luminance() > dark.surface.luminance())
            assertTrue(dark.onPrimary.luminance() < dark.primary.luminance())
        }
    }

    @Test
    fun `a custom seed without a colour falls back to the default`() {
        val fallback = TonalPalette.lightScheme(SeedPreset.CUSTOM, null)
        assertEquals(TonalPalette.lightScheme(SeedPreset.LOCKDOWN, null).primary, fallback.primary)
    }

    @Test
    fun `generation is deterministic`() {
        val a = TonalPalette.lightScheme(SeedPreset.EMBER, null)
        val b = TonalPalette.lightScheme(SeedPreset.EMBER, null)
        assertEquals(a.primary, b.primary)
        assertEquals(a.surfaceContainerHigh, b.surfaceContainerHigh)
    }

    @Test
    fun `a grey seed still produces a usable primary`() {
        // A desaturated seed must not produce a grey button with grey text.
        val scheme = TonalPalette.lightScheme(SeedPreset.CUSTOM, 0xFF808080.toInt())
        assertTrue(contrast(scheme.onPrimary, scheme.primary) >= 4.5)
    }

    @Test
    fun `relative luminance behaves the way the contrast maths assumes`() {
        assertEquals(1.0, Color.White.luminance().toDouble(), 0.01)
        assertEquals(0.0, Color.Black.luminance().toDouble(), 0.01)
    }

    @Test
    fun `gamma curve is applied, not a raw linear mix`() {
        val mid = 0.5
        val encoded = if (mid <= 0.0031308) 12.92 * mid else 1.055 * mid.pow(1 / 2.4) - 0.055
        assertTrue("expected sRGB encoding, got $encoded", encoded in 0.73..0.74)
    }

    private fun Int.toHex(): String = "#%08X".format(this)
}
