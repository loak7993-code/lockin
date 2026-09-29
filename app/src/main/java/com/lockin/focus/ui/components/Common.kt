package com.lockin.focus.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lockin.focus.ui.theme.CountdownStyle
import com.lockin.focus.ui.theme.Motion

/**
 * The countdown ring. It is the app's clock and its pressure gauge at the same
 * time: the last 10% of the arc is the warning, and it is the same colour the
 * check-in overlay will arrive in.
 */
@Composable
fun CountdownRing(
    progress: Float,
    timeText: String,
    caption: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 232.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    progressColor: Color = MaterialTheme.colorScheme.primary,
    warnColor: Color = MaterialTheme.colorScheme.error,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(Motion.DURATION_MEDIUM, easing = Motion.Emphasized),
        label = "ringProgress",
    )
    val warning = progress > 0.9f
    val arcColor = if (warning) warnColor else progressColor

    Box(
        modifier = modifier
            .size(diameter)
            .semantics {
                contentDescription = "$caption, $timeText remaining"
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(diameter)) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (animated > 0f) {
                drawArc(
                    color = arcColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = timeText, style = CountdownStyle, color = contentColor)
            Spacer(Modifier.height(2.dp))
            Text(
                text = caption.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Small status chip used for session state, outcomes and counts. */
@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    content: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    leading: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .background(container, MaterialTheme.shapes.extraLarge)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (leading != null) {
            Icon(leading, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = content)
    }
}

/** A number with a label under it. Four of these make a stat row. */
@Composable
fun StatTile(
    value: String,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A small section label. Deliberately does not fill its width: it is nearly
 * always the leading item in a Row next to an action, and filling the width
 * there squeezes that action to nothing and stacks its label one letter per line.
 */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        trailing?.invoke()
    }
}

/**
 * Seven-day bar chart. Drawn rather than pulled in as a dependency because it is
 * one canvas and a data class, and because the escape days need to read as a
 * different colour without a legend.
 */
@Composable
fun WeekBars(
    focusMs: List<Long>,
    dayLabels: List<String>,
    escapes: List<Int>,
    modifier: Modifier = Modifier,
) {
    val max = (focusMs.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val barColor = MaterialTheme.colorScheme.primary
    val escapeColor = MaterialTheme.colorScheme.error
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val emptyColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            focusMs.forEachIndexed { index, value ->
                val fraction = (value.toFloat() / max.toFloat()).coerceIn(0f, 1f)
                val hasEscape = (escapes.getOrNull(index) ?: 0) > 0
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(88.dp)
                            .clearAndSetSemantics {
                                contentDescription = "${dayLabels.getOrNull(index) ?: ""}: " +
                                    "${(value / 60_000L)} minutes" +
                                    if (hasEscape) ", ${escapes[index]} escape" else ""
                            },
                    ) {
                        val radius = size.width / 2f
                        drawRoundRect(
                            color = trackColor,
                            size = Size(size.width, size.height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
                        )
                        if (value > 0L) {
                            drawRoundRect(
                                color = barColor,
                                size = Size(size.width, size.height * fraction),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
                            )
                        } else {
                            drawCircle(
                                color = emptyColor.copy(alpha = 0.25f),
                                radius = 3.dp.toPx(),
                                center = Offset(size.width / 2f, size.height / 2f),
                            )
                        }
                        if (hasEscape) {
                            drawCircle(
                                color = escapeColor,
                                radius = 4.dp.toPx(),
                                center = Offset(size.width / 2f, 6.dp.toPx()),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            dayLabels.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Loading placeholder that matches the shape of the content it replaces. */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, height: Dp = 72.dp) {
    val placeholder = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        modifier = modifier
            .height(height)
            .drawBehind {
                drawRoundRect(color = placeholder, cornerRadius = CornerRadius(16.dp.toPx()))
            },
    )
}

fun formatClock(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

fun formatDuration(millis: Long): String {
    val minutes = millis / 60_000L
    val hours = minutes / 60
    return when {
        hours > 0 -> "${hours}h ${minutes % 60}m"
        minutes > 0 -> "${minutes}m"
        else -> "${millis / 1000L}s"
    }
}
