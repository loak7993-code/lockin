package com.lockin.focus.ui

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lockin.focus.LockIn
import com.lockin.focus.R
import com.lockin.focus.core.model.FocusSettings
import com.lockin.focus.core.model.SessionState
import com.lockin.focus.ui.components.HoldToEscapeButton
import com.lockin.focus.ui.components.StatusPill
import com.lockin.focus.ui.components.VSpace
import com.lockin.focus.ui.components.formatDuration
import com.lockin.focus.ui.components.rememberNow
import com.lockin.focus.ui.theme.Motion

/**
 * The 15-minute check-in.
 *
 * It takes the screen and asks one question. There is no dismiss gesture and no
 * timeout: the only ways out are answering, or the five-second hold. Swiping away
 * just makes the guard put it straight back, and the counter in the corner keeps
 * score of how many times you tried.
 */
class CheckpointActivity : TakeoverActivity() {

    /**
     * Preview mode, launched from Settings so the user can see what a check-in
     * looks like without waiting out a real interval. It shows the real screen but
     * touches nothing: no answer is recorded, no dodge is counted, and the actual
     * countdown is not reset. It is a look, not a door.
     */
    private val preview: Boolean
        get() = intent?.getBooleanExtra(EXTRA_PREVIEW, false) == true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LockIn.host?.signalHaptic()
    }

    @Composable
    override fun TakeoverBody(state: SessionState, settings: FocusSettings, onFinish: () -> Unit) {
        val now = rememberNow(500L)
        var editing by remember { mutableStateOf(false) }
        var draft by remember(state.task) { mutableStateOf(state.task) }

        LaunchedEffect(state) {
            if (!preview && (shouldSelfClose(state) || !state.owesCheckIn)) onFinish()
        }

        BackHandler {
            if (preview) {
                onFinish()
                return@BackHandler
            }
            // No door here. The guard will raise this screen again the moment the
            // user touches anything, and the dodge counter goes up.
            LockIn.engine.nag()
        }

        val nagLine = when (state.nags) {
            0 -> "You have to answer this one."
            1 -> "Dodged once. It comes back."
            2 -> "Twice now. Still here."
            else -> "${state.nags} times. This screen is not going anywhere."
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .systemBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxHeight()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
            ) {
                    if (preview) {
                        StatusPill(
                            text = "Preview — nothing you do here counts",
                            leading = Icons.Rounded.Visibility,
                            container = MaterialTheme.colorScheme.tertiaryContainer,
                            content = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                    IntervalBadge(minutes = state.intervalMs / 60_000L)
                    Text(
                        text = "What are you\nworking on?",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = if (preview) {
                            "This is what LockIn will put on your screen every " +
                                "${state.intervalMs / 60_000L} minutes. Your real countdown " +
                                "is still running — closing this changes nothing."
                        } else {
                            "Your apps stay locked until you say this is done. Answer honestly — " +
                                "the only thing this screen is for is making you say it out loud."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )

                    CardLikeContainer {
                        if (editing) {
                            OutlinedTextField(
                                value = draft,
                                onValueChange = { if (it.length <= MAX_TASK_LENGTH) draft = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Primary task") },
                                singleLine = false,
                                minLines = 2,
                                shape = MaterialTheme.shapes.large,
                            )
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "COMMITTED TO",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    // Previewing with no session running would otherwise
                                    // show an empty commitment, which is the opposite of
                                    // what this screen is trying to demonstrate.
                                    text = state.task.ifBlank {
                                        if (preview) "Primary study — chapter 4 problems" else "—"
                                    },
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatChip("Locked for", formatDuration(state.elapsedMs(now)))
                        StatChip("Tried to open", state.blocksIntercepted.toString())
                        StatChip("Check-ins", state.checkpointsPassed.toString())
                    }

                    if (state.nags > 0) {
                        Text(
                            text = nagLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (!preview) {
                            Button(
                                onClick = {
                                    if (editing) {
                                        if (LockIn.engine.retask(draft)) editing = false
                                    } else {
                                        editing = true
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp),
                                shape = MaterialTheme.shapes.large,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            ) {
                                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
                                Text(
                                    text = if (editing) "  Commit to this" else "  No — change the task",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                            FilledTonalButton(
                                onClick = {
                                    LockIn.engine.acknowledge()
                                    onFinish()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp),
                                shape = MaterialTheme.shapes.large,
                            ) {
                                Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(20.dp))
                                Text(
                                    text = "  Still on it — next ${state.intervalMs / 60_000L} min",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    LockIn.completeSession()
                                    onFinish()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = MaterialTheme.shapes.large,
                            ) {
                                Icon(Icons.Rounded.DoneAll, contentDescription = null, modifier = Modifier.size(20.dp))
                                Text("  It's done — unlock everything")
                            }
                            HoldToEscapeButton(
                                label = "Emergency escape — hold 5s",
                                holdMillis = 5_000L,
                                onComplete = {
                                    LockIn.escapeSession()
                                    onFinish()
                                },
                            )
                        } else {
                            Button(
                                onClick = onFinish,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp),
                                shape = MaterialTheme.shapes.large,
                            ) {
                                Text("  Close preview", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
    }

    companion object {
        private const val MAX_TASK_LENGTH = 80
        const val EXTRA_PREVIEW = "preview"
    }
}

@Composable
private fun IntervalBadge(minutes: Long) {
    val transition = rememberInfiniteTransition(label = "badgePulse")
    val alpha by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(Motion.DURATION_LONG, easing = Motion.Emphasized),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "badgeAlpha",
    )
    Box(modifier = Modifier.alpha(alpha)) {
        StatusPill(
            text = pluralStringResource(
                R.plurals.checkin_every,
                minutes.toInt(),
                minutes,
            ),
            leading = Icons.Rounded.Bolt,
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun CardLikeContainer(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.extraLarge)
            .padding(20.dp),
    ) { content() }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.large)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
