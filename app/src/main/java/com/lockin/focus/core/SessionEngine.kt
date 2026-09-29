package com.lockin.focus.core

import com.lockin.focus.core.model.Outcome
import com.lockin.focus.core.model.SessionPhase
import com.lockin.focus.core.model.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The whole rulebook of a lock-in, with no Android in sight.
 *
 * A session runs until the user answers a check-in with "done", or breaks out
 * through the deliberately painful escape hatch. There is no timer that quietly
 * expires and no grace period: the apps stay locked for as long as the task
 * takes, and every 15 minutes the user is made to say out loud what they are
 * working on.
 */
class SessionEngine(private val clock: Clock = Clock.Wall) {

    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _lastEnded = MutableStateFlow<EndedSession?>(null)
    val lastEnded: StateFlow<EndedSession?> = _lastEnded.asStateFlow()

    /** Fires once per interval when the check-in becomes due. */
    var onCheckpointDue: ((SessionState) -> Unit)? = null

    /** Fires once when a session leaves [SessionPhase.IDLE]. */
    var onSessionEnd: ((EndedSession) -> Unit)? = null

    data class EndedSession(
        val snapshot: SessionState,
        val outcome: Outcome,
        val endedAt: Long,
    )

    /**
     * @return false when [task] is blank or the session is already running.
     */
    fun start(task: String, intervalMs: Long): Boolean {
        val clean = task.trim()
        if (clean.isEmpty() || _state.value.active) return false
        val now = clock.now()
        val interval = intervalMs.clampIntervalMs()
        _state.value = SessionState(
            phase = SessionPhase.FOCUSED,
            task = clean,
            startedAt = now,
            nextCheckpointAt = now + interval,
            intervalMs = interval,
        )
        return true
    }

    /**
     * Re-adopts a persisted session after a process death or reboot. If the
     * process was gone when the check-in came due, the restored state is already
     * overdue and the next [tick] re-raises it — the user still owes an answer.
     */
    fun restore(state: SessionState) {
        if (!state.active || state.task.isBlank()) return
        _state.value = state
    }

    /** Drives the countdown. Cheap and idempotent; call it as often as you like. */
    fun tick() {
        val current = _state.value
        if (current.phase != SessionPhase.FOCUSED) return
        if (clock.now() < current.nextCheckpointAt) return
        val due = current.copy(phase = SessionPhase.CHECKPOINT, nags = 0)
        _state.value = due
        onCheckpointDue?.invoke(due)
    }

    fun onBlockIntercepted() {
        _state.update { if (it.active) it.copy(blocksIntercepted = it.blocksIntercepted + 1) else it }
    }

    /** "Still on it": the blocklist stays, the next check-in is scheduled. */
    fun acknowledge() {
        val current = _state.value
        if (current.phase != SessionPhase.CHECKPOINT) return
        val now = clock.now()
        _state.value = current.copy(
            phase = SessionPhase.FOCUSED,
            nextCheckpointAt = now + current.intervalMs,
            checkpointsPassed = current.checkpointsPassed + 1,
            nags = 0,
        )
    }

    /** Re-commit to a different primary task without dropping the session. */
    fun retask(task: String): Boolean {
        val clean = task.trim()
        if (clean.isEmpty() || !_state.value.active) return false
        _state.update { it.copy(task = clean, nags = 0) }
        return true
    }

    /** Counted when the user swipes past the check-in without answering. */
    fun nag() {
        _state.update { if (it.owesCheckIn) it.copy(nags = it.nags + 1) else it }
    }

    /**
     * Folds the doom detector's running totals into the session. Taken as a
     * running maximum so a process death mid-session can only lose precision,
     * never let the user bank back time they had already spent.
     */
    fun noteDoomProgress(continuousMs: Long, distractorMs: Long) {
        _state.update { s ->
            if (!s.active) {
                s
            } else {
                s.copy(
                    distractorMs = maxOf(s.distractorMs, distractorMs),
                    longestDoomMs = maxOf(s.longestDoomMs, continuousMs),
                )
            }
        }
    }

    fun recordDoomAlert() {
        _state.update { if (it.active) it.copy(doomAlerts = it.doomAlerts + 1) else it }
    }

    fun completeGoal() {
        _state.update { if (it.active) it.copy(goalsCompleted = it.goalsCompleted + 1) else it }
    }

    fun complete(): EndedSession? = end(Outcome.COMPLETED)

    /** The hold-to-break hatch. Logged as an escape, not a win. */
    fun escape(): EndedSession? = end(Outcome.ESCAPED)

    private fun end(outcome: Outcome): EndedSession? {
        val snapshot = _state.value
        if (!snapshot.active) return null
        val ended = EndedSession(snapshot = snapshot, outcome = outcome, endedAt = clock.now())
        _state.value = SessionState()
        _lastEnded.value = ended
        onSessionEnd?.invoke(ended)
        return ended
    }

    /** Test/diagnostic helper: the in-flight session without a side effect. */
    fun snapshot(): SessionState = _state.value
}
