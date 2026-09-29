package com.lockin.focus.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.lockin.focus.core.model.SeedPreset
import com.lockin.focus.core.model.ThemeMode

/**
 * One theme, four ways to pick its colour:
 *
 * 1. Android 12+ wallpaper colours, the way Pixel does it;
 * 2. a colour pulled out of a photo the user chose, so the app looks like
 *    something they picked rather than something they were handed;
 * 3. one of four built-in seeds;
 * 4. a flat seed colour.
 *
 * Light and dark are always generated together from the same seed, so switching
 * system theme never changes what the app *is*, only how bright it is.
 */
@Composable
fun LockInTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    seed: SeedPreset = SeedPreset.LOCKDOWN,
    customSeed: Int? = null,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current

    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme: ColorScheme = when {
        useDynamic && dark -> androidx.compose.material3.dynamicDarkColorScheme(context)
        useDynamic -> androidx.compose.material3.dynamicLightColorScheme(context)
        dark -> TonalPalette.darkScheme(seed, customSeed)
        else -> TonalPalette.lightScheme(seed, customSeed)
    }

    CompositionLocalProvider(LocalLockInColors provides LockInColors(scheme = scheme, dark = dark)) {
        MaterialTheme(
            colorScheme = scheme,
            typography = LockInTypography,
            shapes = LockInShapes,
            content = content,
        )
    }
}

/** Colours the app uses that are not part of the M3 role set. */
data class LockInColors(
    val scheme: ColorScheme,
    val dark: Boolean,
) {
    val urgency: androidx.compose.ui.graphics.Color get() = scheme.error
    val onUrgency: androidx.compose.ui.graphics.Color get() = scheme.onError
    val urgencyContainer: androidx.compose.ui.graphics.Color get() = scheme.errorContainer
    val onUrgencyContainer: androidx.compose.ui.graphics.Color get() = scheme.onErrorContainer
    val success: androidx.compose.ui.graphics.Color get() = scheme.tertiary
    val onSuccess: androidx.compose.ui.graphics.Color get() = scheme.onTertiary
    val successContainer: androidx.compose.ui.graphics.Color get() = scheme.tertiaryContainer
    val onSuccessContainer: androidx.compose.ui.graphics.Color get() = scheme.onTertiaryContainer
}

val LocalLockInColors = staticCompositionLocalOf {
    LockInColors(scheme = TonalPalette.lightScheme(SeedPreset.LOCKDOWN, null), dark = false)
}

object LockInTheme {
    val colors: LockInColors
        @Composable get() = LocalLockInColors.current
}
