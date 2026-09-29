package com.lockin.focus.ui

import android.content.Intent
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lockin.focus.LockIn
import com.lockin.focus.core.model.FocusSettings
import com.lockin.focus.core.model.SessionState
import com.lockin.focus.ui.components.HoldToEscapeButton
import com.lockin.focus.ui.components.StatusPill
import com.lockin.focus.ui.components.formatClock
import com.lockin.focus.ui.components.formatDuration
import com.lockin.focus.ui.components.rememberNow

/**
 * The wall. Slams over a blocked app and stays there until the task is done.
 *
 * The screen has three doors and they are not equal: "back to work" is the
 * default, "task is done" ends the session honestly and logs it, and the
 * emergency escape is a five-second hold that logs a failure. You can always get
 * out — you just cannot get out pretending you did the work.
 */
class BlockActivity : TakeoverActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        blockedLabel = resolveLabel(intent)
        super.onCreate(savedInstanceState)
    }

    private fun resolveLabel(intent: Intent?): String {
        val pkg = intent?.getStringExtra(EXTRA_BLOCKED_PACKAGE) ?: return "That app"
        return runCatching { AppLabelResolver.label(this, pkg) }.getOrDefault(pkg)
    }

    private var blockedLabel: String = "That app"

    @Composable
    override fun TakeoverBody(state: SessionState, settings: FocusSettings, onFinish: () -> Unit) {
        val now = rememberNow(500L)
        val context = androidx.compose.ui.platform.LocalContext.current

        LaunchedEffect(state) {
            if (shouldSelfClose(state)) onFinish()
        }

        // Back and recents both mean "take me back to something I am allowed to use".
        BackHandler {
            LockIn.host?.returnToSafeApp(blockedPackage)
            onFinish()
        }

        // If the guard was switched off while this screen was up there is nowhere
        // safe to bounce to; closing is the only honest outcome.
        LaunchedEffect(Unit) {
            if (LockIn.host == null) onFinish()
        }

        val remaining = state.remainingMs(now)
        val urgent = state.owesCheckIn

        val background = if (urgent) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surface
        }
        val onBackground = if (urgent) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
        val onBackgroundMuted = onBackground.copy(alpha = 0.75f)

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
                verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            ) {
                    Spacer(Modifier.heightIn(min = 0.dp))
                    PulsingLock(color = onBackground)
                    StatusPill(
                        text = if (urgent) "Check-in waiting" else "Blocked",
                        leading = Icons.Rounded.Block,
                        container = if (urgent) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                        content = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        text = blockedLabel,
                        style = MaterialTheme.typography.displaySmall,
                        color = onBackground,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = if (state.task.isBlank()) {
                            "is locked for this session."
                        } else {
                            "is locked until you finish this:\n${state.task}"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = onBackgroundMuted,
                        textAlign = TextAlign.Center,
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = formatClock(remaining),
                            style = MaterialTheme.typography.displayMedium,
                            color = onBackground,
                        )
                        Text(
                            text = if (state.owesCheckIn) {
                                "answer the check-in to get these apps back"
                            } else {
                                "until the next check-in"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = onBackgroundMuted,
                            textAlign = TextAlign.Center,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MiniStat("Locked for", formatDuration(state.elapsedMs(now)))
                            MiniStat("Tried to open", state.blocksIntercepted.toString())
                            MiniStat("Check-ins", state.checkpointsPassed.toString())
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = {
                                LockIn.host?.returnToSafeApp(blockedPackage)
                                onFinish()
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
                            Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(22.dp))
                            Text("  Back to work", style = MaterialTheme.typography.titleMedium)
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
                            Text("  Task is done — unlock everything")
                        }
                        HoldToEscapeButton(
                            label = "Emergency escape — hold 5s",
                            holdMillis = 5_000L,
                            onComplete = {
                                LockIn.escapeSession()
                                onFinish()
                            },
                        )
                        Text(
                            text = "Emergency escape is logged as unfinished. Calls and messages " +
                                "are never blocked.",
                            style = MaterialTheme.typography.bodySmall,
                            color = onBackgroundMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
        }
    }

    private val blockedPackage: String
        get() = intent?.getStringExtra(EXTRA_BLOCKED_PACKAGE).orEmpty()

    companion object {
        const val EXTRA_BLOCKED_PACKAGE = "blocked_package"
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
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

@Composable
private fun PulsingLock(color: Color) {
    val transition = rememberInfiniteTransition(label = "lockPulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "lockScale",
    )
    Box(
        modifier = Modifier
            .size(104.dp)
            .scale(scale)
            .background(color.copy(alpha = 0.12f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(52.dp).alpha(0.95f),
        )
    }
}

private object AppLabelResolver {
    fun label(context: android.content.Context, pkg: String): String =
        com.lockin.focus.data.AppCatalog.labelOf(context, pkg)
}
