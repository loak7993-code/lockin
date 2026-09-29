package com.lockin.focus.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lockin.focus.core.AccessPolicy
import com.lockin.focus.core.model.FocusSettings
import com.lockin.focus.data.AppGroup
import com.lockin.focus.data.Distractors
import com.lockin.focus.data.InstalledApp
import com.lockin.focus.ui.MainViewModel
import com.lockin.focus.ui.components.AppRow
import com.lockin.focus.ui.components.EmptyState
import com.lockin.focus.ui.components.ReadableScreen
import com.lockin.focus.ui.components.SectionHeader
import com.lockin.focus.ui.components.VSpace

private enum class BlockFilter(val label: String) {
    ALL("All"),
    BLOCKED("Blocked"),
    DISTRACTORS("Distractors"),
    ALLOWED("Allowed"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockListScreen(
    settings: FocusSettings,
    installedApps: List<InstalledApp>,
    loading: Boolean,
    failed: Boolean,
    viewModel: MainViewModel,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(BlockFilter.ALL) }
    var confirmBlockAll by remember { mutableStateOf(false) }

    val installedSet = remember(installedApps) { installedApps.map { it.packageName }.toSet() }
    // The quick picks have to honour the search box, or a search for an app that
    // is not a usual suspect shows a list of ones that are, which reads as a bug.
    val quickPicks = remember(installedApps, query) {
        val q = query.trim().lowercase()
        Distractors.KNOWN.mapNotNull { (pkg, label) ->
            installedApps.firstOrNull { it.packageName == pkg }?.copy(label = label)
        }.filter { q.isEmpty() || it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
    }

    val visible = remember(installedApps, query, filter, settings) {
        val q = query.trim().lowercase()
        val self = context.packageName
        installedApps.filter { app ->
            if (app.packageName == self) return@filter false
            val matchesQuery = q.isEmpty() ||
                app.label.lowercase().contains(q) ||
                app.packageName.lowercase().contains(q)
            val matchesFilter = when (filter) {
                BlockFilter.ALL -> true
                BlockFilter.BLOCKED -> app.packageName in settings.blocked
                BlockFilter.DISTRACTORS -> app.group != AppGroup.OTHER
                BlockFilter.ALLOWED -> app.packageName in settings.allowed ||
                    app.packageName in AccessPolicy.SYSTEM_ALWAYS_ALLOWED
            }
            matchesQuery && matchesFilter
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Blocklist") },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        ReadableScreen(modifier = Modifier.padding(padding)) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    text = "These apps get a full-screen wall while a session runs. Calls, messages, " +
                        "settings and your launcher always stay open — a focus timer must never be " +
                        "the reason you cannot answer the phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search apps") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BlockFilter.entries.forEach { entry ->
                        FilterChip(
                            selected = filter == entry,
                            onClick = { filter = entry },
                            label = { Text(entry.label) },
                        )
                    }
                }
            }

            if (quickPicks.isNotEmpty() && filter != BlockFilter.ALLOWED) {
                item { VSpace(6.dp) }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionHeader("The usual suspects", Modifier.weight(1f))
                        TextButton(
                            onClick = {
                                val allBlocked = quickPicks.all { it.packageName in settings.blocked }
                                if (allBlocked) {
                                    quickPicks.forEach { viewModel.setBlocked(it.packageName, false) }
                                } else {
                                    quickPicks.forEach { viewModel.setBlocked(it.packageName, true) }
                                }
                            },
                        ) {
                            Text(
                                if (quickPicks.all { it.packageName in settings.blocked }) "Clear all" else "Block all",
                            )
                        }
                    }
                }
                item {
                    Column(
                        modifier = Modifier.animateContentSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        quickPicks.forEach { app ->
                            AppRow(
                                app = app,
                                blocked = app.packageName in settings.blocked,
                                onToggle = {
                                    viewModel.setBlocked(
                                        app.packageName,
                                        app.packageName !in settings.blocked,
                                    )
                                },
                            )
                        }
                    }
                }
            }

            item { VSpace(8.dp) }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionHeader("Installed (${visible.size})", Modifier.weight(1f))
                    if (visible.isNotEmpty() && !loading) {
                        TextButton(onClick = { viewModel.clearBlocked() }) { Text("Clear") }
                    }
                }
            }

            when {
                loading -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        repeat(5) { SkeletonRow() }
                    }
                }

                failed -> item {
                    EmptyState(
                        icon = Icons.Rounded.Apps,
                        title = "Could not read your apps",
                        body = "Android refused to list installed apps. Try again.",
                        action = { TextButton(onClick = { viewModel.refreshApps() }) { Text("Retry") } },
                    )
                }

                visible.isEmpty() -> item {
                    EmptyState(
                        icon = Icons.Rounded.Search,
                        title = "Nothing matches",
                        body = if (query.isBlank()) {
                            "No apps in this filter. Apps you have blocked that are no longer " +
                                "installed stay in the list but do nothing."
                        } else {
                            "No app called \"$query\". Try the package name."
                        },
                    )
                }

                else -> items(visible, key = { it.packageName }) { app ->
                    AppRow(
                        app = app,
                        blocked = app.packageName in settings.blocked,
                        onToggle = {
                            viewModel.setBlocked(app.packageName, app.packageName !in settings.blocked)
                        },
                    )
                }
            }

            item { VSpace(6.dp) }
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SectionHeader("Everything at once", Modifier.fillMaxWidth())
                        Text(
                            text = "Locking every app leaves you the phone, messages, the clock, " +
                                "settings and your launcher — the apps the policy will never " +
                                "block, whatever you select here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { confirmBlockAll = true },
                                enabled = installedApps.any { it.packageName !in settings.blocked },
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.large,
                            ) {
                                Text("Block everything")
                            }
                            OutlinedButton(
                                onClick = { viewModel.clearBlocked() },
                                enabled = settings.blocked.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.large,
                            ) {
                                Text("Deselect all")
                            }
                        }
                    }
                }
            }

            val orphaned = settings.blocked - installedSet
            if (orphaned.isNotEmpty() && filter == BlockFilter.BLOCKED) {
                item { VSpace(10.dp) }
                item {
                    SectionHeader("No longer installed (${orphaned.size})", Modifier.fillMaxWidth())
                }
                items(orphaned.sorted().toList(), key = { "orphan-$it" }) { pkg ->
                    AppRow(
                        app = InstalledApp(pkg, pkg, system = false, group = AppGroup.OTHER),
                        blocked = true,
                        onToggle = { viewModel.setBlocked(pkg, false) },
                    )
                }
            }
        }
        }
    }

    if (confirmBlockAll) {
        AlertDialog(
            onDismissRequest = { confirmBlockAll = false },
            title = { Text("Block every installed app?") },
            text = {
                Text(
                    "Everything goes on the list except the phone, messages, the clock, " +
                        "settings and your launcher, which LockIn refuses to block. " +
                        "Nothing but those will open until you finish a session.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.blockAllInstalled()
                    confirmBlockAll = false
                    filter = BlockFilter.BLOCKED
                }) { Text("Block everything") }
            },
            dismissButton = {
                TextButton(onClick = { confirmBlockAll = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SkeletonRow() {
    com.lockin.focus.ui.components.SkeletonBlock(height = 64.dp, modifier = Modifier.fillMaxWidth())
}
