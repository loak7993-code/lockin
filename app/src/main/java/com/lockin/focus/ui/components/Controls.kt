package com.lockin.focus.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lockin.focus.data.AppCatalog
import com.lockin.focus.data.InstalledApp
import com.lockin.focus.ui.theme.Motion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The escape hatch. Holding is the point: an app that "locks" itself must still
 * have a door, and a door you have to mean it opens with is a door nobody walks
 * through by accident. Screen readers and switch access get the same door via the
 * long-click action.
 */
@Composable
fun HoldToEscapeButton(
    label: String,
    holdMillis: Long,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val progress = remember { Animatable(0f) }
    var pressed by remember { mutableStateOf(false) }

    LaunchedEffect(pressed, holdMillis, enabled) {
        if (pressed && enabled) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis = holdMillis.toInt(), easing = LinearEasing))
            pressed = false
            onComplete()
        } else if (!pressed) {
            progress.animateTo(
                0f,
                tween(durationMillis = Motion.DURATION_SHORT, easing = Motion.EmphasizedAccelerate),
            )
        }
    }

    val fill = progress.value
    val shape = MaterialTheme.shapes.large
    val baseContainer = MaterialTheme.colorScheme.surfaceContainer
    val fillColor = MaterialTheme.colorScheme.error

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(shape)
            .background(baseContainer)
            .drawBehind {
                drawRect(
                    color = fillColor,
                    alpha = 0.22f * fill.coerceIn(0f, 1f),
                    size = size.copy(width = size.width * fill.coerceIn(0f, 1f)),
                )
            }
            .then(
                if (!enabled) {
                    Modifier
                } else {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                pressed = true
                                tryAwaitRelease()
                                pressed = false
                            },
                        )
                    }
                },
            )
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "$label. Hold for ${holdMillis / 1000} seconds to confirm."
                onLongClick(label = "Hold to confirm") {
                    onComplete()
                    true
                }
            }
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (fill > 0f) "keep holding…" else label,
            style = MaterialTheme.typography.titleSmall,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/** Standard M3 list row with a switch, used all over settings and the blocklist. */
@Composable
fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
fun AppRow(
    app: InstalledApp,
    blocked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(initialValue = null, app.packageName) {
        value = withContext(Dispatchers.IO) { AppCatalog.iconOf(context, app.packageName) }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(
                if (blocked) {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "${app.label}, ${if (blocked) "blocked" else "allowed"}"
                onLongClick(label = "Toggle") {
                    onToggle()
                    true
                }
            }
            .pointerInputTap(onToggle)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Image(bitmap = icon!!, contentDescription = null, modifier = Modifier.fillMaxSize().padding(4.dp))
            } else {
                Text(
                    text = app.label.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(checked = blocked, onCheckedChange = { onToggle() })
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(4.dp))
            action()
        }
    }
}

@Composable
fun VSpace(height: Dp) = Spacer(Modifier.height(height))

/** Tappable wrapper used by [AppRow]; kept separate so the row stays declarative. */
private fun Modifier.pointerInputTap(onTap: () -> Unit): Modifier =
    this.then(
        Modifier.pointerInput(onTap) { detectTapGestures(onTap = { onTap() }) },
    )
