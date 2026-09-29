package com.lockin.focus.service

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.lockin.focus.LockIn
import com.lockin.focus.ui.BlockActivity
import com.lockin.focus.ui.CheckpointActivity
import com.lockin.focus.ui.GoalActivity

/**
 * Sees which app comes to the front and puts LockIn in front of it.
 *
 * Why an AccessibilityService and not a device-admin app-limiter: it needs no
 * root, no Play Protect bypass, and no special installer. It reads exactly one
 * thing — the package name of the window that changed — and `canRetrieveWindowContent`
 * is false, so it cannot see a single thing on the user's screen.
 *
 * It is also the only component in the app that is allowed to start an activity
 * while something else is in front, which is exactly what a 15-minute
 * "what are you working on" overlay needs.
 */
class AppWatchService : AccessibilityService(), OverlayHost {

    private val main = Handler(Looper.getMainLooper())

    private var blockScreenUp = false
    private var checkpointUp = false
    private var blockedPackage: String? = null

    private var suppressedPackage: String? = null
    private var suppressUntil: Long = 0L

    private var lastCheckpointPresentAt: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        LockIn.registerHost(this)
        Log.i(TAG, "Focus guard connected")
        // A session that was already running (or a check-in that came due while we
        // were off) has to be re-asserted the moment the guard comes back.
        main.post { LockIn.raiseCheckpointIfOwed() }
    }

    override fun onDestroy() {
        LockIn.unregisterHost(this)
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
        val now = SystemClock.elapsedRealtime()

        // The app we just bounced out of: ignore the window change we caused.
        if (pkg == suppressedPackage && now < suppressUntil) return

        val state = LockIn.engine.snapshot()
        if (!state.active) {
            blockScreenUp = false
            checkpointUp = false
            blockedPackage = null
            return
        }
        // The guard is the longest-lived component in the app, so it doubles as a
        // second clock. If the foreground service was killed, the next window
        // change still lands the check-in on time — and still advances the doom
        // detector, which otherwise has nothing driving it.
        LockIn.engine.tick()
        LockIn.tickDoomDetector()
        if (LockIn.engine.snapshot().owesCheckIn) {
            val nowCheck = SystemClock.elapsedRealtime()
            if (nowCheck - lastCheckpointPresentAt >= REASSERT_COOLDOWN_MS) presentCheckpoint()
        }
        if (pkg == packageName) {
            // LockIn's own overlay screens are a legitimate place for the user to
            // be, so they count as somewhere safe to bounce back to. They are
            // deliberately not fed to the doom detector: a takeover on screen is
            // proof the user is not currently doom scrolling.
            LockIn.noteSafePackage(pkg)
            return
        }

        // The dwell clock is fed for every window, blocked or not.
        LockIn.noteForegroundWindow(pkg)

        if (LockIn.blockDecision(pkg)) {
            // A served goal outranks the wall: the goal screen already has the
            // user's attention, and stacking the block screen under it is just
            // noise. Answering the goal clears it and blocking resumes.
            if (LockIn.activeGoal != null) return
            if (blockedPackage != pkg) {
                blockedPackage = pkg
                LockIn.engine.onBlockIntercepted()
            }
            presentBlockScreen(pkg)
        } else {
            if (blockScreenUp || checkpointUp) {
                blockScreenUp = false
                checkpointUp = false
            }
            blockedPackage = null
            LockIn.noteSafePackage(pkg)
            if (state.owesCheckIn) presentCheckpoint()
        }
    }

    override fun presentBlockScreen(blockedPackage: String) {
        if (blockScreenUp && this.blockedPackage == blockedPackage) return
        this.blockedPackage = blockedPackage
        blockScreenUp = true
        suppress(blockedPackage, BOUNCE_SUPPRESS_MS)
        main.post {
            startActivity(
                Intent(this, BlockActivity::class.java)
                    .putExtra(BlockActivity.EXTRA_BLOCKED_PACKAGE, blockedPackage)
                    .addFlags(INTENT_FLAGS),
            )
        }
    }

    override fun presentCheckpoint() {
        // A served goal is the more urgent screen, and the check-in re-asserts
        // itself on a timer — left alone it buried the goal under a question
        // about homework, which is exactly the wrong thing to show someone who
        // has just been caught scrolling. The check-in is still owed and fires
        // as soon as the goal is answered.
        if (LockIn.activeGoal != null) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastCheckpointPresentAt < REASSERT_COOLDOWN_MS) return
        lastCheckpointPresentAt = now
        checkpointUp = true
        main.post {
            runCatching {
                startActivity(
                    Intent(this, CheckpointActivity::class.java).addFlags(INTENT_FLAGS),
                )
            }.onFailure { Log.w(TAG, "Could not raise the check-in", it) }
        }
    }

    override fun presentGoal() {
        main.post {
            runCatching {
                startActivity(
                    Intent(this, GoalActivity::class.java).addFlags(INTENT_FLAGS),
                )
            }.onFailure { Log.w(TAG, "Could not raise the goal", it) }
        }
    }

    override fun returnToSafeApp(blockedPackage: String) {
        suppress(blockedPackage, BOUNCE_SUPPRESS_MS)
        val target = LockIn.lastSafePackage
        val launched = target != null && target != blockedPackage && launchPackage(target)
        if (!launched) goHome()
        Log.i(TAG, "Bounced out of $blockedPackage -> ${target ?: "home"}")
    }

    override fun goHome() {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        runCatching { startActivity(intent) }
            .onFailure { Log.w(TAG, "Could not go home", it) }
    }

    override fun signalHaptic() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        if (!vibrator.hasVibrator()) return
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 180, 90, 180), -1)
        runCatching { vibrator.vibrate(effect) }
    }

    private fun launchPackage(pkg: String): Boolean {
        val intent = runCatching { packageManager.getLaunchIntentForPackage(pkg) }.getOrNull() ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try {
            startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No launchable activity for $pkg", e)
            false
        }
    }

    private fun suppress(pkg: String, durationMs: Long) {
        suppressedPackage = pkg
        suppressUntil = SystemClock.elapsedRealtime() + durationMs
    }

    companion object {
        private const val TAG = "LockIn"
        private const val BOUNCE_SUPPRESS_MS = 2_500L
        private const val REASSERT_COOLDOWN_MS = 1_200L
        private val INTENT_FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP

        @Volatile
        private var instance: AppWatchService? = null

        fun isRunning(): Boolean = instance != null
    }
}
