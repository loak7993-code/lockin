package com.lockin.focus.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lockin.focus.LockIn
import com.lockin.focus.core.model.FocusSettings
import com.lockin.focus.core.model.SessionPhase
import com.lockin.focus.ui.theme.LockInTheme

/**
 * Base for the two full-screen takeovers.
 *
 * Both are their own task, excluded from recents, and shown over the lock screen
 * so the check-in still lands when the phone is face-down on a desk. System bars
 * are hidden but reachable with a swipe, and the screen is held awake: a blocker
 * that times out because you put the phone down is not a blocker.
 */
abstract class TakeoverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
        setContent { TakeoverContent { finishTakeover() } }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    @Composable
    private fun TakeoverContent(onFinish: () -> Unit) {
        val settings by LockIn.settingsFlow.collectAsStateWithLifecycle()
        val state by LockIn.engine.state.collectAsStateWithLifecycle()
        LockInTheme(
            themeMode = settings.themeMode,
            seed = settings.seed,
            customSeed = settings.customSeedArgb,
            dynamicColor = false,
        ) {
            TakeoverBody(
                state = state,
                settings = settings,
                onFinish = onFinish,
            )
        }
    }

    @Composable
    protected abstract fun TakeoverBody(
        state: com.lockin.focus.core.model.SessionState,
        settings: FocusSettings,
        onFinish: () -> Unit,
    )

    /**
     * Closes the takeover and takes its whole task with it.
     *
     * Plain [finish] would leave the blocked app's task sitting underneath, the
     * system would surface it, and the guard would immediately block it again —
     * a loop the user cannot get out of by tapping the right button. Removing the
     * task means the app we bounced to is genuinely what is on screen.
     */
    protected fun finishTakeover() {
        finishAndRemoveTask()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun showOverLockScreen() {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    /** True once the session is no longer something this screen should be showing. */
    protected fun shouldSelfClose(state: com.lockin.focus.core.model.SessionState): Boolean =
        !state.active || state.phase == SessionPhase.IDLE
}
