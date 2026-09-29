package com.lockin.focus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lockin.focus.LockIn
import com.lockin.focus.R
import com.lockin.focus.core.DayClock
import com.lockin.focus.core.model.Outcome
import com.lockin.focus.core.model.SessionRecord
import com.lockin.focus.ui.MainViewModel
import com.lockin.focus.ui.components.EmptyState
import com.lockin.focus.ui.components.ReadableScreen
import com.lockin.focus.ui.components.SectionHeader
import com.lockin.focus.ui.components.StatTile
import com.lockin.focus.ui.components.VSpace
import com.lockin.focus.ui.components.WeekBars
import com.lockin.focus.ui.components.formatDuration
import com.lockin.focus.ui.components.rememberNow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val WEEK_DAYS = 7

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(history: List<SessionRecord>, viewModel: MainViewModel) {
    val now = rememberNow(1_000L)
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val days = remember(history, now) { LockIn.computeDayStats(history, now, WEEK_DAYS) }
    val totalMs = history.sumOf { it.durationMs }
    val completed = history.count { it.outcome == Outcome.COMPLETED }
    val escapes = history.size - completed
    val streak = LockInStreak.compute(history, now)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { TopAppBar(title = { Text("History") }, scrollBehavior = scrollBehavior) },
    ) { padding ->
        if (history.isEmpty()) {
            Column(
                modifier = Modifier.padding(padding).padding(top = 40.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                EmptyState(
                    icon = Icons.Rounded.History,
                    title = "No sessions yet",
                    body = "Lock in on something and it shows up here — how long you held, how " +
                        "many times you tried to open a blocked app, and whether you finished.",
                )
            }
            return@Scaffold
        }

        ReadableScreen(modifier = Modifier.padding(padding)) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        SectionHeader("Last 7 days", Modifier.fillMaxWidth())
                        WeekBars(
                            focusMs = days.map { it.focusedMs },
                            escapes = days.map { it.escapes },
                            dayLabels = days.map { dayLabel(it.dayStart) },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile(
                                value = formatDuration(totalMs),
                                label = "Total focused",
                                icon = Icons.Rounded.LockClock,
                                modifier = Modifier.weight(1f),
                            )
                            StatTile(
                                value = "$streak d",
                                label = "Streak",
                                icon = Icons.Rounded.Insights,
                                modifier = Modifier.weight(1f),
                                accent = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile(
                                value = completed.toString(),
                                label = "Finished",
                                icon = Icons.Rounded.LockClock,
                                modifier = Modifier.weight(1f),
                                accent = MaterialTheme.colorScheme.tertiary,
                            )
                            StatTile(
                                value = escapes.toString(),
                                label = "Escaped",
                                icon = Icons.Rounded.Warning,
                                modifier = Modifier.weight(1f),
                                accent = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SectionHeader("Sessions (${history.size})", Modifier.weight(1f))
                    TextButton(onClick = { viewModel.clearHistory() }) { Text("Clear") }
                }
            }

            items(history, key = { it.id }) { record ->
                HistoryRow(record)
            }

            item { VSpace(8.dp) }
            item {
                Text(
                    text = "An escape is a 5-second hold on the block screen. It unlocks you and " +
                        "logs the session as unfinished, which is the only honest way to count it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                )
            }
        }
        }
    }
}

@Composable
private fun HistoryRow(record: SessionRecord) {
    val escaped = record.outcome == Outcome.ESCAPED
    val time = remember(record.startedAt) { timeLabel(record.startedAt) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = record.task,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (escaped) "ESCAPED" else "DONE",
                style = MaterialTheme.typography.labelSmall,
                color = if (escaped) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.tertiary
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = formatDuration(record.durationMs),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(text = time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = "${record.blocksIntercepted} blocked",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (record.checkpointsPassed > 0) {
                Text(
                    text = pluralStringResource(
                        R.plurals.checkins_count,
                        record.checkpointsPassed,
                        record.checkpointsPassed,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun dayLabel(dayStart: Long): String {
    val calendar = Calendar.getInstance().apply { timeInMillis = dayStart }
    val names = listOf("S", "M", "T", "W", "T", "F", "S")
    return names[calendar.get(Calendar.DAY_OF_WEEK) - 1]
}

private fun timeLabel(epochMs: Long): String {
    val today = DayClock.startOfDay(System.currentTimeMillis())
    val pattern = if (DayClock.startOfDay(epochMs) == today) "HH:mm" else "d MMM, HH:mm"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(epochMs))
}
