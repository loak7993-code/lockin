package com.lockin.focus.core

import com.lockin.focus.core.model.DoomTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DoomDetectorTest {

    private val threshold = 15L * 60_000L
    private val detector = DoomDetector(threshold)
    private var now = 0L

    private fun enter(pkg: String) = detector.current(pkg, pkg, now)

    private fun stay(seconds: Long) {
        now += seconds * 1000
        detector.tick("com.a.scroller", "Scroller", now)
    }

    @Test
    fun `nothing fires before the threshold`() {
        enter("com.a.scroller")
        stay(60 * 14)
        assertNull(detector.tick("com.a.scroller", "Scroller", now))
    }

    @Test
    fun `fifteen minutes in one app is a continuous doom`() {
        enter("com.a.scroller")
        var fired: DoomDetector.Result? = null
        for (minute in 1..15) {
            now += 60_000
            fired = fired ?: detector.tick("com.a.scroller", "Scroller", now)
        }
        val result = requireNotNull(fired) { "15 minutes in one app must trip" }
        assertEquals(DoomTrigger.CONTINUOUS, result.trigger)
        assertTrue(result.continuousMs >= threshold)
    }

    @Test
    fun `fourteen minutes is not yet a doom`() {
        enter("com.a.scroller")
        var fired: DoomDetector.Result? = null
        for (minute in 1..14) {
            now += 60_000
            fired = fired ?: detector.tick("com.a.scroller", "Scroller", now)
        }
        assertNull("14 minutes is still just looking at something", fired)
    }

    @Test
    fun `firing once does not fire again on the next tick`() {
        enter("com.a.scroller")
        now += threshold
        assertNotNull(detector.tick("com.a.scroller", "Scroller", now))
        now += 1000
        assertNull("must not re-fire while the goal is still on screen", detector.tick("com.a.scroller", "Scroller", now))
        now += 60_000
        assertNull(detector.tick("com.a.scroller", "Scroller", now))
    }

    @Test
    fun `answering the goal rebases the clock so you are not re-caught instantly`() {
        enter("com.a.scroller")
        var first: DoomDetector.Result? = null
        for (minute in 1..15) {
            now += 60_000
            val r = detector.tick("com.a.scroller", "Scroller", now)
            if (r != null) {
                first = r
                break
            }
        }
        assertNotNull("expected the first doom", first)

        detector.rearm(now)
        now += 60_000
        assertNull(
            "one minute after doing the goal it must not fire again",
            detector.tick("com.a.scroller", "Scroller", now),
        )
    }

    @Test
    fun `but it still fires if you go straight back to scrolling`() {
        enter("com.a.scroller")
        for (minute in 1..15) {
            now += 60_000
            if (detector.tick("com.a.scroller", "Scroller", now) != null) break
        }
        detector.rearm(now)

        var fired: DoomDetector.Result? = null
        for (minute in 1..15) {
            now += 60_000
            fired = fired ?: detector.tick("com.a.scroller", "Scroller", now)
        }
        assertNotNull("a fresh 15 minutes is still 15 minutes", fired)
    }
    @Test
    fun `hopping between apps still trips the cumulative rule`() {
        // Six minutes each, four apps, never more than six minutes in any one.
        var fired: DoomDetector.Result? = null
        listOf("a", "b", "c", "d").forEach { pkg ->
            enter("com.x.$pkg")
            for (m in 1..6) {
                now += 60_000
                fired = fired ?: detector.tick("com.x.$pkg", pkg, now)
            }
        }
        assertNotNull("24 minutes across four apps must not slip through", fired)
        assertEquals(DoomTrigger.CUMULATIVE, fired!!.trigger)
    }

    @Test
    fun `quick flicking through apps does not fire`() {
        // Ten seconds at a time: the time is banked, but no single stretch is long
        // enough for the cumulative rule to count as a pattern.
        var fired: DoomDetector.Result? = null
        repeat(100) { i ->
            enter("com.x.app$i")
            now += 10_000
            fired = fired ?: detector.tick("com.x.app$i", "app$i", now)
        }
        assertNull("dipping in and out is not doom scrolling", fired)
    }

    @Test
    fun `time is banked when an app is left, not lost`() {
        enter("com.a.scroller")
        now += 8 * 60_000
        enter("com.a.other") // leaves after 8 minutes without a tick in between
        now += 8 * 60_000
        enter("com.a.scroller")

        // 16 minutes banked across two visits to the same app; a tick must see it.
        var fired: DoomDetector.Result? = null
        for (m in 1..8) {
            now += 60_000
            fired = fired ?: detector.tick("com.a.scroller", "Scroller", now)
        }
        assertNotNull(fired)
    }

    @Test
    fun `dwell is measured in the app the user is actually in`() {
        enter("com.a.scroller")
        now += 90_000
        assertEquals(90_000L, detector.currentDwellMs(now))
        assertEquals("com.a.scroller", detector.currentPackage())
    }

    @Test
    fun `reset clears everything`() {
        enter("com.a.scroller")
        now += 5 * 60_000
        detector.reset()
        assertNull(detector.currentPackage())
        assertEquals(0L, detector.currentDwellMs(now))
    }

    @Test
    fun `widening the threshold does not refund time already served`() {
        enter("com.a.scroller")
        for (minute in 1..10) {
            now += 60_000
            detector.tick("com.a.scroller", "Scroller", now)
        }

        val widened = detector.withThreshold(20 * 60_000L)
        assertEquals("the ten minutes already served must carry over", 10 * 60_000L, widened.currentDwellMs(now))

        var fired: DoomDetector.Result? = null
        for (minute in 1..10) {
            now += 60_000
            fired = fired ?: widened.tick("com.a.scroller", "Scroller", now)
        }
        assertNotNull("20 minutes total, 10 already served, should trip", fired)
    }

    @Test
    fun `narrowing the threshold fires on what has already been served`() {
        enter("com.a.scroller")
        for (minute in 1..12) {
            now += 60_000
            detector.tick("com.a.scroller", "Scroller", now)
        }
        assertNull("12 minutes is under the original 15", detector.tick("com.a.scroller", "Scroller", now))

        val narrowed = detector.withThreshold(10 * 60_000L)
        assertNotNull(
            "12 minutes is over the new 10, it should trip immediately",
            narrowed.tick("com.a.scroller", "Scroller", now),
        )
    }

    @Test
    fun `answering a goal never refunds the reported session total`() {
        enter("com.a.scroller")
        for (minute in 1..15) {
            now += 60_000
            if (detector.tick("com.a.scroller", "Scroller", now) != null) break
        }
        assertEquals(15 * 60_000L, detector.totalMs(now))

        detector.rearm(now)
        for (minute in 1..15) {
            now += 60_000
            if (detector.tick("com.a.scroller", "Scroller", now) != null) break
        }
        assertEquals(
            "two full stretches is 30 minutes of scrolling, whatever the goals did",
            30 * 60_000L,
            detector.totalMs(now),
        )
    }

    @Test
    fun `the cumulative rule needs a fresh full stretch after each goal`() {
        enter("com.a.scroller")
        for (minute in 1..15) {
            now += 60_000
            if (detector.tick("com.a.scroller", "Scroller", now) != null) break
        }
        detector.rearm(now)

        // Five minutes of hopping between apps, none longer than three. That is
        // well past the threshold as a session total, but only five minutes past
        // the baseline, so it must not fire.
        var fired: DoomDetector.Result? = null
        listOf("a", "b", "c").forEach { pkg ->
            enter("com.x.$pkg")
            for (m in 1..3) {
                now += 60_000
                fired = fired ?: detector.tick("com.x.$pkg", pkg, now)
            }
            now -= 120_000 // hop back to a safe app in between
        }
        assertNull("five minutes after the goal is not another fifteen", fired)
    }

    @Test
    fun `a hostile clock cannot manufacture doom`() {
        enter("com.a.scroller")
        now -= 60_000 // clock jumps backwards
        val result = detector.tick("com.a.scroller", "Scroller", now)
        assertTrue("backwards time must not count, got ${result?.continuousMs}", detector.currentDwellMs(now) <= 0)
    }
}
