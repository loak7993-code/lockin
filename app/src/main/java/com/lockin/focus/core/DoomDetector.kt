package com.lockin.focus.core

import com.lockin.focus.core.model.DoomTrigger

/**
 * Watches how long the user is actually inside the apps they told us to block,
 * and inside the well-known time sinks they left off the list because they need
 * them for something.
 *
 * Two ways to get caught, because they are different behaviours and one rule
 * catches neither on its own:
 *
 * - **Continuous** — parked in one app, not leaving. The classic doom scroll.
 * - **Cumulative** — fifteen minutes' worth of those apps, hopping between
 *   TikTok and Instagram every ninety seconds. Never "stayed" anywhere long
 *   enough to trip a dwell timer.
 *
 * Deliberately driven by window changes rather than scroll events: it needs no
 * extra permission, it keeps `canRetrieveWindowContent="false"` honest, and
 * "did not leave this app for fifteen minutes" is a stronger signal than "this
 * app emits scroll events" anyway — one of those is a fact about the user, the
 * other is an accident of implementation.
 */
class DoomDetector(
    val thresholdMs: Long,
    /** Dwell shorter than this counts towards the cumulative total but cannot fire it alone. */
    private val minContinuousMs: Long = 60_000L,
) {

    data class Result(
        val trigger: DoomTrigger?,
        val continuousMs: Long,
        /** Time since the last rearm, not the session total. See [totalMs]. */
        val distractorMs: Long,
    )

    private var currentPackage: String? = null
    private var currentLabel: String = ""
    private var enteredAt: Long = 0L
    private var continuousMs: Long = 0L

    /**
     * Monotonic for the life of the detector. This is the number the session
     * reports as "time spent in blocked apps", so it is never rebased — a stat
     * that resets every time you answer a goal is not a stat.
     */
    private var distractorMs: Long = 0L

    /**
     * Where [distractorMs] stood at the last [rearm]. The cumulative rule fires
     * on `distractorMs - baseline`, so answering a goal requires a fresh full
     * stretch of scrolling to trip again without refunding a single second of the
     * running total.
     */
    private var fireBaselineMs: Long = 0L
    private var armed: Boolean = true

    /** Non-null only on the tick where a threshold is first crossed. */
    fun current(appPackage: String?, appLabel: String, now: Long): Result? {
        if (appPackage == currentPackage) return null

        bank(now)

        currentPackage = appPackage
        currentLabel = appLabel
        enteredAt = now
        continuousMs = 0L
        armed = true
        return null
    }

    /**
     * Drives the dwell clock. Call it periodically — the guard service's ticker is
     * the natural place. Returns a trigger on the first crossing only, so a user
     * who leaves the app does not get the goal screen again ten seconds later.
     */
    fun tick(appPackage: String?, appLabel: String, now: Long): Result? {
        if (appPackage != currentPackage) return current(appPackage, appLabel, now)

        val delta = (now - enteredAt).coerceAtLeast(0L)
        continuousMs += delta
        enteredAt = now
        if (appPackage != null) distractorMs += delta

        if (!armed) return null
        val sinceFire = distractorMs - fireBaselineMs
        if (continuousMs >= thresholdMs) {
            armed = false
            return Result(DoomTrigger.CONTINUOUS, continuousMs, sinceFire)
        }
        if (sinceFire >= thresholdMs && continuousMs >= minContinuousMs) {
            armed = false
            return Result(DoomTrigger.CUMULATIVE, continuousMs, sinceFire)
        }
        return null
    }

    /**
     * Banks the time owed to the app being left. Kept separate from [tick] so a
     * switch that happens between ticks is not lost — the common case, because
     * app switches generate window events and timeouts do not.
     */
    private fun bank(now: Long) {
        if (currentPackage == null) return
        val delta = (now - enteredAt).coerceAtLeast(0L)
        continuousMs += delta
        distractorMs += delta
    }

    fun currentDwellMs(now: Long): Long =
        if (currentPackage == null) 0L else continuousMs + (now - enteredAt).coerceAtLeast(0L)

    /**
     * Everything clocked since the detector was created, plus the slice since the
     * last tick. For reporting, never rebased — a stat that resets every time you
     * answer a goal is not a stat.
     */
    fun totalMs(now: Long): Long =
        distractorMs + if (currentPackage != null) (now - enteredAt).coerceAtLeast(0L) else 0L

    fun currentPackage(): String? = currentPackage

    fun currentLabel(): String = currentLabel

    fun reset() {
        currentPackage = null
        currentLabel = ""
        enteredAt = 0L
        continuousMs = 0L
        distractorMs = 0L
        fireBaselineMs = 0L
        armed = true
    }

    /**
     * A copy running on a different threshold but carrying the same history.
     *
     * Nudging the threshold must not hand back time already served — a user who
     * has burned ten minutes into a scroller and then widens the limit from
     * fifteen to twenty keeps those ten, it does not start again from zero.
     */
    fun withThreshold(thresholdMs: Long): DoomDetector {
        val copy = DoomDetector(thresholdMs, minContinuousMs)
        copy.currentPackage = currentPackage
        copy.currentLabel = currentLabel
        copy.enteredAt = enteredAt
        copy.continuousMs = continuousMs
        copy.distractorMs = distractorMs
        copy.fireBaselineMs = fireBaselineMs
        copy.armed = armed
        return copy
    }

    /**
     * Called when the user answers the goal. Both clocks are rebased to *now* —
     * the trigger re-armed, and the in-progress stretch restarted — because
     * otherwise the dwell total is still sitting past the threshold and the goal
     * screen comes straight back the instant they open the app again.
     *
     * The session total in [totalMs] is deliberately left alone: that is history,
     * not a countdown.
     */
    fun rearm(now: Long) {
        bank(now)
        enteredAt = now
        continuousMs = 0L
        fireBaselineMs = distractorMs
        armed = true
    }
}
