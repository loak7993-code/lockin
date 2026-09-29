package com.lockin.focus.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.lockin.focus.LockIn
import com.lockin.focus.R
import com.lockin.focus.core.model.SessionState
import com.lockin.focus.ui.CheckpointActivity
import com.lockin.focus.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the countdown. Started when the user locks in, sticky so a low-memory kill
 * does not silently end a session, and self-destructs the moment the session ends.
 */
class FocusGuardService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ticker: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        // Must happen within 5s of startForegroundService, before any suspend work.
        ServiceCompat.startForeground(this, ONGOING_ID, buildOngoing(SessionState()), foregroundType())
        observeSession()
        startTicker()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_END_SESSION -> {
                LockIn.completeSession()
                return START_NOT_STICKY
            }

            ACTION_OPEN_CHECKPOINT -> LockIn.raiseCheckpointIfOwed()
        }
        if (!LockIn.engine.snapshot().active) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun observeSession() {
        scope.launch {
            LockIn.engine.state.collectLatest { state ->
                if (!state.active) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collectLatest
                }
                notificationManager().notify(ONGOING_ID, buildOngoing(state))
                if (state.owesCheckIn) {
                    raiseCheckpointNotification(state)
                }
            }
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                LockIn.engine.tick()
                LockIn.tickDoomDetector()
                delay(TICK_MS)
            }
        }
    }

    private fun buildOngoing(state: SessionState): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            REQ_CONTENT,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            pendingIntentFlags(),
        )
        val answerIntent = PendingIntent.getActivity(
            this,
            REQ_ANSWER,
            Intent(this, CheckpointActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            ),
            pendingIntentFlags(),
        )
        val endIntent = PendingIntent.getService(
            this,
            REQ_END,
            Intent(this, FocusGuardService::class.java).setAction(ACTION_END_SESSION),
            pendingIntentFlags(),
        )

        return NotificationCompat.Builder(this, CHANNEL_SESSION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_session_title, state.task))
            .setContentText(
                resources.getQuantityString(
                    R.plurals.notification_session_text,
                    state.blocksIntercepted,
                    state.blocksIntercepted,
                ),
            )
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            // Native countdown: the OS ticks it, so it survives doze without us.
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(state.nextCheckpointAt)
            .addAction(
                R.drawable.ic_notification,
                getString(R.string.notification_action_answer),
                answerIntent,
            )
            .addAction(
                R.drawable.ic_notification,
                getString(R.string.notification_action_end),
                endIntent,
            )
            .build()
    }

    /**
     * Fallback path for when the accessibility guard is off (or the OS refuses to
     * let us start an activity): a full-screen notification that asks the same
     * question. Loud by design.
     */
    private fun raiseCheckpointNotification(state: SessionState) {
        if (LockIn.accessibilityConnected) return
        val fullScreen = PendingIntent.getActivity(
            this,
            REQ_CHECKPOINT,
            Intent(this, CheckpointActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            ),
            pendingIntentFlags(),
        )
        val minutes = (state.intervalMs / 60_000L).toInt()
        val notification = NotificationCompat.Builder(this, CHANNEL_CHECKPOINT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(resources.getQuantityString(R.plurals.notification_checkpoint_title, minutes, minutes))
            .setContentText(getString(R.string.notification_checkpoint_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(getString(R.string.notification_checkpoint_text)))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .build()
        runCatching { notificationManager().notify(CHECKPOINT_ID, notification) }
            .onFailure { Log.w(TAG, "Checkpoint notification refused", it) }
    }

    private fun createChannels() {
        val manager = notificationManager()
        val session = NotificationChannel(
            CHANNEL_SESSION,
            getString(R.string.channel_session_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.channel_session_description)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        val checkpoint = NotificationChannel(
            CHANNEL_CHECKPOINT,
            getString(R.string.channel_checkpoint_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.channel_checkpoint_description)
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(session)
        manager.createNotificationChannel(checkpoint)
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun pendingIntentFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private fun foregroundType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }

    companion object {
        private const val TAG = "LockIn"
        const val ACTION_END_SESSION = "com.lockin.focus.END_SESSION"
        const val ACTION_OPEN_CHECKPOINT = "com.lockin.focus.OPEN_CHECKPOINT"

        private const val CHANNEL_SESSION = "session"
        private const val CHANNEL_CHECKPOINT = "checkpoint"
        private const val ONGOING_ID = 0x10
        private const val CHECKPOINT_ID = 0x11
        private const val REQ_CONTENT = 1
        private const val REQ_ANSWER = 2
        private const val REQ_END = 3
        private const val REQ_CHECKPOINT = 4
        private const val TICK_MS = 250L
    }
}
