package com.lockin.focus.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.lockin.focus.LockIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A lock-in is a promise, so a reboot does not dissolve it. If a session was
 * running when the phone restarted, the guard comes back up and the apps stay
 * blocked — including the check-in that was already overdue.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val session = LockIn.currentSession()
                val settings = LockIn.currentSettings()
                if (session != null && settings.autoResumeOnBoot) {
                    LockIn.restoreSession(session)
                    Log.i("LockIn", "Resumed session '${session.task}' after ${intent.action}")
                }
            } catch (e: Exception) {
                Log.w("LockIn", "Could not resume session after boot", e)
            } finally {
                pending.finish()
            }
        }
    }
}
