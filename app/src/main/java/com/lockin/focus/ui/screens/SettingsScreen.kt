package com.lockin.focus.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lockin.focus.R
import com.lockin.focus.core.model.FocusSettings
import com.lockin.focus.core.model.PRESET_INTERVAL_MINUTES
import com.lockin.focus.core.model.SeedPreset
import com.lockin.focus.core.model.ThemeMode
import com.lockin.focus.ui.CheckpointActivity
import com.lockin.focus.ui.MainViewModel
import com.lockin.focus.ui.components.ReadableScreen
import com.lockin.focus.ui.components.SectionHeader
import com.lockin.focus.ui.components.SwitchRow
import com.lockin.focus.ui.components.VSpace
import com.lockin.focus.ui.theme.TonalPalette
import com.lockin.focus.util.GuardPermissions
import com.lockin.focus.util.Permissions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: FocusSettings,
    permissions: GuardPermissions,
    viewModel: MainViewModel,
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    var confirmClear by remember { mutableStateOf(false) }
    var snackbar by remember { mutableStateOf<String?>(null) }

    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) viewModel.applySeedFromImage(uri) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { TopAppBar(title = { Text("Settings") }, scrollBehavior = scrollBehavior) },
    ) { padding ->
        ReadableScreen(modifier = Modifier.padding(padding)) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            snackbar?.let { message ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }

            item { SectionHeader("Check-in interval", Modifier.fillMaxWidth()) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PRESET_INTERVAL_MINUTES.forEach { minutes ->
                        FilterChip(
                            selected = settings.intervalMinutes == minutes,
                            onClick = { viewModel.setIntervalMinutes(minutes) },
                            label = { Text("${minutes}m") },
                        )
                    }
                }
            }
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Rounded.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = pluralStringResource(
                                    R.plurals.minutes_label,
                                    settings.intervalMinutes.toInt(),
                                    settings.intervalMinutes,
                                ),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Finer control",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { viewModel.setIntervalMinutes(settings.intervalMinutes - 1) },
                                    enabled = settings.intervalMinutes > 1,
                                ) { Text("−") }
                                OutlinedButton(
                                    onClick = { viewModel.setIntervalMinutes(settings.intervalMinutes + 1) },
                                    enabled = settings.intervalMinutes < 180,
                                ) { Text("+") }
                            }
                        }
                        Text(
                            text = pluralStringResource(
                                R.plurals.checkin_every,
                                settings.intervalMinutes.toInt(),
                                settings.intervalMinutes,
                            ) + ". LockIn takes the screen and asks what you are working on. " +
                                "Answer it, or the apps stay locked.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent(context, CheckpointActivity::class.java)
                                        .putExtra(CheckpointActivity.EXTRA_PREVIEW, true)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(20.dp))
                            Text("  See what a check-in looks like")
                        }
                        Text(
                            text = "Opens the real screen so you know what you are signing up " +
                                "for. Answering it does nothing and your countdown keeps running.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item { VSpace(6.dp) }
            item { SectionHeader("Appearance", Modifier.fillMaxWidth()) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = {
                                Text(
                                    when (mode) {
                                        ThemeMode.SYSTEM -> "System"
                                        ThemeMode.LIGHT -> "Light"
                                        ThemeMode.DARK -> "Dark"
                                    },
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = when (mode) {
                                        ThemeMode.SYSTEM -> Icons.Rounded.AutoAwesome
                                        ThemeMode.LIGHT -> Icons.Rounded.LightMode
                                        ThemeMode.DARK -> Icons.Rounded.DarkMode
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                }
            }
            item {
                SwitchRow(
                    title = "Use wallpaper colours",
                    subtitle = "Android 12+ system palette. Ignored on older phones.",
                    checked = settings.dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor,
                    icon = Icons.Rounded.Palette,
                )
            }
            item {
                val pickersAvailable = settings.dynamicColor
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Rounded.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Seed colour",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SeedPreset.entries.filter { it != SeedPreset.CUSTOM }.forEach { preset ->
                                SeedSwatch(
                                    preset = preset,
                                    selected = settings.seed == preset && !settings.dynamicColor,
                                    onClick = {
                                        viewModel.setDynamicColor(false)
                                        viewModel.setSeed(preset)
                                    },
                                )
                            }
                            if (settings.customSeedArgb != null) {
                                SeedSwatch(
                                    preset = SeedPreset.CUSTOM,
                                    selected = settings.seed == SeedPreset.CUSTOM && !settings.dynamicColor,
                                    onClick = {
                                        viewModel.setDynamicColor(false)
                                        viewModel.setSeed(SeedPreset.CUSTOM)
                                    },
                                    custom = settings.customSeedArgb,
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                pickImage.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (pickersAvailable) "Pick a photo" else "Pick a photo to seed the theme")
                        }
                        Text(
                            text = "LockIn pulls the dominant colour out of a photo and rebuilds the " +
                                "whole light and dark scheme from it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item { VSpace(6.dp) }
            item { SectionHeader("Doom detector", Modifier.fillMaxWidth()) }
            item {
                SwitchRow(
                    title = "Catch me scrolling",
                    subtitle = "Clock how long you actually spend inside the apps you blocked. " +
                        "Past the limit you get a wall and something to do instead.",
                    checked = settings.doomDetection,
                    onCheckedChange = viewModel::setDoomDetection,
                    icon = Icons.Rounded.LocalFireDepartment,
                )
            }
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = pluralStringResource(
                                    R.plurals.minutes_label,
                                    (settings.doomThresholdMs / 60_000L).toInt(),
                                    settings.doomThresholdMs / 60_000L,
                                ),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Time inside blocked apps before you get caught",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.setDoomThresholdMinutes(
                                            settings.doomThresholdMs / 60_000L - 1,
                                        )
                                    },
                                    enabled = settings.doomDetection &&
                                        settings.doomThresholdMs > 60_000L,
                                ) { Text("−") }
                                OutlinedButton(
                                    onClick = {
                                        viewModel.setDoomThresholdMinutes(
                                            settings.doomThresholdMs / 60_000L + 1,
                                        )
                                    },
                                    enabled = settings.doomDetection &&
                                        settings.doomThresholdMs < 120 * 60_000L,
                                ) { Text("+") }
                            }
                        }
                        Text(
                            text = "Counts continuous time in one app and total time across all " +
                                "of them, so hopping between TikTok and Instagram every " +
                                "minute does not slip through.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                SwitchRow(
                    title = "Goal roulette",
                    subtitle = "A button on the home screen that hands you a random thing to do. " +
                        "For boredom, or coffee, or because you fancy it.",
                    checked = settings.goalRoulette,
                    onCheckedChange = viewModel::setGoalRoulette,
                    icon = Icons.Rounded.Casino,
                )
            }

            item { VSpace(6.dp) }
            item { SectionHeader("Behaviour", Modifier.fillMaxWidth()) }
            item {
                SwitchRow(
                    title = "Vibrate on check-in",
                    subtitle = "So the 15-minute screen grab is felt, not just seen.",
                    checked = settings.vibrateOnCheckIn,
                    onCheckedChange = viewModel::setVibrate,
                    icon = Icons.Rounded.Vibration,
                )
            }
            item {
                SwitchRow(
                    title = "Keep sessions through a reboot",
                    subtitle = "Rebooting does not dissolve a promise.",
                    checked = settings.autoResumeOnBoot,
                    onCheckedChange = viewModel::setAutoResume,
                    icon = Icons.Rounded.RestartAlt,
                )
            }

            item { VSpace(6.dp) }
            item { SectionHeader("Permissions", Modifier.fillMaxWidth()) }
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
                        PermissionRow(
                            title = "Focus guard (accessibility)",
                            body = "Sees which app opens so it can block it. Cannot read screen content.",
                            granted = permissions.accessibilityOn,
                            onFix = { Permissions.openAccessibilitySettings(context) },
                        )
                        PermissionRow(
                            title = "Notifications",
                            body = "Keeps the countdown and the check-in on your lock screen.",
                            granted = permissions.notificationsOn,
                            onFix = { Permissions.openAppNotificationSettings(context) },
                        )
                        PermissionRow(
                            title = "Full-screen notifications",
                            body = "Backup path: shows the check-in if the guard cannot put a screen up.",
                            granted = permissions.fullScreenIntentOn,
                            onFix = { Permissions.openFullScreenIntentSettings(context) },
                        )
                    }
                }
            }

            item { VSpace(6.dp) }
            item { SectionHeader("Data", Modifier.fillMaxWidth()) }
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Rounded.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Everything stays on this phone",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Text(
                            text = "LockIn has no network permission at all. Your blocklist, tasks and " +
                                "history cannot leave the device, and cloud backup is switched off so " +
                                "they cannot be copied to another phone either.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { confirmClear = true }) { Text("Clear session history") }
                    }
                }
            }
        }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear session history?") },
            text = { Text("Every logged session, streak and total is deleted. Your blocklist and settings stay.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory()
                    confirmClear = false
                    snackbar = "History cleared"
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Keep") }
            },
        )
    }
}

private fun SeedSwatchLabel(preset: SeedPreset): String = if (preset == SeedPreset.CUSTOM) "Photo" else preset.label

@Composable
private fun SeedSwatch(
    preset: SeedPreset,
    selected: Boolean,
    onClick: () -> Unit,
    custom: Int? = null,
) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val scheme = if (dark) TonalPalette.darkScheme(preset, custom) else TonalPalette.lightScheme(preset, custom)
    val label = SeedSwatchLabel(preset)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick, role = Role.RadioButton)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(scheme.primary)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label.take(1),
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onPrimary,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    body: String,
    granted: Boolean,
    onFix: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(
                    if (granted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                ),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!granted) {
            TextButton(onClick = onFix) {
                Text("Fix", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
