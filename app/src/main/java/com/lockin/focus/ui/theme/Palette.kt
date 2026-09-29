package com.lockin.focus.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.lockin.focus.core.model.SeedPreset
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Material 3 tonal palettes generated from a single seed colour.
 *
 * Colours are built in OKLCH rather than hand-picked, because that is what makes
 * a palette a palette: tone *is* lightness, so every role at the same tone has
 * the same perceived lightness and the scheme stays legible in both light and
 * dark without anyone eyeballing forty hex values. Out-of-gamut requests are
 * resolved by walking chroma down instead of clipping channels, which would
 * otherwise rotate the hue of every saturated role.
 */
object TonalPalette {

    // Role chroma. Primary carries the seed's identity, tertiary is the
    // split-complement, secondary and neutrals stay quiet.
    private const val CHROMA_PRIMARY = 0.17
    private const val CHROMA_SECONDARY = 0.058
    private const val CHROMA_TERTIARY = 0.13
    private const val CHROMA_NEUTRAL = 0.006
    private const val CHROMA_NEUTRAL_VARIANT = 0.022
    private const val CHROMA_ERROR = 0.19

    private const val ERROR_HUE = 27.0

    const val PRESET_LOCKDOWN: Int = 0xFF7B4DFF.toInt()
    const val PRESET_EMBER: Int = 0xFFE0532B.toInt()
    const val PRESET_ABYSS: Int = 0xFF00A0B4.toInt()
    const val PRESET_MOSS: Int = 0xFF3F8A2F.toInt()

    fun seedOf(preset: SeedPreset, customSeedArgb: Int?): Int = when (preset) {
        SeedPreset.LOCKDOWN -> PRESET_LOCKDOWN
        SeedPreset.EMBER -> PRESET_EMBER
        SeedPreset.ABYSS -> PRESET_ABYSS
        SeedPreset.MOSS -> PRESET_MOSS
        SeedPreset.CUSTOM -> customSeedArgb ?: PRESET_LOCKDOWN
    }

    fun lightScheme(preset: SeedPreset, customSeedArgb: Int?): ColorScheme =
        build(seedOf(preset, customSeedArgb), light = true)

    fun darkScheme(preset: SeedPreset, customSeedArgb: Int?): ColorScheme =
        build(seedOf(preset, customSeedArgb), light = false)

    private fun build(seed: Int, light: Boolean): ColorScheme {
        val hsl = argbToHsl(seed)
        val hue = hsl.hue
        // A washed-out seed should not produce a washed-out app.
        val chromaScale = (0.45 + hsl.saturation * 0.55).coerceIn(0.45, 1.0)

        val primary = ramp(CHROMA_PRIMARY, hue, chromaScale)
        val secondary = ramp(CHROMA_SECONDARY, hue, chromaScale)
        val tertiary = ramp(CHROMA_TERTIARY, splitComplement(hue), chromaScale)
        val neutral = ramp(CHROMA_NEUTRAL, hue, chromaScale)
        val neutralVariant = ramp(CHROMA_NEUTRAL_VARIANT, hue, chromaScale)
        val error = ramp(CHROMA_ERROR, ERROR_HUE, 1.0)

        return if (light) {
            lightColorScheme(
                primary = primary(40), onPrimary = primary(100),
                primaryContainer = primary(90), onPrimaryContainer = primary(10),
                inversePrimary = primary(80),
                secondary = secondary(40), onSecondary = secondary(100),
                secondaryContainer = secondary(90), onSecondaryContainer = secondary(10),
                tertiary = tertiary(40), onTertiary = tertiary(100),
                tertiaryContainer = tertiary(90), onTertiaryContainer = tertiary(10),
                background = neutral(98), onBackground = neutral(10),
                surface = neutral(98), onSurface = neutral(10),
                surfaceVariant = neutralVariant(90), onSurfaceVariant = neutralVariant(30),
                surfaceTint = primary(40),
                inverseSurface = neutral(20), inverseOnSurface = neutral(95),
                error = error(40), onError = error(100),
                errorContainer = error(90), onErrorContainer = error(10),
                outline = neutralVariant(50), outlineVariant = neutralVariant(80),
                scrim = neutral(0),
                surfaceBright = neutral(98), surfaceDim = neutral(87),
                surfaceContainerLowest = neutral(100), surfaceContainerLow = neutral(96),
                surfaceContainer = neutral(94), surfaceContainerHigh = neutral(92),
                surfaceContainerHighest = neutral(90),
            )
        } else {
            darkColorScheme(
                primary = primary(80), onPrimary = primary(20),
                primaryContainer = primary(30), onPrimaryContainer = primary(90),
                inversePrimary = primary(40),
                secondary = secondary(80), onSecondary = secondary(20),
                secondaryContainer = secondary(30), onSecondaryContainer = secondary(90),
                tertiary = tertiary(80), onTertiary = tertiary(20),
                tertiaryContainer = tertiary(30), onTertiaryContainer = tertiary(90),
                background = neutral(6), onBackground = neutral(90),
                surface = neutral(6), onSurface = neutral(90),
                surfaceVariant = neutralVariant(30), onSurfaceVariant = neutralVariant(80),
                surfaceTint = primary(80),
                inverseSurface = neutral(90), inverseOnSurface = neutral(20),
                error = error(80), onError = error(20),
                errorContainer = error(30), onErrorContainer = error(90),
                outline = neutralVariant(60), outlineVariant = neutralVariant(30),
                scrim = neutral(0),
                surfaceBright = neutral(24), surfaceDim = neutral(6),
                surfaceContainerLowest = neutral(4), surfaceContainerLow = neutral(10),
                surfaceContainer = neutral(12), surfaceContainerHigh = neutral(17),
                surfaceContainerHighest = neutral(22),
            )
        }
    }

    /** Mapped 150 degrees off the seed: a second identity, not a repeat of the first. */
    private fun splitComplement(hue: Double): Double = ((hue + 150.0) % 360.0 + 360.0) % 360.0

    private data class Hsl(val hue: Double, val saturation: Double, val lightness: Double)

    /**
     * sRGB -> HSL for the seed, done here rather than pulled from Compose so the
     * palette generator stays a pure function of a number.
     */
    private fun argbToHsl(argb: Int): Hsl {
        val r = ((argb shr 16) and 0xFF) / 255.0
        val g = ((argb shr 8) and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        val lightness = (max + min) / 2.0
        if (delta < 1e-6) return Hsl(0.0, 0.0, lightness)
        val saturation = if (lightness > 0.5) delta / (2.0 - max - min) else delta / (max + min)
        val hue = when (max) {
            r -> 60.0 * (((g - b) / delta) % 6.0)
            g -> 60.0 * (((b - r) / delta) + 2.0)
            else -> 60.0 * (((r - g) / delta) + 4.0)
        }
        return Hsl(((hue + 360.0) % 360.0), saturation, lightness)
    }

    private fun ramp(baseChroma: Double, hue: Double, chromaScale: Double): (Int) -> Color {
        val chroma = baseChroma * chromaScale
        return { tone -> oklch(toneToLightness(tone), chroma, hue) }
    }

    /**
     * OKLab lightness for an M3 tone. OKLab L is already close to perceptual
     * lightness; this only pulls the two ends in so tone 0 is true black and
     * tone 100 is near-white.
     */
    private fun toneToLightness(tone: Int): Double {
        val t = tone.coerceIn(0, 100) / 100.0
        return (0.012 + 0.976 * t.pow(0.96)).coerceIn(0.0, 1.0)
    }

    /** OKLCH -> sRGB, reducing chroma until the colour fits the display gamut. */
    private fun oklch(lightness: Double, chroma: Double, hueDeg: Double): Color {
        var c = chroma
        repeat(24) {
            val rgb = oklchToLinear(lightness, c, hueDeg)
            if (rgb.all { it >= -0.0005 && it <= 1.0005 }) {
                return Color(encode(rgb[0]), encode(rgb[1]), encode(rgb[2]))
            }
            c *= 0.94
        }
        val rgb = oklchToLinear(lightness, 0.0, hueDeg)
        return Color(encode(rgb[0]), encode(rgb[1]), encode(rgb[2]))
    }

    private fun oklchToLinear(l: Double, c: Double, hueDeg: Double): DoubleArray {
        val h = Math.toRadians(hueDeg)
        val a = c * cos(h)
        val b = c * sin(h)
        val lPrime = l + 0.3963377774 * a + 0.2158037573 * b
        val mPrime = l - 0.1055613458 * a - 0.0638541728 * b
        val sPrime = l - 0.0894841775 * a - 1.2914855480 * b
        val l3 = lPrime * lPrime * lPrime
        val m3 = mPrime * mPrime * mPrime
        val s3 = sPrime * sPrime * sPrime
        return doubleArrayOf(
            4.0767416621 * l3 - 3.3077115913 * m3 + 0.2309699292 * s3,
            -1.2684380046 * l3 + 2.6097574011 * m3 - 0.3413193965 * s3,
            -0.0041960863 * l3 - 0.7034186147 * m3 + 1.7076147010 * s3,
        )
    }

    private fun encode(channel: Double): Int {
        val clamped = channel.coerceIn(0.0, 1.0)
        val srgb = if (clamped <= 0.0031308) 12.92 * clamped else 1.055 * clamped.pow(1 / 2.4) - 0.055
        return (srgb * 255.0).roundToInt().coerceIn(0, 255)
    }
}
