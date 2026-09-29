package com.lockin.focus.ui.screens

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lockin.focus.core.model.FocusSettings
import com.lockin.focus.data.AppGroup
import com.lockin.focus.ui.MainViewModel
import com.lockin.focus.ui.components.AppRow
import com.lockin.focus.ui.components.EmptyState
import com.lockin.focus.ui.components.SectionHeader
import com.lockin.focus.ui.theme.Motion
import com.lockin.focus.util.GuardPermissions
import com.lockin.focus.util.Permissions

private const val MAX_TASK_LENGTH = 80

private data class OnboardStep(
    val title: String,
    val body: String,
    val icon: ImageVector,
)

private val ONBOARD_STEPS = listOf(
    OnboardStep(
        title = "Name the one thing",
        body = "LockIn locks your apps until you say the task is done. Pick the smallest " +
            "thing you can actually finish — \"chapter 4 problems\", not \"maths\".",
        icon = Icons.Rounded.Lock,
    ),
    OnboardStep(
        title = "Choose what gets locked",
        body = "TikTok, YouTube, the group chat. Calls, messages, the clock and your launcher " +
            "always stay open, so a focus timer never becomes an emergency.",
        icon = Icons.Rounded.Shield,
    ),
    OnboardStep(
        title = "Switch on the focus guard",
        body = "This is the only permission LockIn asks for. It reads the name of the app that " +
            "opens — nothing else, and it cannot see your screen. Android will not let the " +
            "app block anything without it.",
        icon = Icons.Rounded.Warning,
    ),
    OnboardStep(
        title = "Every 15 minutes it interrupts",
        body = "LockIn takes the screen and asks what you are working on. Answer it and the " +
            "clock restarts. Ignore it and it comes straight back — that is the entire point " +
            "of the thing.",
        icon = Icons.Rounded.Notifications,
    ),
)

@Composable
fun OnboardingScreen(
    settings: FocusSettings,
    permissions: GuardPermissions,
    viewModel: MainViewModel,
) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    var task by remember { mutableStateOf("") }
    val isLast = step == ONBOARD_STEPS.lastIndex

    val requestNotifications = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.markOnboarded() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // heightIn(min = viewport) means the column is exactly one screen tall
            // when the step is short: SpaceBetween pins the headline to the top and
            // the buttons to the bottom. When the step is a long blocklist the
            // column grows past the viewport and scrolls instead.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "LockIn",
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "An app blocker that will not let you off the hook.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    StepCard(step)

                    when (step) {
                        0 -> OutlinedTextField(
                            value = task,
                            onValueChange = { if (it.length <= MAX_TASK_LENGTH) task = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Your primary task") },
                            placeholder = { Text("Primary study — chapter 4 problems") },
                            minLines = 2,
                            shape = MaterialTheme.shapes.large,
                            supportingText = { Text("${task.trim().length}/$MAX_TASK_LENGTH") },
                        )

                        1 -> BlocklistPickerStep(settings, viewModel)

                        2 -> PermissionStep(
                            granted = permissions.accessibilityOn,
                            grantedLabel = "Focus guard is on",
                            pendingLabel = "Open accessibility settings",
                            body = "Settings → Accessibility → Installed services → " +
                                "\"LockIn focus guard\" → On. LockIn cannot see which app is " +
                                "opening without this, so it cannot block anything.",
                            onAction = { Permissions.openAccessibilitySettings(context) },
                        )

                        else -> LastStep(
                            settings = settings,
                            permissions = permissions,
                            requestNotifications = {
                                requestNotifications.launch(Permissions.notificationPermission())
                            },
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ONBOARD_STEPS.indices.forEach { index ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(4.dp)
                                    .background(
                                        if (index <= step) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                        },
                                        CircleShape,
                                    ),
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (step > 0) {
                            OutlinedButton(
                                onClick = { step-- },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp),
                                shape = MaterialTheme.shapes.large,
                            ) { Text("Back") }
                        }
                        Button(
                            onClick = {
                                if (isLast) {
                                    viewModel.markOnboarded()
                                    viewModel.startSession(task)
                                } else {
                                    step++
                                }
                            },
                            enabled = step != 0 || task.isNotBlank(),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            shape = MaterialTheme.shapes.large,
                        ) {
                            Text(if (isLast) "Lock in" else "Next")
                        }
                    }

                    TextButton(
                        onClick = { viewModel.markOnboarded() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Skip setup — I will do it myself", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun StepCard(step: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    ONBOARD_STEPS[step].icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    text = "${step + 1} of ${ONBOARD_STEPS.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = targetState > initialState
                    val direction = if (forward) 1 else -1
                    (
                        slideInHorizontally(
                            animationSpec = tween(Motion.DURATION_MEDIUM, easing = Motion.EmphasizedDecelerate),
                            initialOffsetX = { it / 4 * direction },
                        ) + fadeIn(tween(Motion.DURATION_MEDIUM, easing = Motion.Emphasized))
                        ) togetherWith (
                        slideOutHorizontally(
                            animationSpec = tween(Motion.DURATION_MEDIUM, easing = Motion.EmphasizedAccelerate),
                            targetOffsetX = { -it / 4 * direction },
                        ) + fadeOut(tween(Motion.DURATION_SHORT, easing = Motion.Emphasized))
                        )
                },
                label = "onboardStep",
            ) { index ->
                val content = ONBOARD_STEPS[index]
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = content.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = content.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LastStep(
    settings: FocusSettings,
    permissions: GuardPermissions,
    requestNotifications: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "One last thing",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionStep(
                granted = permissions.notificationsOn,
                grantedLabel = "Notifications on",
                pendingLabel = "Allow notifications",
                body = "The countdown lives in a notification, and a check-in can arrive as " +
                    "a full-screen notification if the guard is ever blocked by the system.",
                onAction = requestNotifications,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            Text(
                text = "Every " + settings.intervalMinutes + " minutes, LockIn takes the screen " +
                    "and asks what you are working on.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BlocklistPickerStep(settings: FocusSettings, viewModel: MainViewModel) {
    when {
        viewModel.appsLoading -> Text(
            text = "Reading your apps…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        viewModel.installedApps.isEmpty() -> EmptyState(
            icon = Icons.Rounded.Shield,
            title = "No apps found",
            body = "Android would not let LockIn list your apps. You can build the blocklist " +
                "later from the Blocklist tab.",
        )

        else -> {
            val picks = remember(viewModel.installedApps) {
                viewModel.installedApps.filter { it.group != AppGroup.OTHER }.take(8)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(
                    text = if (picks.isEmpty()) "Nothing obvious installed" else "Start with these",
                    modifier = Modifier.fillMaxWidth(),
                )
                if (picks.isEmpty()) {
                    Text(
                        text = "None of the usual suspects are on this phone. Pick anything you " +
                            "like from the Blocklist tab.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                picks.forEach { app ->
                    AppRow(
                        app = app,
                        blocked = app.packageName in settings.blocked,
                        onToggle = {
                            viewModel.setBlocked(app.packageName, app.packageName !in settings.blocked)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionStep(
    granted: Boolean,
    grantedLabel: String,
    pendingLabel: String,
    body: String,
    onAction: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (granted) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Text(
                    text = grantedLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        } else {
            Button(
                onClick = onAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.large,
            ) { Text(pendingLabel) }
        }
    }
}
