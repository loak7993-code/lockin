package com.lockin.focus.core

import com.lockin.focus.core.model.MAX_INTERVAL_MINUTES
import com.lockin.focus.core.model.MIN_INTERVAL_MINUTES
import java.util.Calendar
import java.util.TimeZone

/** Wall-clock source, injected so the session engine can be tested without sleeping. */
fun interface Clock {
    fun now(): Long

    companion object {
        val Wall: Clock = Clock { java.lang.System.currentTimeMillis() }
    }
}

/** Wall-clock helpers. All in the device's local zone: focus stats are about "my day". */
object DayClock {
    fun startOfDay(epochMs: Long, zone: TimeZone = TimeZone.getDefault()): Long =
        Calendar.getInstance(zone).apply {
            timeInMillis = epochMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /** Whole days between two instants, ignoring time of day. */
    fun daysBetween(fromEpochMs: Long, toEpochMs: Long, zone: TimeZone = TimeZone.getDefault()): Int {
        val a = startOfDay(fromEpochMs, zone)
        val b = startOfDay(toEpochMs, zone)
        return ((b - a) / DAY_MS).toInt()
    }

    const val DAY_MS: Long = 24L * 60L * 60L * 1000L
}

fun Long.clampTo(min: Long, max: Long): Long = coerceIn(min, max)

fun Long.clampIntervalMs(): Long =
    clampTo(MIN_INTERVAL_MINUTES * 60_000L, MAX_INTERVAL_MINUTES * 60_000L)
