package com.lockin.focus.core

import com.lockin.focus.core.model.Outcome
import com.lockin.focus.core.model.SessionPhase
import com.lockin.focus.core.model.SessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionEngineTest {

    private var now = 1_000_000L
    private val engine = SessionEngine(Clock { now })

    private val fifteenMinutes = 15L * 60_000L

    @Test
    fun `start refuses a blank task`() {
        assertFalse(engine.start("   ", fifteenMinutes))
        assertEquals(SessionPhase.IDLE, engine.snapshot().phase)
    }

    @Test
    fun `start refuses to double up a session`() {
        assertTrue(engine.start("Study", fifteenMinutes))
        assertFalse(engine.start("Something else", fifteenMinutes))
        assertEquals("Study", engine.snapshot().task)
    }

    @Test
    fun `session starts focused with the first check-in one interval out`() {
        engine.start("  Primary study  ", fifteenMinutes)
        val state = engine.snapshot()
        assertEquals(SessionPhase.FOCUSED, state.phase)
        assertEquals("Primary study", state.task)
        assertEquals(now + fifteenMinutes, state.nextCheckpointAt)
        assertEquals(fifteenMinutes, state.intervalMs)
    }

    @Test
    fun `nothing happens before the interval elapses`() {
        engine.start("Study", fifteenMinutes)
        now += fifteenMinutes - 1
        engine.tick()
        assertEquals(SessionPhase.FOCUSED, engine.snapshot().phase)
    }

    @Test
    fun `the check-in fires exactly at the interval`() {
        var fired = 0
        engine.onCheckpointDue = { fired++ }
        engine.start("Study", fifteenMinutes)

        now += fifteenMinutes - 1
        engine.tick()
        now += 1
        engine.tick()
        assertEquals(SessionPhase.CHECKPOINT, engine.snapshot().phase)
        assertEquals(1, fired)

        // Ticking again while a check-in is owed must not spam the overlay.
        repeat(10) { engine.tick() }
        assertEquals(1, fired)
    }

    @Test
    fun `answering the check-in keeps the blocklist on and schedules the next one`() {
        engine.start("Study", fifteenMinutes)
        now += fifteenMinutes
        engine.tick()
        engine.acknowledge()

        val state = engine.snapshot()
        assertEquals(SessionPhase.FOCUSED, state.phase)
        assertTrue("the session must not end just because a check-in was answered", state.active)
        assertEquals(1, state.checkpointsPassed)
        assertEquals(now + fifteenMinutes, state.nextCheckpointAt)
        assertEquals(0, state.nags)
    }

    @Test
    fun `dodging a check-in is counted and stays owed`() {
        engine.start("Study", fifteenMinutes)
        now += fifteenMinutes
        engine.tick()
        engine.nag()
        engine.nag()
        engine.nag()

        val state = engine.snapshot()
        assertTrue(state.owesCheckIn)
        assertEquals(3, state.nags)
        engine.acknowledge()
        assertEquals(0, engine.snapshot().nags)
    }

    @Test
    fun `blocked attempts only count while a session is live`() {
        engine.start("Study", fifteenMinutes)
        repeat(4) { engine.onBlockIntercepted() }
        assertEquals(4, engine.snapshot().blocksIntercepted)

        engine.complete()
        engine.onBlockIntercepted()
        assertEquals(0, engine.snapshot().blocksIntercepted)
    }

    @Test
    fun `completing ends the session and logs it as done`() {
        engine.start("Study", fifteenMinutes)
        now += 45 * 60_000L
        val ended = engine.complete()

        assertNotNull(ended)
        assertEquals(Outcome.COMPLETED, ended!!.outcome)
        assertEquals(45 * 60_000L, ended.snapshot.elapsedMs(now))
        assertEquals(SessionPhase.IDLE, engine.snapshot().phase)
        assertFalse(engine.snapshot().active)
        assertNull(engine.complete())
    }

    @Test
    fun `the escape hatch is logged separately from finishing`() {
        engine.start("Study", fifteenMinutes)
        val ended = engine.escape()
        assertEquals(Outcome.ESCAPED, ended!!.outcome)
        assertEquals(SessionPhase.IDLE, engine.snapshot().phase)
    }

    @Test
    fun `a restored session that is already overdue re-raises the check-in`() {
        val overdue = SessionState(
            phase = SessionPhase.FOCUSED,
            task = "Chapter 4",
            startedAt = now - 60 * 60_000L,
            nextCheckpointAt = now - 5_000L,
            intervalMs = fifteenMinutes,
        )
        engine.restore(overdue)

        var fired = 0
        engine.onCheckpointDue = { fired++ }
        engine.tick()
        assertEquals(1, fired)
        assertTrue(engine.snapshot().owesCheckIn)
    }

    @Test
    fun `restoring refuses an idle or untitled session`() {
        engine.restore(SessionState())
        assertEquals(SessionPhase.IDLE, engine.snapshot().phase)
        engine.restore(SessionState(phase = SessionPhase.FOCUSED, task = "  "))
        assertEquals(SessionPhase.IDLE, engine.snapshot().phase)
    }

    @Test
    fun `a new task can be committed without dropping the session`() {
        engine.start("Study", fifteenMinutes)
        assertTrue(engine.retask("  Physics revision  "))
        val state = engine.snapshot()
        assertEquals("Physics revision", state.task)
        assertTrue(state.active)

        assertFalse(engine.retask(""))
        assertEquals("Physics revision", engine.snapshot().task)
    }

    @Test
    fun `the interval is clamped to something a human can survive`() {
        engine.start("Study", 0L)
        assertEquals(60_000L, engine.snapshot().intervalMs)

        val other = SessionEngine(Clock { now })
        other.start("Study", 99 * 60 * 60_000L)
        assertEquals(180 * 60_000L, other.snapshot().intervalMs)
    }

    @Test
    fun `progress and remaining track the countdown`() {
        engine.start("Study", fifteenMinutes)
        val start = engine.snapshot()

        now += fifteenMinutes / 2
        val midway = engine.snapshot()
        assertEquals(0.5f, midway.intervalProgress(now), 0.02f)
        assertEquals(fifteenMinutes / 2, midway.remainingMs(now))
        assertEquals(fifteenMinutes / 2, midway.elapsedMs(now))

        now += fifteenMinutes
        assertEquals(1f, start.intervalProgress(now), 0.001f)
    }
}
