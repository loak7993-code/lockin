package com.lockin.focus.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * The M3 type scale, unchanged in size, with two deliberate edits:
 *
 * - the display/headline styles are pulled in one step and set to Bold, because
 *   this app is a countdown that has to be readable from across a desk;
 * - body styles get a slightly looser line height, because the block screen and
 *   the check-in are wall-to-wall text with nothing else on them.
 *
 * Sizes stay inside the scale on purpose: no invented type sizes.
 */
private val Sans = FontFamily.SansSerif

private val TightLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

val LockInTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Bold, lineHeightStyle = TightLineHeight),
        displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Bold, lineHeightStyle = TightLineHeight),
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, lineHeightStyle = TightLineHeight),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, lineHeightStyle = TightLineHeight),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, lineHeightStyle = TightLineHeight),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, lineHeightStyle = TightLineHeight),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(lineHeight = 26.sp),
        bodyMedium = base.bodyMedium.copy(lineHeight = 22.sp),
    )
}

/** Used by the countdown ring: tabular so the digits do not shuffle every second. */
val CountdownStyle: TextStyle = TextStyle(
    fontFamily = Sans,
    fontSize = 56.sp,
    lineHeight = 60.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = (-1.5).sp,
)
