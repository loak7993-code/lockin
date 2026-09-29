package com.lockin.focus.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lockin.focus.R
import com.lockin.focus.core.DayClock
import com.lockin.focus.core.model.FocusSettings
import com.lockin.focus.core.model.Outcome
import com.lockin.focus.core.model.SessionRecord
import com.lockin.focus.core.model.SessionState
import com.lockin.focus.ui.MainViewModel
import com.lockin.focus.ui.components.CountdownRing
import com.lockin.focus.ui.components.HoldToEscapeButton
import com.lockin.focus.ui.components.ReadableScreen
import com.lockin.focus.ui.components.SectionHeader
import com.lockin.focus.ui.components.StatTile
import com.lockin.focus.ui.components.StatusPill
import com.lockin.focus.ui.components.VSpace
import com.lockin.focus.ui.components.formatClock
import com.lockin.focus.ui.components.formatDuration
import com.lockin.focus.ui.components.rememberNow
import com.lockin.focus.util.GuardPermissions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: SessionState,
    settings: FocusSettings,
    history: List<SessionRecord>,
    permissions: GuardPermissions,
    viewModel: MainViewModel,
    onOpenBlocks: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onRollGoal: () -> Unit,
) {
    val now = rememberNow(500L)
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            // A small bar on purpose: this screen's job is to put the countdown and
            // the task in front of the user the instant the app opens.
            TopAppBar(
                title = { Text("LockIn") },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        ReadableScreen(modifier = Modifier.padding(padding)) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                if (state.active) {
                    ActiveSessionCard(state, now, viewModel)
                } else {
                    IdleSessionCard(state, settings, permissions, onOpenAccessibilitySettings, viewModel)
                }
            }

            if (!state.active) {
                item { GuardStatusCard(permissions, settings, onOpenBlocks, onOpenAccessibilitySettings) }
            }

            if (settings.goalRoulette) {
                item { GoalRouletteCard(onRoll = onRollGoal) }
            }

            item { TodayCard(history, now) }

            if (history.isNotEmpty()) {
                item { SectionHeader("Recent", Modifier.fillMaxWidth()) }
                items(history.take(3), key = { it.id }) { record -> SessionRow(record) }
            }
        }
        }
    }
}

@Composable
private fun IdleSessionCard(
    state: SessionState,
    settings: FocusSettings,
    permissions: GuardPermissions,
    onOpenAccessibilitySettings: () -> Unit,
    viewModel: MainViewModel,
) {
    var task by remember(state.task) { mutableStateOf(state.task) }
    val blockedCount = settings.blocked.size
    val canStart = task.isNotBlank() && permissions.canEnforce && blockedCount > 0

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusPill(
                    text = "Not locked in",
                    leading = Icons.Rounded.LockOpen,
                    container = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                StatusPill(
                    text = "${settings.intervalMinutes} min",
                    leading = Icons.Rounded.LockClock,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }

            Text(
                text = "What is your primary task?",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "One thing. The apps you picked stay locked until you say this is done.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = task,
                onValueChange = { if (it.length <= MAX_TASK_LENGTH) task = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Primary task") },
                placeholder = { Text("Primary study — chapter 4 problems") },
                singleLine = false,
                minLines = 2,
                shape = MaterialTheme.shapes.large,
                supportingText = {
                    Text(
                        text = if (task.isBlank()) {
                            "Required"
                        } else {
                            "${task.trim().length}/$MAX_TASK_LENGTH"
                        },
                    )
                },
            )

            AnimatedVisibility(
                visible = !permissions.canEnforce,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                InlineWarning(
                    text = "The focus guard is off, so nothing would actually be blocked.",
                    actionLabel = "Turn it on",
                    onAction = onOpenAccessibilitySettings,
                )
            }

            if (blockedCount == 0) {
                InlineWarning(text = "Pick at least one app to block first.", container = null)
            }

            Button(
                onClick = { viewModel.startSession(task) },
                enabled = canStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(22.dp))
                Text(
                    text = "  LOCK IN",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun ActiveSessionCard(state: SessionState, now: Long, viewModel: MainViewModel) {
    val remaining = state.remainingMs(now)
    val progress = state.intervalProgress(now)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusPill(
                    text = if (state.owesCheckIn) "Check-in due" else "Locked in",
                    leading = Icons.Rounded.Lock,
                    container = if (state.owesCheckIn) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    content = if (state.owesCheckIn) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                )
                Text(
                    text = formatDuration(state.elapsedMs(now)),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            CountdownRing(
                progress = progress,
                timeText = formatClock(remaining),
                caption = if (state.owesCheckIn) "answer me" else "next check-in",
                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                progressColor = MaterialTheme.colorScheme.onPrimaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                diameter = 216.dp,
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.task,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                )
                VSpace(6.dp)
                Text(
                    text = if (state.owesCheckIn) {
                        "LOCKED until you say this is done."
                    } else {
                        "Locked until you say this is done. " +
                            pluralStringResource(
                                R.plurals.checkin_every,
                                (state.intervalMs / 60_000L).toInt(),
                                (state.intervalMs / 60_000L).toInt(),
                            ) + "."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                    textAlign = TextAlign.Center,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    value = state.blocksIntercepted.toString(),
                    label = "Blocked",
                    icon = Icons.Rounded.Shield,
                    modifier = Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                StatTile(
                    value = state.checkpointsPassed.toString(),
                    label = "Check-ins",
                    icon = Icons.Rounded.Bolt,
                    modifier = Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                StatTile(
                    value = formatDuration(state.elapsedMs(now)),
                    label = "Locked for",
                    icon = Icons.Rounded.LockClock,
                    modifier = Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            // Only worth the row when something actually happened. A doom alert is
            // the most expensive thing this app can do to you, and it should be
            // on the record rather than silently absorbed.
            if (state.doomAlerts > 0 || state.goalsCompleted > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        value = state.doomAlerts.toString(),
                        label = "Caught",
                        icon = Icons.Rounded.LocalFireDepartment,
                        modifier = Modifier.weight(1f),
                        accent = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    StatTile(
                        value = state.goalsCompleted.toString(),
                        label = "Goals done",
                        icon = Icons.Rounded.Casino,
                        modifier = Modifier.weight(1f),
                        accent = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    StatTile(
                        value = formatDuration(state.distractorMs),
                        label = "In apps",
                        icon = Icons.Rounded.PhoneAndroid,
                        modifier = Modifier.weight(1f),
                        accent = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Button(
                onClick = { viewModel.completeSession() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                Icon(Icons.Rounded.DoneAll, contentDescription = null, modifier = Modifier.size(22.dp))
                Text("  Task is done — unlock", style = MaterialTheme.typography.titleMedium)
            }

            HoldToEscapeButton(
                label = "Emergency escape — hold 5s",
                holdMillis = 5_000L,
                onComplete = { viewModel.escapeSession() },
            )
        }
    }
}

@Composable
private fun GuardStatusCard(
    permissions: GuardPermissions,
    settings: FocusSettings,
    onOpenBlocks: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionHeader("Armed", Modifier.weight(1f))
                StatusPill(
                    text = if (permissions.canEnforce) "Guard on" else "Guard off",
                    leading = if (permissions.canEnforce) Icons.Rounded.Check else Icons.Rounded.Warning,
                    container = if (permissions.canEnforce) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    content = if (permissions.canEnforce) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    },
                )
            }

            Text(
                text = pluralStringResource(
                    R.plurals.apps_locked_summary,
                    settings.blocked.size,
                    settings.blocked.size,
                    settings.intervalMinutes,
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (!permissions.canEnforce) {
                InlineWarning(
                    text = "Without the focus guard LockIn cannot see which app is open, so it cannot block anything.",
                    actionLabel = "Open settings",
                    onAction = onOpenAccessibilitySettings,
                )
            } else if (!permissions.notificationsOn) {
                InlineWarning(
                    text = "Notifications are off, so the countdown is not on your lock screen.",
                    actionLabel = "Fix",
                    onAction = onOpenAccessibilitySettings,
                )
            }

            OutlinedButton(
                onClick = onOpenBlocks,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            ) {
                Text("Choose what to block")
            }
        }
    }
}

/** A deliberate way in, so the goal deck is reachable without being caught. */
@Composable
private fun GoalRouletteCard(onRoll: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader("Bored? Or avoiding?", Modifier.fillMaxWidth())
            Text(
                text = "Same deck the doom detector uses. Russian maths, English, homework, " +
                    "going outside — whatever comes up.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(
                onClick = onRoll,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                Icon(Icons.Rounded.Casino, contentDescription = null, modifier = Modifier.size(20.dp))
                Text("  Give me something to do")
            }
        }
    }
}

@Composable
private fun TodayCard(history: List<SessionRecord>, now: Long) {
    val startOfToday = DayClock.startOfDay(now)
    val today = history.filter { it.startedAt >= startOfToday }
    val todayMs = today.sumOf { it.durationMs }
    val streak = LockInStreak.compute(history, now)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader("Today")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    value = formatDuration(todayMs),
                    label = "Locked in",
                    icon = Icons.Rounded.LockClock,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = today.count { it.outcome == Outcome.COMPLETED }.toString(),
                    label = "Finished",
                    icon = Icons.Rounded.DoneAll,
                    modifier = Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.tertiary,
                )
                StatTile(
                    value = "${streak}d",
                    label = "Streak",
                    icon = Icons.Rounded.Insights,
                    modifier = Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

@Composable
internal fun SessionRow(record: SessionRecord) {
    val escaped = record.outcome == Outcome.ESCAPED
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.large)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    if (escaped) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (escaped) Icons.Rounded.Warning else Icons.Rounded.Check,
                contentDescription = null,
                tint = if (escaped) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onTertiaryContainer
                },
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.task,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = formatDuration(record.durationMs) + " · " +
                    pluralStringResource(R.plurals.blocked_count, record.blocksIntercepted, record.blocksIntercepted),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InlineWarning(
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    container: androidx.compose.ui.graphics.Color? = MaterialTheme.colorScheme.errorContainer,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                container ?: MaterialTheme.colorScheme.surfaceContainerHighest,
                MaterialTheme.shapes.large,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Warning,
            contentDescription = null,
            tint = if (container != null) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (container != null) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(
                    text = actionLabel,
                    color = if (container != null) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        }
    }
}

private const val MAX_TASK_LENGTH = 80

internal object LockInStreak {
    fun compute(history: List<SessionRecord>, now: Long): Int {
        if (history.isEmpty()) return 0
        val completedDays = history
            .filter { it.outcome == Outcome.COMPLETED }
            .map { DayClock.startOfDay(it.startedAt) }
            .toSortedSet()
        if (completedDays.isEmpty()) return 0
        var streak = 0
        var day = DayClock.startOfDay(now)
        // Today not being logged yet must not break a streak that is still alive.
        if (day !in completedDays) day -= DayClock.DAY_MS
        while (day in completedDays) {
            streak++
            day -= DayClock.DAY_MS
        }
        return streak
    }
}
