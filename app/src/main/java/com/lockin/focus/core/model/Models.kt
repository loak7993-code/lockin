package com.lockin.focus.core.model

import kotlinx.serialization.Serializable

/** Default check-in cadence. The whole point of the app: 15 minutes, then you answer for yourself. */
const val DEFAULT_INTERVAL_MINUTES: Long = 15L

const val MIN_INTERVAL_MINUTES: Long = 1L
const val MAX_INTERVAL_MINUTES: Long = 180L

/** Fifteen minutes inside a blocked app is the line between "checking" and "doom". */
const val DEFAULT_DOOM_THRESHOLD_MS: Long = 15L * 60_000L
const val MIN_DOOM_THRESHOLD_MINUTES: Long = 1L
const val MAX_DOOM_THRESHOLD_MINUTES: Long = 120L

val PRESET_INTERVAL_MINUTES: List<Long> = listOf(5L, 15L, 25L, 45L, 60L)

@Serializable
enum class SessionPhase {
    /** No session. Nothing is blocked. */
    IDLE,

    /** A session is running and the blocklist is being enforced. */
    FOCUSED,

    /** The 15-minute check-in is due and the user has not answered it yet. */
    CHECKPOINT,
}

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Built-in seed palettes. [CUSTOM] uses [FocusSettings.customSeedArgb]. */
@Serializable
enum class SeedPreset(val label: String) {
    LOCKDOWN("Lockdown"),
    EMBER("Ember"),
    ABYSS("Abyss"),
    MOSS("Moss"),
    CUSTOM("Custom"),
}

@Serializable
enum class Outcome { COMPLETED, ESCAPED }

@Serializable
data class SessionState(
    val phase: SessionPhase = SessionPhase.IDLE,
    val task: String = "",
    val startedAt: Long = 0L,
    val nextCheckpointAt: Long = 0L,
    val intervalMs: Long = DEFAULT_INTERVAL_MINUTES * 60_000L,
    val blocksIntercepted: Int = 0,
    val checkpointsPassed: Int = 0,
    /** How many times the user swiped the check-in away without answering. */
    val nags: Int = 0,
    /** Total milliseconds spent inside blocked apps this session. Feeds the doom detector. */
    val distractorMs: Long = 0L,
    /** Longest single unbroken stretch spent inside one blocked app, in ms. */
    val longestDoomMs: Long = 0L,
    val doomAlerts: Int = 0,
    val goalsCompleted: Int = 0,
) {
    val active: Boolean get() = phase != SessionPhase.IDLE

    /** True once the user has a check-in they still owe an answer to. */
    val owesCheckIn: Boolean get() = phase == SessionPhase.CHECKPOINT

    fun elapsedMs(now: Long): Long =
        if (active) (now - startedAt).coerceAtLeast(0L) else 0L

    fun remainingMs(now: Long): Long =
        if (active) (nextCheckpointAt - now).coerceAtLeast(0L) else 0L

    /** 0f at the start of the current interval, 1f when the check-in fires. */
    fun intervalProgress(now: Long): Float {
        if (!active || intervalMs <= 0L) return 0f
        val sinceStartOfInterval = now - (nextCheckpointAt - intervalMs)
        return (sinceStartOfInterval.toFloat() / intervalMs.toFloat()).coerceIn(0f, 1f)
    }
}

@Serializable
data class FocusSettings(
    val intervalMs: Long = DEFAULT_INTERVAL_MINUTES * 60_000L,
    val blocked: Set<String> = emptySet(),
    /** User-added always-allowed packages on top of [AccessPolicy.SYSTEM_ALWAYS_ALLOWED]. */
    val allowed: Set<String> = emptySet(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val seed: SeedPreset = SeedPreset.LOCKDOWN,
    val customSeedArgb: Int? = null,
    val dynamicColor: Boolean = true,
    val vibrateOnCheckIn: Boolean = true,
    val autoResumeOnBoot: Boolean = true,
    val onboarded: Boolean = false,
    /** Catch the user after this much time inside blocked apps and hand them a task. */
    val doomDetection: Boolean = true,
    val doomThresholdMs: Long = DEFAULT_DOOM_THRESHOLD_MS,
    /** Offer one-off goals outside the check-in rhythm (boredom, coffee, whatever). */
    val goalRoulette: Boolean = true,
) {
    val intervalMinutes: Long get() = intervalMs / 60_000L

    companion object {
        val DEFAULT = FocusSettings()
    }
}

@Serializable
data class SessionRecord(
    val id: String,
    val task: String,
    val startedAt: Long,
    val endedAt: Long,
    val outcome: Outcome,
    val blocksIntercepted: Int,
    val checkpointsPassed: Int,
) {
    val durationMs: Long get() = (endedAt - startedAt).coerceAtLeast(0L)
    val durationMinutes: Long get() = durationMs / 60_000L
}

data class DayStat(val dayStart: Long, val focusedMs: Long, val sessions: Int, val escapes: Int)

/** Why the doom detector fired. Both are real patterns and they feel different. */
enum class DoomTrigger(val blurb: String) {
    /** Fifteen minutes in one blocked app without ever leaving it. */
    CONTINUOUS("You stayed in one app the whole time."),

    /** Fifteen minutes of blocked apps in total, hopping between them. */
    CUMULATIVE("You spent the time in there, hopping around."),
}

@Serializable
data class DoomEvent(
    val appLabel: String,
    val appPackage: String,
    val trigger: DoomTrigger,
    val millisInBlockedApps: Long,
    val at: Long,
)

/** One thing to do instead of scrolling. The whole point of the doom screen. */
@Serializable
data class Goal(
    val text: String,
    val category: GoalCategory,
    val minutes: Int,
)

@Serializable
enum class GoalCategory(val label: String) {
    STUDY("Study"),
    HOMEWORK("Homework"),
    ENGLISH("English"),
    RUSSIAN("Russian"),
    MOVE("Move"),
    BODY("Body"),
    CHORES("Chores"),
    OFFLINE("Offline"),
}
