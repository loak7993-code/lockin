package com.lockin.focus.core

import com.lockin.focus.core.model.FocusSettings

/**
 * Decides whether a package may stay on screen during a session.
 *
 * Deliberately a pure function over package-name strings: no PackageManager, no
 * Context, no Android. That keeps the one rule that actually matters to the user
 * (does the blocklist catch this app?) exhaustively unit-testable.
 */
object AccessPolicy {

    /**
     * Apps that must keep working during a lock-in, even if the user manages to
     * blocklist one of them by accident. Breaking the phone to enforce a focus
     * timer is a worse outcome than a five-second phone call.
     */
    val SYSTEM_ALWAYS_ALLOWED: Set<String> = setOf(
        // Telephony / emergency
        "com.android.dialer",
        "com.google.android.dialer",
        "com.samsung.dialer",
        "com.android.server.telecom",
        "com.android.emergency",
        "com.android.incallui",
        // System UI, settings, installers — the user needs an escape hatch that
        // is not "disable LockIn".
        "com.android.systemui",
        "com.android.settings",
        "com.android.shell",
        "com.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        // Messaging
        "com.android.mms",
        "com.google.android.apps.messaging",
        // Clock / alarm
        "com.android.deskclock",
        "com.google.android.deskclock",
        // Launchers
        "com.android.launcher",
        "com.android.launcher2",
        "com.android.launcher3",
        "com.android.launcher3u",
        "com.google.android.launcher",
        "com.google.android.launcher.gearlink",
        "com.sec.android.app.launcher",
        "com.huawei.android.launcher",
        "com.miui.home",
        "com.oppo.launcher",
        "com.coloros.launcher",
        "com.bbk.launcher2",
        "com.teslacoilsw.launcher",
        "com.samsung.android.app.launcher",
    )

    /** Packages that are launchers regardless of the OEM string above. */
    val LAUNCHER_QUERY: String = "android.intent.category.LAUNCHER"

    enum class Reason { ON_BLOCKLIST }

    sealed interface Decision {
        data object Allow : Decision
        data class Block(val reason: Reason) : Decision
    }

    fun isSelf(pkg: String, selfPackage: String): Boolean = pkg == selfPackage

    fun isAlwaysAllowed(pkg: String, settings: FocusSettings): Boolean =
        pkg in SYSTEM_ALWAYS_ALLOWED || pkg in settings.allowed

    /**
     * @param launcherPackages packages that act as the home app, resolved by the
     *   caller because it needs a PackageManager to find them.
     */
    fun evaluate(
        pkg: String,
        settings: FocusSettings,
        selfPackage: String,
        launcherPackages: Set<String> = emptySet(),
    ): Decision = when {
        pkg.isBlank() -> Decision.Allow
        isSelf(pkg, selfPackage) -> Decision.Allow
        pkg in launcherPackages -> Decision.Allow
        isAlwaysAllowed(pkg, settings) -> Decision.Allow
        pkg in settings.blocked -> Decision.Block(Reason.ON_BLOCKLIST)
        else -> Decision.Allow
    }
}
