package com.lockin.focus.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.lockin.focus.LockIn

/**
 * The check-in alarm: a wake-up alarm set for the exact moment the next check-in
 * is due.
 *
 * The foreground service's ticker is the happy path, but a foreground service is
 * the first thing aggressive battery managers kill, and a focus timer that
 * silently stops counting down is worse than no focus timer — the user believes
 * the apps are still locked. This alarm survives because the system holds it, not
 * the app.
 *
 * Firing it ticks the engine, which flips the phase to CHECKPOINT and asks the
 * overlay host to raise the screen, then immediately re-arms for the next
 * interval.
 */
class CheckpointAlarm : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CHECKPOINT_DUE) return
        val engine = LockIn.engine
        engine.tick()
        val state = engine.snapshot()
        // Only re-arm while a check-in is actually pending. Re-arming while the
        // screen is up would schedule for a nextCheckpointAt that is already in
        // the past, and a past-due exact alarm re-fires almost immediately — an
        // alarm storm that runs until the user finally answers.
        if (state.active && !state.owesCheckIn) {
            arm(context, state.nextCheckpointAt, state.intervalMs)
        } else {
            cancel(context)
        }
        Log.i(TAG, "Check-in alarm fired, phase=${state.phase}")
    }

    companion object {
        private const val TAG = "LockIn"
        const val ACTION_CHECKPOINT_DUE = "com.lockin.focus.CHECKPOINT_DUE"

        private const val REQUEST_CODE = 0x2A

        /**
         * Schedules the next wake-up. Falls back to an inexact window when the app
         * does not hold SCHEDULE_EXACT_ALARM — late by up to a few minutes rather
         * than never, which is the right way round.
         */
        fun arm(context: Context, triggerAtMs: Long, intervalMs: Long) {
            val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val pending = pendingIntent(context)
            // Belt and braces: a trigger already in the past would re-fire at once.
            val target = triggerAtMs.coerceAtLeast(System.currentTimeMillis() + 1_000L)
            val exact = canScheduleExact(manager)
            try {
                if (exact) {
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target, pending)
                } else {
                    // ~10% of the interval, floored at a minute, so it still reads
                    // as "about every N minutes" rather than "sometime soon".
                    val window = (intervalMs / 10).coerceAtLeast(60_000L)
                    manager.setWindow(AlarmManager.RTC_WAKEUP, target, window, pending)
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Alarm refused; falling back to an inexact window", e)
                runCatching {
                    manager.setWindow(
                        AlarmManager.RTC_WAKEUP,
                        target,
                        (intervalMs / 10).coerceAtLeast(60_000L),
                        pending,
                    )
                }
            }
        }

        fun cancel(context: Context) {
            val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            manager.cancel(pendingIntent(context))
        }

        private fun canScheduleExact(manager: AlarmManager): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

        private fun pendingIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                Intent(context, CheckpointAlarm::class.java).setAction(ACTION_CHECKPOINT_DUE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
