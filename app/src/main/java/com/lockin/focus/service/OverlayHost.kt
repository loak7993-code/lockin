package com.lockin.focus.service

/**
 * The only thing LockIn's core needs from Android: a way to put a screen in
 * front of the user. Implemented by [AppWatchService], which is the only
 * component allowed to start an activity while another app is in front.
 */
interface OverlayHost {

    /** Slams the block screen over [blockedPackage]. */
    fun presentBlockScreen(blockedPackage: String)

    /** Raises the 15-minute check-in over whatever is on screen. */
    fun presentCheckpoint()

    /** Raises the goal screen after the doom detector catches a scroll. */
    fun presentGoal()

    /**
     * Takes the user off [blockedPackage] and back to something they are allowed
     * to use, instead of just finishing and revealing the blocked app again.
     */
    fun returnToSafeApp(blockedPackage: String)

    fun goHome()

    fun signalHaptic()
}
