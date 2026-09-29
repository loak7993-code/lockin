package com.lockin.focus

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.util.Log
import com.lockin.focus.core.AccessPolicy
import com.lockin.focus.core.Clock
import com.lockin.focus.core.DayClock
import com.lockin.focus.core.DoomDetector
import com.lockin.focus.core.GoalDeck
import com.lockin.focus.core.SessionEngine
import com.lockin.focus.core.model.DEFAULT_DOOM_THRESHOLD_MS
import com.lockin.focus.core.model.DayStat
import com.lockin.focus.core.model.DoomEvent
import com.lockin.focus.core.model.FocusSettings
import com.lockin.focus.core.model.Goal
import com.lockin.focus.core.model.Outcome
import com.lockin.focus.core.model.SessionRecord
import com.lockin.focus.core.model.SessionState
import com.lockin.focus.data.AppCatalog
import com.lockin.focus.data.Distractors
import com.lockin.focus.data.SettingsStore
import com.lockin.focus.service.CheckpointAlarm
import com.lockin.focus.service.FocusGuardService
import com.lockin.focus.service.OverlayHost
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LockInApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LockIn.install(this)
    }
}

/**
 * Hand-rolled object graph. The app has one process, one scope and one state
 * machine; a DI framework would be more moving parts than the app.
 *
 * The static-Context warning is expected and correct: the only Context ever held
 * here is the application context, which outlives every screen by design.
 */
@SuppressLint("StaticFieldLeak")
object LockIn {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var appContext: Context
    private lateinit var store: SettingsStore
    private lateinit var engineRef: SessionEngine

    lateinit var engine: SessionEngine
        private set

    lateinit var settingsFlow: StateFlow<FocusSettings>
        private set

    lateinit var historyFlow: StateFlow<List<SessionRecord>>
        private set

    @Volatile
    var host: OverlayHost? = null

    /** The last app the user was allowed to be in. Where the block screen bounces them back to. */
    @Volatile
    var lastSafePackage: String? = null
        private set

    fun noteSafePackage(pkg: String) {
        lastSafePackage = pkg
    }

    @Volatile
    var launcherPackages: Set<String> = emptySet()
        private set

    @Volatile
    var accessibilityConnected: Boolean = false
        private set

    /**
     * How long the user has actually been inside the apps they asked to block.
     * Created here so it outlives the service that drives it.
     */
    var doomDetector: DoomDetector = DoomDetector(DEFAULT_DOOM_THRESHOLD_MS)
        private set

    /** The goal currently being served, and what it was served for. */
    @Volatile
    var activeGoal: Goal? = null
        private set

    @Volatile
    var lastDoomEvent: DoomEvent? = null
        private set

    private val labelCache = HashMap<String, String>()

    fun install(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        store = SettingsStore(appContext)
        engineRef = SessionEngine(Clock.Wall)
        engine = engineRef

        settingsFlow = store.settings.stateIn(scope, SharingStarted.Eagerly, FocusSettings.DEFAULT)
        historyFlow = store.history.stateIn(scope, SharingStarted.Eagerly, emptyList())
        launcherPackages = resolveHomePackages(appContext)

        engineRef.onSessionEnd = ::onSessionEnded
        // The countdown firing is the only thing that can raise a check-in, because
        // no window changes when the user sits in the same app the whole time.
        engineRef.onCheckpointDue = { raiseCheckpointIfOwed() }

        scope.launch {
            // The live detector follows the stored setting for as long as the
            // process lives. Doing this from the settings flow rather than from
            // the write path is what makes it survive a restart: a detector built
            // with the default threshold ignored the user's choice until they
            // happened to change the setting again.
            settingsFlow.collect { applyDoomThreshold(it.doomThresholdMs) }
        }

        scope.launch {
            val persisted = store.currentSession()
            if (persisted != null && !engineRef.snapshot().active) {
                engineRef.restore(persisted)
                Log.i(TAG, "Restored active session '${persisted.task}' after process start")
            }
            engineRef.state.collect { state ->
                store.saveSession(state)
                // Armed from here, not from the guard service. This collector lives
                // in Application.onCreate, so it runs whenever the process does —
                // and the alarm it arms is held by the system, so it survives the
                // process not running at all. Arming from the service meant a
                // killed service silently disarmed the timer.
                if (state.active && !state.owesCheckIn) {
                    CheckpointAlarm.arm(appContext, state.nextCheckpointAt, state.intervalMs)
                } else {
                    CheckpointAlarm.cancel(appContext)
                }
                // Same reasoning for the guard itself. A session that outlives its
                // process — killed under memory pressure, or by installing an
                // update over the top of it — used to come back with the countdown
                // alarm armed and nothing behind it to tick the engine or the doom
                // detector. The alarm alone keeps the check-in honest; the service
                // is what makes the wall, the bounce and the dwell clock work.
                if (state.active) startGuardService()
            }
        }
    }

    fun registerHost(overlayHost: OverlayHost) {
        host = overlayHost
        accessibilityConnected = true
        val state = engine.snapshot()
        if (state.active) {
            // A check-in that came due while the guard was off is still owed.
            scope.launch { engine.tick() }
        }
    }

    fun unregisterHost(overlayHost: OverlayHost) {
        if (host === overlayHost) {
            host = null
            accessibilityConnected = false
        }
    }

    fun startSession(task: String, intervalMs: Long) {
        if (!engine.start(task, intervalMs)) return
        startGuardService()
        raiseCheckpointIfOwed()
    }

    fun completeSession() {
        engine.complete()
        stopGuardService()
    }

    fun escapeSession() {
        activeGoal = null
        engine.escape()
        stopGuardService()
    }

    fun refreshHomePackages() {
        launcherPackages = resolveHomePackages(appContext)
    }

    suspend fun currentSession(): SessionState? = store.currentSession()

    suspend fun currentSettings(): FocusSettings = store.currentSettings()

    fun updateSettings(transform: (FocusSettings) -> FocusSettings) {
        scope.launch { store.updateSettings(transform) }
    }

    fun clearHistory() {
        scope.launch { store.clearHistory() }
    }

    /** Re-adopts a persisted session and brings the guard back up (used after boot). */
    fun restoreSession(session: SessionState) {
        if (engine.snapshot().active) return
        engine.restore(session)
        startGuardService()
    }

    /**
     * Tells the doom detector which app is in front.
     *
     * This is the only seam the accessibility service needs: the block/allow
     * decision stays in the service because it owns the bounce, but the dwell
     * clock has to be fed for *every* window, blocked or not. Routing the two
     * through one path is deliberate — when the detector was only updated from
     * the blocked branch it could never accumulate, because blocked apps get
     * walled within a second and never produced the fifteen minutes it needed.
     *
     * LockIn's own windows are filtered by the caller: they are not somewhere the
     * user is doom scrolling, and letting them bank would let a takeover screen
     * keep the clock running.
     */
    fun noteForegroundWindow(pkg: String) {
        doomDetector.current(pkg, labelFor(pkg), System.currentTimeMillis())
    }

    /**
     * Does the doom detector clock time in this app?
     *
     * Blocked apps alone would be pointless to watch: the wall is up within a
     * second, so the counter could never reach anything. What actually happens in
     * the wild is that people leave TikTok or YouTube *off* the blocklist because
     * they need them for something, and then fall into them anyway. So the
     * detector watches those too — installed, not blocked, and a known time sink.
     * Fifteen minutes in, the goal screen is the wall.
     */
    fun doomWatches(pkg: String): Boolean =
        blockDecision(pkg) || pkg in knownDistractors

    private val knownDistractors: Set<String> by lazy {
        Distractors.KNOWN.mapTo(HashSet()) { it.first }
    }

    /**
     * Advances the dwell clock. Driven by the guard service's ticker, because a
     * user staring at one app produces no window events at all for fifteen
     * minutes — which is exactly the case worth catching.
     */
    fun tickDoomDetector() {
        val state = engine.snapshot()
        if (!state.active) return
        val settings = settingsFlow.value
        if (!settings.doomDetection) return

        val pkg = doomDetector.currentPackage() ?: return
        if (!doomWatches(pkg)) return

        val now = System.currentTimeMillis()
        val result = doomDetector.tick(pkg, doomDetector.currentLabel(), now)
        // Always report the running total, not whatever the trigger happened to
        // carry: the "time in apps" stat is the whole point of showing it.
        engine.noteDoomProgress(doomDetector.currentDwellMs(now), doomDetector.totalMs(now))
        val trigger = result?.trigger ?: return

        val label = labelFor(pkg)
        engine.recordDoomAlert()
        lastDoomEvent = DoomEvent(
            appLabel = label,
            appPackage = pkg,
            trigger = trigger,
            millisInBlockedApps = result.distractorMs,
            at = now,
        )
        activeGoal = GoalDeck.draw()
        Log.i(TAG, "Doom detector tripped: $trigger in $label after ${result.distractorMs / 1000}s")
        host?.presentGoal()
    }

    fun rerollGoal() {
        val current = activeGoal
        activeGoal = if (current == null) GoalDeck.draw() else GoalDeck.drawOtherThan(current)
    }

    fun goalServed() {
        engine.completeGoal()
        doomDetector.rearm(System.currentTimeMillis())
        activeGoal = null
    }

    private fun labelFor(pkg: String): String = labelCache.getOrPut(pkg) {
        runCatching { AppCatalog.labelOf(appContext, pkg) }.getOrDefault(pkg)
    }

    /** Applies a new doom threshold to the live detector without losing its state. */
    fun applyDoomThreshold(thresholdMs: Long) {
        if (doomDetector.thresholdMs == thresholdMs) return
        doomDetector = doomDetector.withThreshold(thresholdMs)
    }

    /**
     * Second path to the check-in: the guard's countdown fired while the user was
     * sitting on the same app the whole time, so no window change will ever happen.
     */
    fun raiseCheckpointIfOwed() {
        val state = engine.snapshot()
        if (state.owesCheckIn) host?.presentCheckpoint()
    }

    fun blockDecision(pkg: String): Boolean {
        val state = engine.snapshot()
        if (!state.active) return false
        return AccessPolicy.evaluate(pkg, settingsFlow.value, appContext.packageName, launcherPackages) is
            AccessPolicy.Decision.Block
    }

    private fun onSessionEnded(ended: SessionEngine.EndedSession) {
        scope.launch {
            store.addRecord(
                SessionRecord(
                    id = UUID.randomUUID().toString(),
                    task = ended.snapshot.task,
                    startedAt = ended.snapshot.startedAt,
                    endedAt = ended.endedAt,
                    outcome = ended.outcome,
                    blocksIntercepted = ended.snapshot.blocksIntercepted,
                    checkpointsPassed = ended.snapshot.checkpointsPassed,
                ),
            )
        }
    }

    fun startGuardService() {
        try {
            appContext.startForegroundService(Intent(appContext, FocusGuardService::class.java))
        } catch (e: IllegalStateException) {
            // Background start restrictions: the session still runs and the alarm
            // still fires, the live ticker just waits for the next foreground
            // attempt.
            Log.w(TAG, "Could not start guard service in the background", e)
        }
    }

    fun stopGuardService() {
        appContext.stopService(Intent(appContext, FocusGuardService::class.java))
    }

    /** Rollup used by the history screen. Oldest day first. */
    fun computeDayStats(history: List<SessionRecord>, now: Long, days: Int): List<DayStat> {
        val today = DayClock.startOfDay(now)
        val buckets = ArrayDeque<DayStat>()
        for (offset in days - 1 downTo 0) {
            buckets.addLast(DayStat(today - offset * DayClock.DAY_MS, 0L, 0, 0))
        }
        for (record in history) {
            val index = (DayClock.daysBetween(record.startedAt, now)).let { days - 1 - it }
            val bucket = buckets.elementAtOrNull(index) ?: continue
            buckets.removeAt(index)
            buckets.add(
                index,
                bucket.copy(
                    focusedMs = bucket.focusedMs + record.durationMs,
                    sessions = bucket.sessions + 1,
                    escapes = bucket.escapes + if (record.outcome == Outcome.ESCAPED) 1 else 0,
                ),
            )
        }
        return buckets.toList()
    }

    private fun resolveHomePackages(context: Context): Set<String> {
        val pm = context.packageManager
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return try {
            val resolved: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(homeIntent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(homeIntent, 0)
            }
            resolved.mapNotNull { it.activityInfo?.packageName }.toSet()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not resolve the home app", e)
            emptySet()
        }
    }

    const val TAG = "LockIn"
}
