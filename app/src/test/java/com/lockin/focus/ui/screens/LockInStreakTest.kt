package com.lockin.focus.ui.screens

import com.lockin.focus.core.DayClock
import com.lockin.focus.core.model.Outcome
import com.lockin.focus.core.model.SessionRecord
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class LockInStreakTest {

    private val now = 1_700_000_000_000L
    private val today = DayClock.startOfDay(now)

    private fun record(daysAgo: Long, outcome: Outcome = Outcome.COMPLETED, hours: Long = 2) =
        SessionRecord(
            id = "$daysAgo-$outcome-$hours",
            task = "Task",
            startedAt = today - daysAgo * DayClock.DAY_MS,
            endedAt = today - daysAgo * DayClock.DAY_MS + hours * 3_600_000L,
            outcome = outcome,
            blocksIntercepted = 0,
            checkpointsPassed = 0,
        )

    @Test
    fun `no sessions means no streak`() {
        assertEquals(0, LockInStreak.compute(emptyList(), now))
    }

    @Test
    fun `sessions that were all escaped do not build a streak`() {
        assertEquals(0, LockInStreak.compute(listOf(record(0, Outcome.ESCAPED)), now))
    }

    @Test
    fun `consecutive finished days count`() {
        val history = listOf(record(0), record(1), record(2))
        assertEquals(3, LockInStreak.compute(history, now))
    }

    @Test
    fun `a gap breaks the streak`() {
        val history = listOf(record(0), record(1), record(3), record(4))
        assertEquals(2, LockInStreak.compute(history, now))
    }

    @Test
    fun `a streak is still alive before today has been logged`() {
        val history = listOf(record(1), record(2), record(3))
        assertEquals(3, LockInStreak.compute(history, now))
    }

    @Test
    fun `a streak that stopped three days ago is zero`() {
        val history = listOf(record(4), record(5), record(6))
        assertEquals(0, LockInStreak.compute(history, now))
    }

    @Test
    fun `two finished sessions on one day are one day of streak`() {
        val history = listOf(record(0, hours = 1), record(0, hours = 5), record(1))
        assertEquals(2, LockInStreak.compute(history, now))
    }

    @Test
    fun `day boundaries are computed in the device zone`() {
        val zone = TimeZone.getDefault()
        val midnight = DayClock.startOfDay(now, zone)
        assertEquals(0, DayClock.daysBetween(midnight, midnight, zone))
        assertEquals(1, DayClock.daysBetween(midnight, midnight + DayClock.DAY_MS, zone))
    }
}
