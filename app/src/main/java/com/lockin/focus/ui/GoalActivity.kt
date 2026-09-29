package com.lockin.focus.ui

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lockin.focus.LockIn
import com.lockin.focus.core.model.FocusSettings
import com.lockin.focus.core.model.SessionState
import com.lockin.focus.ui.components.CountdownRing
import com.lockin.focus.ui.components.HoldToEscapeButton
import com.lockin.focus.ui.components.StatusPill
import com.lockin.focus.ui.components.formatDuration
import com.lockin.focus.ui.components.rememberNow
import com.lockin.focus.ui.theme.Motion

/**
 * Caught you.
 *
 * The doom detector has clocked enough time inside a blocked app that this is no
 * longer "checking something". The screen replaces the normal block wall
 * entirely — there is no *Back to work* here, because that is the button that
 * would put you straight back into the feed. The only ways out are doing the
 * thing on screen, rerolling for something else, or the five-second hold, which
 * logs as a failure.
 */
class GoalActivity : TakeoverActivity() {

    private val voluntary: Boolean
        get() = intent?.getBooleanExtra(EXTRA_VOLUNTARY, false) == true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Voluntary roulette has no goal behind it either, so draw one if the deck
        // has not been served yet.
        if (LockIn.activeGoal == null) LockIn.rerollGoal()
        LockIn.host?.signalHaptic()
    }

    @Composable
    override fun TakeoverBody(state: SessionState, settings: FocusSettings, onFinish: () -> Unit) {
        val now = rememberNow(500L)
        val event = LockIn.lastDoomEvent
        // Held locally rather than read straight off LockIn each recomposition.
        // The goal is a plain @Volatile, so Compose has no way to notice it
        // changed — the reroll would only land on the screen whenever some
        // unrelated 500ms tick happened to repaint, which is luck, not behaviour.
        var goalState by remember { mutableStateOf(LockIn.activeGoal) }
        val goal = goalState
        var working by remember { mutableStateOf(false) }
        var workingUntil by remember { mutableStateOf(0L) }

        LaunchedEffect(state) {
            if (!voluntary && (shouldSelfClose(state) || goal == null)) onFinish()
        }

        val remaining = if (working) (workingUntil - now).coerceAtLeast(0L) else 0L
        val finished = working && remaining == 0L

        LaunchedEffect(finished) {
            if (finished) {
                LockIn.goalServed()
                onFinish()
            }
        }

        BackHandler {
            // Voluntary roulette is dismissible; a caught doom scroll is not, and
            // once the clock is running the back button cannot un-start it either.
            if (voluntary) onFinish()
        }

        val accent = MaterialTheme.colorScheme.tertiary
        val background = if (voluntary) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.tertiaryContainer
        }
        val onBackground = if (voluntary) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onTertiaryContainer
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
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
                Pulse(title = if (voluntary) "Pick something" else "Caught you")

                if (!voluntary && event != null) {
                    Text(
                        text = "${formatDuration(event.millisInBlockedApps)} in ${event.appLabel}. " +
                            event.trigger.blurb,
                        style = MaterialTheme.typography.titleMedium,
                        color = onBackground,
                        textAlign = TextAlign.Center,
                    )
                }

                CardLikeBox(color = MaterialTheme.colorScheme.surface) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (goal != null) {
                            StatusPill(
                                text = goal.category.label,
                                leading = Icons.Rounded.LocalFireDepartment,
                                container = MaterialTheme.colorScheme.primaryContainer,
                                content = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                text = goal.text,
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        } else {
                            Text(
                                text = "No goal left. Good.",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                if (working && goal != null) {
                    CountdownRing(
                        progress = if (goal.minutes > 0) {
                            1f - remaining.toFloat() / (goal.minutes * 60_000f)
                        } else {
                            0f
                        }.coerceIn(0f, 1f),
                        timeText = formatRemaining(remaining),
                        caption = "go",
                        diameter = 190.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        progressColor = accent,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "The feed comes back when this finishes. It is not waiting " +
                            "for you and it will not miss you.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (goal != null && !finished) {
                        Button(
                            onClick = {
                                workingUntil = now + goal.minutes * 60_000L
                                working = true
                            },
                            // Once the clock is running it is committed. Leaving it
                            // live let a tap reset the deadline to now + duration,
                            // which is an unbounded free extension.
                            enabled = !working,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp),
                            shape = MaterialTheme.shapes.large,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                                contentColor = MaterialTheme.colorScheme.onTertiary,
                            ),
                        ) {
                            Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(22.dp))
                            Text(
                                text = if (working) {
                                    "  Doing it — ${formatRemaining(remaining)}"
                                } else {
                                    "  Doing it — ${goal.minutes} min"
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                    FilledTonalButton(
                        onClick = {
                            LockIn.rerollGoal()
                            goalState = LockIn.activeGoal
                        },
                        // Same reasoning: swapping the task halfway through would
                        // leave the countdown describing something you are no
                        // longer doing.
                        enabled = !working,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Icon(Icons.Rounded.Casino, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(if (working) "  Locked in — finish it" else "  Give me another one")
                    }
                    OutlinedButton(
                        onClick = {
                            LockIn.goalServed()
                            onFinish()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Icon(Icons.Rounded.DoneAll, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text("  I'm already doing something else")
                    }
                    if (!voluntary) {
                        HoldToEscapeButton(
                            label = "Give up — hold 5s",
                            holdMillis = 5_000L,
                            onComplete = {
                                LockIn.escapeSession()
                                onFinish()
                            },
                        )
                        Text(
                            text = "Giving up unlocks everything and logs the session as " +
                                "failed. There is always a way out; it just costs you.",
                            style = MaterialTheme.typography.bodySmall,
                            color = onBackground.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_VOLUNTARY = "voluntary"

        private fun formatRemaining(ms: Long): String {
            val total = (ms / 1000).coerceAtLeast(0)
            return "%d:%02d".format(total / 60, total % 60)
        }
    }
}

@Composable
private fun Pulse(title: String) {
    val transition = rememberInfiniteTransition(label = "goalPulse")
    val alpha by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(Motion.DURATION_LONG, easing = Motion.Emphasized),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "goalAlpha",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.alpha(alpha),
    ) {
        Icon(
            Icons.Rounded.Visibility,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@Composable
private fun CardLikeBox(color: androidx.compose.ui.graphics.Color, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color, MaterialTheme.shapes.extraLarge)
            .padding(20.dp),
    ) { content() }
}
